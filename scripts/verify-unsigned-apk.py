#!/usr/bin/env python3
"""Verify the explicit F-Droid build has no APK/JAR signature and matches source defaults."""
import importlib.util
from pathlib import Path
import struct
import subprocess
import sys
import zipfile

spec=importlib.util.spec_from_file_location('release',Path(__file__).with_name('prepare-release.py'))
release=importlib.util.module_from_spec(spec);spec.loader.exec_module(release)
apk=Path(sys.argv[1] if len(sys.argv)>1 else 'app/build/outputs/apk/release/app-release-unsigned.apk').resolve()
code,name=release.version((release.REPO/'app/build.gradle.kts').read_text())
tools=release.sdk_tools()
result=subprocess.run([tools/'apksigner','verify','--verbose',apk],capture_output=True,text=True)
assert result.returncode!=0,'Expected unsigned F-Droid APK'
data=apk.read_bytes();end=data.rfind(b'PK\x05\x06');assert end>=0,'Missing ZIP directory'
central=struct.unpack_from('<I',data,end+16)[0]
assert data[max(0,central-16):central]!=b'APK Sig Block 42','Unexpected APK signing block'
with zipfile.ZipFile(apk) as archive:
    assert not any(n.startswith('META-INF/') and n.upper().endswith(('.SF','.RSA','.DSA','.EC')) for n in archive.namelist()),'Unexpected JAR signature'
    assert 'assets/licenses/Textr-GPL-3.0.txt' in archive.namelist(),'Missing license'
badging=release.run([tools/'aapt','dump','badging',apk])
assert f"package: name='de.utrost.textr' versionCode='{code}' versionName='{name}'" in badging,'Version differs from source'
assert 'application-debuggable' not in badging,'F-Droid APK is debuggable'
assert "sdkVersion:'29'" in badging and "targetSdkVersion:'36'" in badging
permissions=release.run([tools/'aapt','dump','permissions',apk])
assert 'android.permission.INTERNET' not in permissions
print(f'Unsigned, non-debuggable de.utrost.textr {name} (code {code}), API 29–36 verified')
