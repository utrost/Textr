#!/usr/bin/env python3
"""Prepare verified, owner-signed PRIVATE DRAFT releases from an explicit tested commit.

No production keys are created. Existing tags/releases and public publication are untouched.
Python 3.12+, JDK 17, Android SDK 36, Git and (for CI/upload) authenticated gh required.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import zipfile

REPO = Path(__file__).resolve().parents[1]
APP_ID = 'de.utrost.textr'


def run(args, cwd=REPO, env=None, capture=True):
    result = subprocess.run([str(a) for a in args], cwd=cwd, env=env, text=True,
                            stdout=subprocess.PIPE if capture else None,
                            stderr=subprocess.PIPE if capture else None)
    if result.returncode:
        # Build output can include user-supplied paths. Never echo signing files or environment.
        raise RuntimeError(f'{Path(str(args[0])).name} failed (exit {result.returncode}); see local build/verification log')
    return result.stdout or ''


def version(text):
    code = re.search(r'versionCode\s*=.*?\?:\s*(\d+)', text)
    name = re.search(r'versionName\s*=.*?\?:\s*"([A-Za-z0-9.+_-]+)"', text)
    if not code or not name:
        raise ValueError('Cannot find committed version defaults')
    return int(code[1]), name[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def sdk_tools():
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or '')
    tools = sdk / 'build-tools/36.0.0'
    if not (tools / 'apksigner').is_file() or not (tools / 'aapt').is_file():
        raise ValueError('Set ANDROID_HOME to an SDK with Build Tools 36.0.0')
    return tools


def inspect_manifest(apk, code, name):
    tools = sdk_tools()
    badging = run([tools / 'aapt', 'dump', 'badging', apk])
    permissions = run([tools / 'aapt', 'dump', 'permissions', apk])
    xml = run([tools / 'aapt', 'dump', 'xmltree', apk, 'AndroidManifest.xml'])
    expected = f"package: name='{APP_ID}' versionCode='{code}' versionName='{name}'"
    if expected not in badging or "sdkVersion:'29'" not in badging or "targetSdkVersion:'36'" not in badging:
        raise ValueError('APK ID, version or SDK differs from committed source')
    if 'application-debuggable' in badging:
        raise ValueError('Release is debuggable')
    allowed = {APP_ID + '.permission.FIND_SELECTION', APP_ID + '.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION'}
    actual = set(re.findall(r"uses-permission(?:-sdk-\d+)?: name='([^']+)'", permissions))
    if actual != allowed:
        raise ValueError('APK permissions differ from the two expected app signature permissions')
    signature = set()
    for block in re.findall(r'(?m)^    E: permission .*\n((?:      A: .*\n)*)', xml):
        name_match = re.search(r'android:name\([^)]*\)="([^"]+)"', block)
        if name_match and re.search(r'android:protectionLevel.*=\(type 0x11\)0x2(?:\s|$)', block):
            signature.add(name_match[1])
    if signature != allowed:
        raise ValueError('App permissions must retain signature protection')
    return badging + '\n' + permissions + '\n' + xml


def inspect(apk, code, name, certificate):
    tools = sdk_tools()
    signing = run([tools / 'apksigner', 'verify', '--verbose', '--print-certs', apk])
    if f'certificate SHA-256 digest: {certificate.lower()}' not in signing:
        raise ValueError('APK signing certificate differs from the explicitly expected identity')
    manifest = inspect_manifest(apk, code, name)
    with zipfile.ZipFile(apk) as archive:
        for required in ['assets/licenses/Textr-GPL-3.0.txt', 'assets/licenses/Attributions.txt']:
            if required not in archive.namelist():
                raise ValueError('APK is missing required notices')
    return signing + '\n' + manifest


def verify(directory, expected_certificate=None):
    directory = Path(directory).resolve()
    manifest = json.loads((directory / 'BUILD.json').read_text())
    required = {'BUILD.json', 'RELEASE-NOTES.md', 'SIGNING.txt', 'apk-verification.txt',
                manifest['apk'], manifest['source']}
    hashes = {}
    for line in (directory / 'SHA256SUMS').read_text().splitlines():
        expected, name = line.split('  ', 1)
        if not re.fullmatch(r'[a-f0-9]{64}', expected) or Path(name).name != name or name in hashes:
            raise ValueError('Unsafe or duplicate checksum entry')
        path = directory / name
        if not path.is_file() or path.is_symlink() or digest(path) != expected:
            raise ValueError('Missing or mismatched artifact: ' + name)
        hashes[name] = expected
    if set(hashes) != required:
        raise ValueError('Checksum manifest does not exactly cover required artifacts')
    cert = manifest['certificateSha256']
    if expected_certificate and cert != expected_certificate.lower():
        raise ValueError('Package certificate differs from the expected identity')
    inspection = inspect(directory / manifest['apk'], manifest['versionCode'], manifest['versionName'], cert)
    if inspection != (directory / 'apk-verification.txt').read_text():
        raise ValueError('Recorded APK verification differs from downloaded artifact')
    with tarfile.open(directory / manifest['source']) as archive:
        members = archive.getmembers()
        if archive.pax_headers.get('comment') != manifest['commit']:
            raise ValueError('Source archive does not identify the expected Git commit')
        for member in members:
            path = Path(member.name)
            if path.is_absolute() or '..' in path.parts:
                raise ValueError('Unsafe source archive member')
        gradle = next(m for m in members if m.name == f"Textr-{manifest['versionName']}/app/build.gradle.kts")
        if version(archive.extractfile(gradle).read().decode()) != (manifest['versionCode'], manifest['versionName']):
            raise ValueError('Source archive version differs from APK')
    if manifest['versionCode'] <= manifest['previousVersionCode']:
        raise ValueError('Version code must exceed the previous installed release')
    return manifest


def prepare(args):
    if run(['git', 'status', '--porcelain']).strip():
        raise ValueError('Commit or isolate working changes before release preparation')
    commit = run(['git', 'rev-parse', args.ref + '^{commit}']).strip()
    gradle = run(['git', 'show', commit + ':app/build.gradle.kts'])
    code, name = version(gradle)
    if code <= args.previous_version_code:
        raise ValueError('Committed version code must exceed --previous-version-code')
    tag = 'v' + name
    # Never repoint or overwrite a tag, including tags on a different branch.
    existing = subprocess.run(['git', 'rev-parse', '--verify', 'refs/tags/' + tag], cwd=REPO,
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if existing.returncode == 0 and run(['git', 'rev-parse', 'refs/tags/' + tag + '^{commit}']).strip() != commit:
        raise ValueError('Release tag exists on another commit; never repoint a release tag')
    plan = {'commit': commit, 'versionCode': code, 'versionName': name, 'tag': tag,
            'previousVersionCode': args.previous_version_code, 'draftOnly': True}
    if args.dry_run:
        print(json.dumps(plan, indent=2)); return
    if not args.ci_run or not args.signing_properties or not args.expected_certificate or not args.output:
        raise ValueError('Packaging needs --ci-run, --signing-properties, --expected-certificate and --output')
    if not re.fullmatch(r'[a-fA-F0-9]{64}', args.expected_certificate):
        raise ValueError('Expected certificate must be a SHA-256 fingerprint without colons')
    ci = json.loads(run(['gh', 'api', f'repos/utrost/Textr/actions/runs/{args.ci_run}']))
    if ci['head_sha'] != commit or ci['conclusion'] != 'success':
        raise ValueError('CI must have passed on the exact requested source commit')
    plan['ciRun'] = ci['html_url']
    signing = Path(args.signing_properties).resolve()
    if not signing.is_file() or signing.is_relative_to(REPO):
        raise ValueError('Signing configuration must exist outside the repository')
    output = Path(args.output).resolve()
    if output.exists():
        raise ValueError('Output directory already exists; never overwrite release artifacts')
    if output.is_relative_to(REPO):
        raise ValueError('Choose an output directory outside the source checkout')
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='textr-release-') as temporary:
        staging = Path(temporary)
        source_name = f'Textr-{name}-source.tar.gz'
        source = staging / source_name
        with source.open('wb') as stream:
            subprocess.run(['git', 'archive', '--format=tar.gz', f'--prefix=Textr-{name}/', commit], cwd=REPO, stdout=stream, check=True)
        with tarfile.open(source) as archive:
            archive.extractall(staging / 'checkout', filter='data')
        checkout = staging / 'checkout' / f'Textr-{name}'
        env = os.environ.copy()
        env['TEXTR_SIGNING_PROPERTIES'] = str(signing)
        command = [str(checkout / 'gradlew'), ':app:assembleRelease', f'-PtextrVersionCode={code}', f'-PtextrVersionName={name}', '-PtextrUnsignedRelease=false', '--no-daemon']
        log = output.parent / (output.name + '-build.log')
        with log.open('w') as stream:
            result = subprocess.run(command, cwd=checkout, env=env, stdout=stream, stderr=subprocess.STDOUT)
        if result.returncode:
            raise RuntimeError('Release build failed; package not created. Inspect the local build log privately.')
        built = checkout / 'app/build/outputs/apk/release/app-release.apk'
        inspection = inspect(built, code, name, args.expected_certificate)
        package = staging / 'package'; package.mkdir()
        apk_name = f'Textr-{name}.apk'
        shutil.copyfile(built, package / apk_name); shutil.copyfile(source, package / source_name)
        plan.update(apk=apk_name, source=source_name, certificateSha256=args.expected_certificate.lower())
        (package / 'BUILD.json').write_text(json.dumps(plan, indent=2) + '\n')
        (package / 'apk-verification.txt').write_text(inspection)
        (package / 'SIGNING.txt').write_text('Explicitly approved signing identity. No private key or credentials included.\nCertificate SHA-256: ' + plan['certificateSha256'] + '\n')
        changelog = checkout / f'fastlane/metadata/android/en-US/changelogs/{code}.txt'
        if not changelog.is_file():
            raise ValueError('Committed English changelog is required for this version code')
        notes = f"# Textr {name} — private testing draft\n\n" + changelog.read_text().strip() + f"\n\nSource: `{commit}`. Version code {code}, Android 10/API 29+.\n\nValidated CI: {plan['ciRun']}\n\nAPK signing/manifest/notices and exact version match verified. Downloaded assets are checksum-verified after upload. Existing releases are immutable. Public publication, production-key creation and signing-channel decisions remain explicit owner actions.\n\nInstall over a previous alpha only when its certificate matches SIGNING.txt. Physical providers, human TalkBack and foldables remain tracked separately.\n"
        (package / 'RELEASE-NOTES.md').write_text(notes)
        (package / 'SHA256SUMS').write_text(''.join(digest(p) + '  ' + p.name + '\n' for p in sorted(package.iterdir())))
        verify(package, args.expected_certificate)
        shutil.copytree(package, output)
    print('Verified local package: ' + str(output))
    if args.upload_draft:
        tags = run(['gh', 'api', '--paginate', 'repos/utrost/Textr/releases', '--jq', '.[].tag_name']).splitlines()
        if tag in tags:
            raise ValueError('Release already exists; nothing was overwritten')
        assets = [str(p) for p in sorted(output.iterdir())]
        run(['gh', 'release', 'create', tag, '--repo', 'utrost/Textr', '--draft', '--prerelease', '--target', commit,
             '--title', f'Textr {name} — private testing', '--notes-file', output / 'RELEASE-NOTES.md', *assets])
        release = json.loads(run(['gh', 'release', 'view', tag, '--repo', 'utrost/Textr', '--json', 'isDraft,url']))
        if not release['isDraft']:
            raise ValueError('Uploaded release is unexpectedly public')
        with tempfile.TemporaryDirectory(prefix='textr-release-download-') as temporary:
            run(['gh', 'release', 'download', tag, '--repo', 'utrost/Textr', '--dir', temporary])
            downloaded = verify(temporary, args.expected_certificate)
            if downloaded != plan:
                raise ValueError('Downloaded release metadata differs')
            for local in output.iterdir():
                if digest(local) != digest(Path(temporary) / local.name):
                    raise ValueError('Downloaded bytes differ from locally verified artifact: ' + local.name)
        print('Private draft and downloaded hashes verified: ' + release['url'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--ref', help='Explicit immutable tested source commit or tag')
    parser.add_argument('--previous-version-code', type=int)
    parser.add_argument('--ci-run', type=int)
    parser.add_argument('--signing-properties')
    parser.add_argument('--expected-certificate')
    parser.add_argument('--output')
    parser.add_argument('--upload-draft', action='store_true')
    parser.add_argument('--dry-run', action='store_true')
    parser.add_argument('--verify-dir', type=Path)
    args = parser.parse_args()
    try:
        if args.verify_dir:
            if not args.expected_certificate:
                raise ValueError('--verify-dir requires --expected-certificate to anchor signature trust')
            verify(args.verify_dir, args.expected_certificate); print('All local artifacts, source versions, signatures and hashes verified')
        else:
            if not args.ref or args.previous_version_code is None:
                parser.error('--ref and --previous-version-code are required')
            prepare(args)
    except (ValueError, RuntimeError, OSError, subprocess.CalledProcessError, StopIteration, KeyError, tarfile.TarError, zipfile.BadZipFile) as error:
        print('Release preparation refused: ' + str(error), file=sys.stderr); return 1
    return 0


if __name__ == '__main__':
    sys.exit(main())
