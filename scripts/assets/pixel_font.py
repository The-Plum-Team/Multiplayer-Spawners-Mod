#!/usr/bin/env python3
"""Draw the settings menu's blocky pixel capitals into a Minecraft bitmap font.

Writes common/src/main/resources/assets/multiplayerspawners/textures/font/pixel.png and font/pixel.json
(PixelFont.java draws its title with them). Each glyph is a few rows of '#'; capitals are 5 rows
tall, accents take 2 of the 3 rows above and a cedilla or comma the 2 rows below. Every cell is
6 x 10 pixels, 16 cells per line. Characters missing here fall back to Minecraft's own font.

    python3 scripts/assets/pixel_font.py

Needs Pillow (e2e/requirements.txt).
"""
import json
from pathlib import Path

from PIL import Image

ASSETS = Path(__file__).resolve().parents[2] / "common/src/main/resources/assets/multiplayerspawners"

G = {
 'A': [".##.", "#..#", "####", "#..#", "#..#"],
 'B': ["###.", "#..#", "###.", "#..#", "###."],
 'C': [".###", "#...", "#...", "#...", ".###"],
 'D': ["###.", "#..#", "#..#", "#..#", "###."],
 'E': ["####", "#...", "###.", "#...", "####"],
 'F': ["####", "#...", "###.", "#...", "#..."],
 'G': [".###", "#...", "#.##", "#..#", ".###"],
 'H': ["#..#", "#..#", "####", "#..#", "#..#"],
 'I': ["###", ".#.", ".#.", ".#.", "###"],
 'J': ["..##", "...#", "...#", "#..#", ".##."],
 'K': ["#..#", "#.#.", "##..", "#.#.", "#..#"],
 'L': ["#...", "#...", "#...", "#...", "####"],
 'M': ["#...#", "##.##", "#.#.#", "#...#", "#...#"],
 'N': ["#..#", "##.#", "#.##", "#..#", "#..#"],
 'O': [".##.", "#..#", "#..#", "#..#", ".##."],
 'P': ["###.", "#..#", "###.", "#...", "#..."],
 'Q': [".##.", "#..#", "#..#", "#.#.", ".#.#"],
 'R': ["###.", "#..#", "###.", "#.#.", "#..#"],
 'S': [".###", "#...", ".##.", "...#", "###."],
 'T': ["###", ".#.", ".#.", ".#.", ".#."],
 'U': ["#..#", "#..#", "#..#", "#..#", ".##."],
 'V': ["#...#", "#...#", ".#.#.", ".#.#.", "..#.."],
 'W': ["#...#", "#...#", "#.#.#", "##.##", "#...#"],
 'X': ["#..#", "#..#", ".##.", "#..#", "#..#"],
 'Y': ["#.#", "#.#", ".#.", ".#.", ".#."],
 'Z': ["####", "...#", ".##.", "#...", "####"],
 '0': [".##.", "#.##", "##.#", "#..#", ".##."],
 '1': [".#.", "##.", ".#.", ".#.", "###"],
 '2': ["###.", "...#", ".##.", "#...", "####"],
 '3': ["###.", "...#", ".##.", "...#", "###."],
 '4': ["#..#", "#..#", "####", "...#", "...#"],
 '5': ["####", "#...", "###.", "...#", "###."],
 '6': [".##.", "#...", "###.", "#..#", ".##."],
 '7': ["####", "...#", "..#.", ".#..", ".#.."],
 '8': [".##.", "#..#", ".##.", "#..#", ".##."],
 '9': [".##.", "#..#", ".###", "...#", ".##."],
 '(': [".#", "#.", "#.", "#.", ".#"],
 ')': ["#.", ".#", ".#", ".#", "#."],
 ':': [".", "#", ".", "#", "."],
 '.': [".", ".", ".", ".", "#"],
 ',': [".", ".", ".", ".", "#", "#"],
 '!': ["#", "#", "#", ".", "#"],
 '?': ["###.", "...#", ".##.", "....", ".#.."],
 '-': ["...", "...", "###", "...", "..."],
 '/': ["..#", "..#", ".#.", "#..", "#.."],
 "'": ["#", "#", ".", ".", "."],
 '+': ["...", ".#.", "###", ".#.", "..."],
 '%': ["#..#", "...#", "..#.", ".#..", "#..#"],
 '&': [".#..", "#.#.", ".#..", "#.#.", ".#.#"],
 '¿': ["..#.", "....", ".##.", "#...", ".###"],
 '¡': ["#", ".", "#", "#", "#"],
}
ACCENTS = {  # two rows above the capitals, centred on the glyph
 'grave': ["#.", ".#"], 'acute': [".#", "#."], 'circ': [".#.", "#.#"],
 'tilde': [".#.#", "#.#."], 'uml': ["#.#", "..."], 'ring': [".#.", "#.#"],
}
BASES = {'À':('A','grave'),'Á':('A','acute'),'Â':('A','circ'),'Ã':('A','tilde'),'Ä':('A','uml'),'Å':('A','ring'),
 'È':('E','grave'),'É':('E','acute'),'Ê':('E','circ'),'Ë':('E','uml'),
 'Ì':('I','grave'),'Í':('I','acute'),'Î':('I','circ'),'Ï':('I','uml'),
 'Ò':('O','grave'),'Ó':('O','acute'),'Ô':('O','circ'),'Õ':('O','tilde'),'Ö':('O','uml'),
 'Ù':('U','grave'),'Ú':('U','acute'),'Û':('U','circ'),'Ü':('U','uml'),'Ñ':('N','tilde')}
CELL_W, CELL_H, CAP_TOP = 6, 10, 3

def cell(ch):
    rows = [['.'] * CELL_W for _ in range(CELL_H)]
    def put(pattern, top, left):
        for y, line in enumerate(pattern):
            for x, c in enumerate(line):
                if c == '#':
                    rows[top + y][left + x] = '#'
    if ch in BASES:
        base, accent = BASES[ch]
        glyph = G[base]
        put(glyph, CAP_TOP, 0)
        mark = ACCENTS[accent]
        put(mark, 0, max(0, (len(glyph[0]) - len(mark[0]) + 1) // 2))
    elif ch == 'Ç':
        put(G['C'], CAP_TOP, 0)
        put(["..#.", ".#.."], CAP_TOP + 5, 0)
    else:
        put(G[ch], CAP_TOP, 0)
    return rows

lines = ["ABCDEFGHIJKLMNOP", "QRSTUVWXYZ012345", "6789():.,!?-/'+%",
         "&¿¡ÀÁÂÃÄÅÈÉÊËÌÍÎ", "ÏÑÒÓÔÕÖÙÚÛÜÇ"]
lines = [l + "\u0000" * (16 - len(l)) for l in lines]
image = Image.new('RGBA', (16 * CELL_W, len(lines) * CELL_H), (0, 0, 0, 0))
for r, line in enumerate(lines):
    for c, ch in enumerate(line):
        if ch == "\u0000":
            continue
        for y, row in enumerate(cell(ch)):
            for x, v in enumerate(row):
                if v == '#':
                    image.putpixel((c * CELL_W + x, r * CELL_H + y), (255, 255, 255, 255))
(ASSETS / "textures" / "font").mkdir(parents=True, exist_ok=True)
(ASSETS / "font").mkdir(parents=True, exist_ok=True)
image.save(ASSETS / "textures" / "font" / "pixel.png")
font = {"providers": [
    {"type": "space", "advances": {" ": 3}},
    {"type": "bitmap", "file": "multiplayerspawners:font/pixel.png", "height": CELL_H, "ascent": 8, "chars": lines},
    {"type": "reference", "id": "minecraft:default"}]}
(ASSETS / "font" / "pixel.json").write_text(json.dumps(font, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
