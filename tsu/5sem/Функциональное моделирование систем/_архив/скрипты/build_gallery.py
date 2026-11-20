#!/usr/bin/env python3
"""Собирает «защита/ФМС — все диаграммы.pdf»: оглавление + все диаграммы из защита/диаграммы/
по одной на страницу A4 landscape. Закладки (bookmark) дают навигацию
по боковой панели PDF-просмотрщика.

Запуск:  python3 build_gallery.py
"""

import os
import subprocess

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SITE = os.path.normpath(os.path.join(HERE, "../.."))
BASE = os.path.join(SITE, "защита/диаграммы")
OUT = os.path.join(SITE, "защита/ФМС — все диаграммы.pdf")
WORK = "/tmp/opencode/fms-gallery"

GROUPS = [
    ("1-прецеденты", "Лаб. 1 · модель прецедентов"),
    ("2-модель", "Лаб. 2 · модель предметной области"),
    ("3-последовательности", "Лаб. 3 · диаграммы последовательностей"),
    ("4-коммуникации", "Лаб. 4 · диаграммы коммуникаций"),
    ("5-классы", "Лаб. 5 · классы проектирования"),
]

BOX_W, BOX_H = 268.0, 158.0  # мм под картинку

rows = []
for folder, headline in GROUPS:
    d = os.path.join(BASE, folder)
    for name in sorted(os.listdir(d)):
        if name.endswith(".png"):
            rows.append((headline, name[:-4], os.path.join(d, name)))

os.makedirs(WORK, exist_ok=True)

parts = ["""<!DOCTYPE html><html lang="ru"><head><meta charset="utf-8">
<title>ФМС — все диаграммы</title>
<style>
@page { size: 297mm 210mm; margin: 12mm; }
html { font-family: "DejaVu Sans", sans-serif; color: #1b2028; }
body { margin: 0; }
section { break-after: page; page-break-after: always; }
section:last-child { break-after: auto; }
.cap { display: flex; justify-content: space-between; align-items: baseline;
       font-size: 9pt; letter-spacing: .08em; text-transform: uppercase; color: #8a929d; }
h2 { font-size: 15pt; color: #1c3554; margin: 2mm 0 4mm; bookmark-level: 1; bookmark-label: content(); }
figure { margin: 0; text-align: center; }
img { display: block; margin: 0 auto; }
h1 { font-size: 26pt; color: #1c3554; margin: 0 0 2mm; bookmark-level: 1; }
.sub { font-size: 11pt; color: #55606f; margin: 0 0 8mm; }
table { border-collapse: collapse; font-size: 8.5pt; }
td { padding: 0.8mm 4mm 0.8mm 0; vertical-align: top; border-bottom: 0.2mm solid #eef1f5; }
td.n { color: #8a929d; width: 10mm; font-variant-numeric: tabular-nums; }
td.g { color: #c8611f; width: 14mm; white-space: nowrap; }
thead td { font-size: 8pt; text-transform: uppercase; letter-spacing: .07em; color: #8a929d;
           border-bottom: 0.4mm solid #c9d2dd; }
</style></head><body>"""]

parts.append('<section><h1>Все диаграммы ФМС</h1>')
parts.append(f'<p class="sub">{len(rows)} диаграммы из <b>github.com/mflkee/fms-course</b>, рендер 200 dpi. '
             'Слева — номер страницы; в боковой панели PDF — закладки по диаграммам.</p>')

half = (len(rows) + 1) // 2
for start in (0, half):
    side = "left" if start == 0 else "right"
    parts.append(f'<table style="width:49%;float:{side}"><thead><tr>'
                 '<td class="n">стр.</td><td class="g">раб.</td><td>диаграмма</td></tr></thead><tbody>')
    for i, (headline, title, path) in enumerate(rows, start=2):
        if start <= i - 2 < start + half:
            lab = headline.split(" · ")[0].replace("Лаб. ", "")
            parts.append(f'<tr><td class="n">{i}</td><td class="g">{lab}</td><td>{title}</td></tr>')
    parts.append("</tbody></table>")
parts.append("</section>")

for i, (headline, title, path) in enumerate(rows, start=2):
    src = Image.open(path)
    w, h = src.size
    # для PDF достаточно ~2000 px по длинной стороне — файл легче в разы
    scale_px = min(1.0, 2000.0 / max(w, h))
    if scale_px < 1.0:
        src = src.resize((int(w * scale_px), int(h * scale_px)), Image.LANCZOS)
        path = os.path.join(WORK, f"p{i:02d}.png")
        src.save(path, optimize=True)
        w, h = src.size
    scale = min(BOX_W / w, BOX_H / h)
    parts.append(
        f'<section><div class="cap"><span>{headline}</span><span>{i} / {len(rows) + 1}</span></div>'
        f'<h2>{title}</h2>'
        f'<figure><img src="file://{path}" style="width:{w * scale:.1f}mm;height:{h * scale:.1f}mm">'
        f"</figure></section>"
    )

parts.append("</body></html>")

html = os.path.join(WORK, "gallery.html")
open(html, "w", encoding="utf-8").write("\n".join(parts))
subprocess.run(["uvx", "weasyprint", html, OUT], check=True)
print("готово:", OUT)
