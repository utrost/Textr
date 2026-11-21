#!/usr/bin/env python3
"""Check source-owned English/German listing limits, versioned changelogs and PNG CRCs."""
from pathlib import Path
import re
import struct
import zlib

root=Path(__file__).resolve().parents[1]
gradle=(root/'app/build.gradle.kts').read_text()
code=int(re.search(r'versionCode\s*=.*?\?:\s*(\d+)',gradle)[1])
for locale in ['en-US','de-DE']:
    directory=root/'fastlane/metadata/android'/locale
    for name,maximum in [('title.txt',50),('short_description.txt',80),('full_description.txt',4000),(f'changelogs/{code}.txt',500)]:
        text=(directory/name).read_text().strip()
        assert 0<len(text)<=maximum,(locale,name,'length')
        assert '<script' not in text.lower(),(locale,name,'script')
        if name=='short_description.txt': assert '\n' not in text and not text.endswith('.'),(locale,'summary formatting')
    pngs=list((directory/'images').rglob('*.png'))
    assert (directory/'images/icon.png') in pngs and list((directory/'images/phoneScreenshots').glob('*.png'))
    for path in pngs:
        data=path.read_bytes();assert data[:8]==b'\x89PNG\r\n\x1a\n',path
        at=8;saw_end=False
        while at<len(data):
            size=struct.unpack('>I',data[at:at+4])[0];kind=data[at+4:at+8];payload=data[at+8:at+8+size]
            crc=struct.unpack('>I',data[at+8+size:at+12+size])[0]
            assert zlib.crc32(kind+payload)&0xffffffff==crc,path
            if kind==b'IHDR':
                width,height=struct.unpack('>II',payload[:8]);assert width>0 and height>0,path
            at+=size+12
            if kind==b'IEND':saw_end=True;break
        assert saw_end and at==len(data),path
print(f'English/German metadata, version-code {code} changelogs and all PNGs validated')
