#!/usr/bin/env python3
"""Минимальный конвертер Markdown -> HTML -> PDF (LibreOffice), без внешних зависимостей.

Использование:
    python tools/md2pdf.py input.md output.html [--pdf output.pdf] [--title "..."]

Поддерживает: заголовки, абзацы, **жирный**, *курсив*, `код`, ```блоки кода```,
маркированные и нумерованные списки, таблицы, горизонтальные линии.
"""
from __future__ import annotations

import argparse
import html
import re
import shutil
import subprocess
import sys
from pathlib import Path

CSS = """
@page { size: A4; margin: 18mm 16mm 16mm 16mm; }
body { font-family: "Noto Sans", "DejaVu Sans", sans-serif; font-size: 10.5pt;
       line-height: 1.45; color: #1a1a1a; max-width: 100%; }
h1, div.h1 { font-size: 19pt; font-weight: 700; margin: 0 0 2mm 0; line-height: 1.2;
         color: #111; text-indent: 0; }
h2, div.h2 { font-size: 13.5pt; font-weight: 700; margin: 7mm 0 2mm 0; padding-bottom: 1mm;
         border-bottom: 1px solid #d8d8d8; color: #111; text-indent: 0; }
h3, div.h3 { font-size: 11.5pt; font-weight: 700; margin: 5mm 0 1.5mm 0; color: #222;
         text-indent: 0; }
h4, div.h4 { font-size: 10.5pt; font-weight: 700; margin: 4mm 0 1mm 0; color: #333;
         text-indent: 0; }
p { margin: 0 0 2.2mm 0; text-align: justify; }
ul, ol { margin: 0 0 2.5mm 0; padding-left: 6mm; }
li { margin-bottom: 1mm; }
code { font-family: "Noto Sans Mono", "DejaVu Sans Mono", monospace; font-size: 9pt;
       background: #f2f2f2; padding: 0.3mm 1mm; border-radius: 1.5mm; }
pre { background: #f6f6f6; border: 1px solid #e2e2e2; border-radius: 2mm;
      padding: 2.5mm 3mm; margin: 0 0 3mm 0; overflow: hidden; }
pre code { background: none; font-size: 8.6pt; line-height: 1.35; }
table { border-collapse: collapse; width: 100%; margin: 1mm 0 4mm 0; font-size: 9.2pt; }
th, td { border: 1px solid #cfcfcf; padding: 1.6mm 2mm; text-align: left;
         vertical-align: top; }
th { background: #efefef; font-weight: 600; }
hr { border: none; border-top: 1px solid #dcdcdc; margin: 5mm 0; }
.meta { color: #555; font-size: 9.5pt; margin: 0 0 6mm 0; padding-bottom: 2.5mm;
        border-bottom: 2px solid #333; }
blockquote { margin: 0 0 3mm 0; padding: 1mm 0 1mm 4mm; border-left: 3px solid #bbb;
             color: #444; }
"""


def inline(text: str) -> str:
    """Экранирование + инлайновая разметка."""
    stash: list[str] = []

    def keep(fragment: str) -> str:
        stash.append(fragment)
        return f"\x00{len(stash) - 1}\x00"

    text = re.sub(r"`([^`]+)`", lambda m: keep(f"<code>{html.escape(m.group(1))}</code>"), text)
    text = html.escape(text, quote=False)
    text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"(?<![\w*])\*([^*\n]+)\*(?![\w*])", r"<em>\1</em>", text)
    text = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2">\1</a>', text)
    text = re.sub(r"\x00(\d+)\x00", lambda m: stash[int(m.group(1))], text)
    return text


def convert(md: str) -> str:
    lines = md.split("\n")
    out: list[str] = []
    i = 0
    list_stack: list[str] = []

    def close_lists() -> None:
        while list_stack:
            out.append(f"</{list_stack.pop()}>")

    while i < len(lines):
        line = lines[i]
        stripped = line.strip()

        # блок кода
        if stripped.startswith("```"):
            close_lists()
            lang = stripped[3:].strip()
            i += 1
            buf = []
            while i < len(lines) and not lines[i].strip().startswith("```"):
                buf.append(lines[i])
                i += 1
            i += 1
            body = html.escape("\n".join(buf))
            cls = f' class="lang-{html.escape(lang)}"' if lang else ""
            out.append(f"<pre><code{cls}>{body}</code></pre>")
            continue

        # таблица
        if stripped.startswith("|") and i + 1 < len(lines) and re.match(
            r"^\|[\s:|-]+\|$", lines[i + 1].strip()
        ):
            close_lists()
            header = [c.strip() for c in stripped.strip("|").split("|")]
            i += 2
            rows = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                rows.append([c.strip() for c in lines[i].strip().strip("|").split("|")])
                i += 1
            out.append("<table><thead><tr>")
            out.extend(f"<th>{inline(c)}</th>" for c in header)
            out.append("</tr></thead><tbody>")
            for row in rows:
                out.append("<tr>")
                out.extend(f"<td>{inline(c)}</td>" for c in row)
                out.append("</tr>")
            out.append("</tbody></table>")
            continue

        # заголовки
        m = re.match(r"^(#{1,4})\s+(.*)$", stripped)
        if m:
            close_lists()
            level = len(m.group(1))
            out.append(f'<div class="h{level}">{inline(m.group(2))}</div>')
            i += 1
            continue

        # горизонтальная линия
        if re.match(r"^(-{3,}|\*{3,}|_{3,})$", stripped):
            close_lists()
            out.append("<hr>")
            i += 1
            continue

        # списки
        m = re.match(r"^(\s*)([-*+]|\d+[.)])\s+(.*)$", line)
        if m:
            indent, marker, body = m.group(1), m.group(2), m.group(3)
            tag = "ol" if marker[0].isdigit() else "ul"
            depth = len(indent) // 2
            if not list_stack:
                list_stack.append(tag)
                out.append(f"<{tag}>")
            elif depth >= len(list_stack):
                list_stack.append(tag)
                out.append(f"<{tag}>")
            while depth < len(list_stack) - 1:
                out.append(f"</{list_stack.pop()}>")
            out.append(f"<li>{inline(body)}</li>")
            i += 1
            continue

        # пустая строка
        if not stripped:
            close_lists()
            i += 1
            continue

        # абзац (с продолжением строк; «\» в конце строки = перенос)
        close_lists()
        buf = [stripped]
        i += 1
        while i < len(lines) and lines[i].strip() and not re.match(
            r"^(#{1,4}\s|```|\||\s*([-*+]|\d+[.)])\s)", lines[i]
        ):
            buf.append(lines[i].strip())
            i += 1
        rendered = []
        for part in buf:
            hard = part.endswith("\\")
            if hard:
                part = part[:-1].rstrip()
            rendered.append(inline(part) + ("<br>" if hard else ""))
        text = re.sub(r"\s*<br>", "<br>", " ".join(rendered))
        out.append(f"<p>{text}</p>")

    close_lists()
    return "\n".join(out)


def build_html(md_path: Path, title: str | None) -> str:
    source = md_path.read_text(encoding="utf-8")
    try:  # полноценный парсер, если установлен (python-markdown)
        import markdown as _md

        body = _md.markdown(
            source,
            extensions=["tables", "fenced_code", "sane_lists", "attr_list", "md_in_html"],
            output_format="html5",
        )
    except Exception:  # запасной вариант — встроенный минимальный конвертер
        body = convert(source)
    head_title = title or md_path.stem
    return f"""<!DOCTYPE html>
<html lang="ru"><head><meta charset="utf-8">
<title>{html.escape(head_title)}</title>
<style>{CSS}</style></head>
<body>
{body}
</body></html>"""


def html_to_pdf(html_path: Path, pdf_path: Path) -> None:
    """Chromium печатает PDF, полностью соблюдая CSS (LibreOffice искажает заголовки)."""
    chromium = shutil.which("chromium") or shutil.which("chromium-browser")
    if chromium:
        subprocess.run(
            [
                chromium,
                "--headless=new",
                "--disable-gpu",
                "--no-sandbox",
                "--no-pdf-header-footer",
                "--virtual-time-budget=3000",
                f"--print-to-pdf={pdf_path}",
                html_path.resolve().as_uri(),
            ],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=180,
        )
        return
    subprocess.run(
        [
            "soffice",
            "--headless",
            "--norestore",
            "--convert-to",
            "pdf",
            "--outdir",
            str(pdf_path.parent),
            str(html_path),
        ],
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=180,
    )


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("input", type=Path)
    ap.add_argument("html", type=Path, nargs="?")
    ap.add_argument("--pdf", type=Path)
    ap.add_argument("--title")
    args = ap.parse_args()

    html_path = args.html or args.input.with_suffix(".html")
    html_path.write_text(build_html(args.input, args.title), encoding="utf-8")
    print(f"html: {html_path}")

    if args.pdf:
        html_to_pdf(html_path, args.pdf)
        print(f"pdf:  {args.pdf}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
