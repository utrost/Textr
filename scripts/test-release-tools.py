#!/usr/bin/env python3
"""Offline regression checks for release refusal boundaries; never builds or uploads."""
import argparse
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('release', Path(__file__).with_name('prepare-release.py'))
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseGuards(unittest.TestCase):
    def setUp(self):
        self.scratch = tempfile.TemporaryDirectory(prefix='textr-release-guards-')
        self.root = Path(self.scratch.name)
        self.old_repo = release.REPO
        release.REPO = self.root
        self.original_run = release.run
        # run's default cwd was bound at import; route only test calls into this disposable repo.
        self.routed = patch.object(release, 'run', side_effect=lambda args, **kwargs: self.original_run(args, cwd=self.root, **kwargs))
        self.routed.start()
        self.git('init', '-q')
        self.git('config', 'user.name', 'Synthetic release test')
        self.git('config', 'user.email', 'test@example.invalid')
        (self.root / 'app').mkdir()
        (self.root / 'app/build.gradle.kts').write_text('versionCode = property ?: 5\nversionName = property ?: "0.5-alpha"\n')
        self.git('add', '.')
        self.git('commit', '-qm', 'Synthetic fixture')
        self.sha = self.git('rev-parse', 'HEAD').strip()
        self.args = argparse.Namespace(ref=self.sha, previous_version_code=4, dry_run=True,
                                       ci_run=123, signing_properties=None, expected_certificate=None,
                                       output=None, upload_draft=False)

    def tearDown(self):
        self.routed.stop()
        release.REPO = self.old_repo
        self.scratch.cleanup()

    def git(self, *args):
        return subprocess.check_output(['git', *args], cwd=self.root, text=True, stderr=subprocess.DEVNULL)

    def test_dry_run_selects_explicit_commit_and_committed_version(self):
        result = io.StringIO()
        with contextlib.redirect_stdout(result):
            release.prepare(self.args)
        plan = json.loads(result.getvalue())
        self.assertEqual((self.sha, 5, 'v0.5-alpha', True), (plan['commit'], plan['versionCode'], plan['tag'], plan['draftOnly']))
        self.assertFalse((self.root / 'build').exists())

    def test_dirty_checkout_is_refused(self):
        (self.root / 'uncommitted.txt').write_text('fixture')
        with self.assertRaisesRegex(ValueError, 'working changes'):
            release.prepare(self.args)

    def test_equal_or_lower_version_cannot_package(self):
        for previous in (5, 6):
            self.args.previous_version_code = previous
            with self.assertRaisesRegex(ValueError, 'version code'):
                release.prepare(self.args)

    def test_existing_tag_cannot_move_to_new_source(self):
        self.git('tag', 'v0.5-alpha')
        (self.root / 'notes.txt').write_text('next commit')
        self.git('add', '.')
        self.git('commit', '-qm', 'Another fixture')
        self.args.ref = self.git('rev-parse', 'HEAD').strip()
        with self.assertRaisesRegex(ValueError, 'never repoint'):
            release.prepare(self.args)
        self.assertEqual(self.sha, self.git('rev-parse', 'v0.5-alpha').strip())

    def test_successful_ci_for_another_commit_is_refused_before_signing(self):
        self.args.dry_run = False
        self.args.signing_properties = '/not/read'
        self.args.expected_certificate = 'a' * 64
        self.args.output = '/not/written'
        original = release.run
        def ci(args, **kwargs):
            return json.dumps({'head_sha': 'b' * 40, 'conclusion': 'success'}) if args[0] == 'gh' else original(args, **kwargs)
        with patch.object(release, 'run', side_effect=ci), self.assertRaisesRegex(ValueError, 'exact requested'):
            release.prepare(self.args)

    def test_remote_lightweight_and_annotated_tags_must_match_source(self):
        tag = 'v0.5-alpha'
        for output in ['', self.sha + '\trefs/tags/' + tag + '\n', 'a' * 40 + '\trefs/tags/' + tag + '\n' + self.sha + '\trefs/tags/' + tag + '^{}\n']:
            with patch.object(release, 'run', return_value=output):
                self.assertEqual(bool(output), release.remote_tag_matches(tag, self.sha))
        with patch.object(release, 'run', return_value='b' * 40 + '\trefs/tags/' + tag + '\n'):
            with self.assertRaisesRegex(ValueError, 'Remote release tag'):
                release.remote_tag_matches(tag, self.sha)

    def test_package_rejects_unverified_extra_assets(self):
        package = self.root / 'extra-assets'
        package.mkdir()
        (package / 'BUILD.json').write_text(json.dumps({'apk': 'app.apk', 'source': 'source.tar.gz'}))
        for name in ['app.apk', 'source.tar.gz', 'RELEASE-NOTES.md', 'SIGNING.txt', 'apk-verification.txt']:
            (package / name).write_bytes(b'synthetic fixture')
        (package / 'SHA256SUMS').write_text(''.join(release.digest(path) + '  ' + path.name + '\n' for path in sorted(package.iterdir())))
        (package / 'unexpected.txt').write_text('unverified')
        with self.assertRaisesRegex(ValueError, 'unverified extra'):
            release.verify(package, 'a' * 64)

    def manifest_fixture(self, extra_permission=False, weak_signature=False):
        names = [release.APP_ID + '.permission.FIND_SELECTION', release.APP_ID + '.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION']
        badging = "package: name='de.utrost.textr' versionCode='5' versionName='0.5-alpha'\nsdkVersion:'29'\ntargetSdkVersion:'36'\n"
        permissions = ''.join("uses-permission: name='" + name + "'\n" for name in names)
        if extra_permission:
            permissions += "uses-permission: name='android.permission.INTERNET'\n"
        xml = ''.join('    E: permission (line=1)\n      A: android:name(0x01010003)="' + name + '"\n      A: android:protectionLevel(0x01010009)=(type 0x11)' + ('0x0' if weak_signature else '0x2') + '\n' for name in names)
        return badging, permissions, xml

    def test_release_cannot_add_network_or_other_permissions(self):
        with patch.object(release, 'sdk_tools', return_value=Path('/unused')), patch.object(release, 'run', side_effect=self.manifest_fixture(extra_permission=True)):
            with self.assertRaisesRegex(ValueError, 'permissions differ'):
                release.inspect_manifest(Path('/unused.apk'), 5, '0.5-alpha')

    def test_release_cannot_weaken_signature_permissions(self):
        with patch.object(release, 'sdk_tools', return_value=Path('/unused')), patch.object(release, 'run', side_effect=self.manifest_fixture(weak_signature=True)):
            with self.assertRaisesRegex(ValueError, 'signature protection'):
                release.inspect_manifest(Path('/unused.apk'), 5, '0.5-alpha')

    def test_download_verification_rejects_corruption_and_unsafe_names_before_tools(self):
        package = self.root / 'package'
        package.mkdir()
        (package / 'BUILD.json').write_text(json.dumps({'apk': 'app.apk', 'source': 'source.tar.gz'}))
        (package / 'app.apk').write_bytes(b'corrupt')
        for line, message in [('0' * 64 + '  app.apk\n', 'mismatched'),
                              ('0' * 64 + '  ../escape\n', 'Unsafe')]:
            (package / 'SHA256SUMS').write_text(line)
            with self.assertRaisesRegex(ValueError, message):
                release.verify(package, 'a' * 64)


if __name__ == '__main__':
    unittest.main()
