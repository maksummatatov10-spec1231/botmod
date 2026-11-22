#!/usr/bin/env python3
"""Делает область «шляпы» (hat layer) в скине 64x64 прозрачной.

Слой hat рисуется поверх головы и в некоторых скинах залит непрозрачным чёрным —
тогда лица не видно (глаза оказываются под «шляпой»). Стандартная раскладка:
hat занимает прямоугольник (32,0)-(64,16).

Запуск: python3 tools/skin_clear_hat.py <вход.png> <выход.png>
"""
import struct
import sys
import zlib
from pathlib import Path


def read_rgba(path: Path):
    data = path.read_bytes()
    pos, idat, w, h = 8, b"", 0, 0
    while pos < len(data):
        ln = struct.unpack(">I", data[pos:pos + 4])[0]
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + ln]
        if tag == b"IHDR":
            w, h, depth, color = struct.unpack(">IIBB", body[:10])
            if depth != 8 or color != 6:
                raise SystemExit(f"нужен 8-бит RGBA PNG, получено depth={depth} color={color}")
        elif tag == b"IDAT":
            idat += body
        pos += 12 + ln

    raw = zlib.decompress(idat)
    stride = w * 4
    rows, prev, p = [], bytearray(stride), 0
    for _ in range(h):
        f = raw[p]; p += 1
        line = bytearray(raw[p:p + stride]); p += stride
        if f == 1:
            for i in range(4, stride):
                line[i] = (line[i] + line[i - 4]) & 255
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 255
        elif f == 3:
            for i in range(stride):
                left = line[i - 4] if i >= 4 else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 255
        elif f == 4:
            for i in range(stride):
                a = line[i - 4] if i >= 4 else 0
                b = prev[i]
                c = prev[i - 4] if i >= 4 else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 255
        rows.append(bytearray(line))
        prev = line
    return w, h, rows


def write_rgba(path: Path, w: int, h: int, rows):
    raw = b"".join(b"\x00" + bytes(row) for row in rows)

    def chunk(tag, body):
        return (struct.pack(">I", len(body)) + tag + body
                + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF))

    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("использование: skin_clear_hat.py <вход.png> <выход.png>")

    src, dst = Path(sys.argv[1]), Path(sys.argv[2])
    w, h, rows = read_rgba(src)

    cleared = 0
    for y in range(min(16, h)):
        for x in range(32, min(64, w)):
            offset = x * 4
            if rows[y][offset + 3] != 0:
                rows[y][offset + 3] = 0
                cleared += 1

    write_rgba(dst, w, h, rows)
    print(f"hat cleared: {cleared} px → {dst}")


if __name__ == "__main__":
    main()
