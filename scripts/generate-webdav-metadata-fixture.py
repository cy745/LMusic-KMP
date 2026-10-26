"""Add reproducible embedded artwork and timed lyrics to the synthetic P3 FLAC (stdlib only)."""
from pathlib import Path
import base64
import struct
root = Path(__file__).resolve().parents[1] / 'androidApp/src/androidTest/assets'
data = (root / 'p3/constant-16k.flac').read_bytes()
assert data[:4] == b'fLaC'
pos, blocks = 4, []
while True:
    flag = data[pos]
    length = int.from_bytes(data[pos+1:pos+4], 'big')
    body = data[pos+4:pos+4+length]
    if flag & 127 not in (4, 6):
        blocks.append((flag & 127, body))
    pos += 4 + length
    if flag & 128:
        break
vendor = b'LMusic test'
tags = [b'TITLE=Cache metadata fixture', b'ARTIST=LMusic test', b'ALBUM=Cache bridge', b'LYRICS=[00:00.00]Cached lyric\n[00:00.50]Second line']
comments = struct.pack('<I', len(vendor)) + vendor + struct.pack('<I', len(tags))
comments += b''.join(struct.pack('<I', len(tag)) + tag for tag in tags)
png = base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=')
picture = struct.pack('>II', 3, 9) + b'image/png' + struct.pack('>IIIIII', 0, 1, 1, 16, 0, len(png)) + png
blocks += [(4, comments), (6, picture)]
result = b'fLaC' + b''.join(bytes([kind | (128 if i == len(blocks)-1 else 0)]) + len(body).to_bytes(3, 'big') + body for i, (kind, body) in enumerate(blocks)) + data[pos:]
(root / 'webdav').mkdir(exist_ok=True)
(root / 'webdav/metadata.flac').write_bytes(result)
