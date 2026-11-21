#!/usr/bin/env python3
"""Собирает по одному DOCX на каждую лабораторную в «защита/».

Markdown -> HTML (модуль markdown) -> DOCX (python-docx, разбор HTML через html.parser).
Так таблицы и рисунки становятся настоящими объектами Word, а не картинкой страницы.

Требует: /tmp/opencode/mdvenv (markdown, python-docx).
Запуск: /tmp/opencode/mdvenv/bin/python build_lab_docs.py
"""

import os
import re
import shutil
import tempfile
from html.parser import HTMLParser
from io import BytesIO

import markdown
from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Cm, Pt

HERE = os.path.dirname(os.path.abspath(__file__))
SITE = os.path.normpath(os.path.join(HERE, "../.."))
REPO = os.path.join(SITE, "fms-course")
OUT = os.path.join(SITE, "защита")

LABS = [
    ("lab1-usecases", "Лаб 1 — Прецеденты"),
    ("lab2-domain-model", "Лаб 2 — Модель предметной области"),
    ("lab3-sequence-diagrams", "Лаб 3 — Диаграммы последовательностей"),
    ("lab4-communication-diagrams", "Лаб 4 — Диаграммы коммуникаций"),
    ("lab5-design-classes", "Лаб 5 — Классы проектирования"),
    ("lab6-implementation", "Лаб 6 — Реализация"),
]

INLINE = re.compile(r"(</?(?:strong|b|em|i|code)>)", re.I)


class DocBuilder(HTMLParser):
    def __init__(self, doc, base_dir):
        super().__init__(convert_charrefs=True)
        self.doc = doc
        self.base_dir = base_dir
        self.mode = None            # 'p' | 'h1'..'h4' | 'li' | 'pre'
        self.para = None
        self.buf = []
        self.list_depth = 0
        self.in_table = False
        self.table = None
        self.row = None
        self.cell = None
        self.cell_text = []
        self.bold = 0
        self.italic = 0
        self.code = 0
        self.first_row = True

    # --- helpers ---
    def _flush_para(self):
        if self.para is None:
            return
        text = "".join(self.buf).strip()
        if text:
            self._add_runs(self.para, text)
        self.buf = []
        self.para = None

    def _add_runs(self, para, text):
        parts = INLINE.split(text)
        for part in parts:
            if not part:
                continue
            low = part.lower()
            if low.startswith("<strong") or low.startswith("<b"):
                self.bold += 1
                continue
            if low in ("</strong>", "</b>"):
                self.bold = max(0, self.bold - 1)
                continue
            if low.startswith("<em") or low.startswith("<i"):
                self.italic += 1
                continue
            if low in ("</em>", "</i>"):
                self.italic = max(0, self.italic - 1)
                continue
            if low.startswith("<code"):
                self.code += 1
                continue
            if low == "</code>":
                self.code = max(0, self.code - 1)
                continue
            run = para.add_run(part)
            run.bold = self.bold > 0
            run.italic = self.italic > 0
            if self.code:
                run.font.name = "DejaVu Sans Mono"
                run.font.size = Pt(9)

    # --- parser events ---
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag in ("h1", "h2", "h3", "h4"):
            self._flush_para()
            self.mode = tag
            self.para = self.doc.add_paragraph(style=f"Heading {int(tag[1])}")
            return
        if tag == "p":
            if self.cell is not None:
                return
            self._flush_para()
            self.para = self.doc.add_paragraph()
            return
        if tag == "pre":
            self._flush_para()
            self.mode = "pre"
            self.buf = []
            return
        if tag in ("ul", "ol"):
            self.list_depth += 1
            return
        if tag == "li":
            self._flush_para()
            style = "List Bullet" if self.list_depth <= 1 else "List Bullet 2"
            self.para = self.doc.add_paragraph(style=style)
            return
        if tag == "table":
            self._flush_para()
            self.in_table = True
            self.table = None
            self.first_row = True
            return
        if tag == "tr":
            self.cell_text = []
            self.row = []
            return
        if tag in ("td", "th"):
            self.cell = tag
            self.cell_text = []
            return
        if tag == "img" and a.get("src"):
            self._flush_para()
            path = os.path.join(self.base_dir, a["src"])
            if os.path.exists(path):
                self.doc.add_picture(path, width=Cm(16.5))
                self.doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
            return
        if tag == "br" and self.para is not None:
            self.buf.append("\n")

    def handle_endtag(self, tag):
        if tag in ("h1", "h2", "h3", "h4"):
            self._flush_para()
            self.mode = None
            return
        if tag == "p":
            self._flush_para()
            return
        if tag == "pre":
            text = "".join(self.buf)
            p = self.doc.add_paragraph()
            run = p.add_run(text.strip())
            run.font.name = "DejaVu Sans Mono"
            run.font.size = Pt(8.5)
            self.buf = []
            self.mode = None
            return
        if tag in ("ul", "ol"):
            self.list_depth = max(0, self.list_depth - 1)
            return
        if tag == "li":
            self._flush_para()
            return
        if tag in ("td", "th"):
            self.row.append("".join(self.cell_text).strip())
            self.cell = None
            return
        if tag == "tr":
            if self.row:
                if self.table is None:
                    self.table = self.doc.add_table(rows=0, cols=len(self.row))
                    self.table.style = "Table Grid"
                cells = self.table.add_row().cells
                for i, value in enumerate(self.row[: len(cells)]):
                    cells[i].text = ""
                    para = cells[i].paragraphs[0]
                    run = para.add_run(value)
                    run.font.size = Pt(9)
                    if self.first_row:
                        run.bold = True
                self.first_row = False
            self.row = []
            return
        if tag == "table":
            self.in_table = False
            self.table = None
            self.doc.add_paragraph()

    def handle_data(self, data):
        if self.cell is not None:
            self.cell_text.append(data)
        elif self.para is not None or self.mode == "pre":
            self.buf.append(data)


def md_to_html(text):
    return markdown.markdown(text, extensions=["tables", "fenced_code", "sane_lists"])


def diagram_section(lab_dir, work):
    pngs = []
    for root, _dirs, files in os.walk(lab_dir):
        for name in sorted(files):
            if name.endswith(".png"):
                pngs.append(os.path.join(root, name))
    if not pngs:
        return ""
    parts = ["<h2>Диаграммы</h2>"]
    for i, path in enumerate(pngs, start=1):
        local = f"img{i:02d}.png"
        shutil.copyfile(path, os.path.join(work, local))
        title = os.path.splitext(os.path.basename(path))[0]
        rel = os.path.relpath(path, lab_dir)
        parts.append(f'<p><img src="{local}"></p><p><em>{title.replace("_", " ")}</em></p>')
    return "\n".join(parts)


def setup(doc):
    section = doc.sections[0]
    section.page_width = Cm(21)
    section.page_height = Cm(29.7)
    section.left_margin = Cm(2)
    section.right_margin = Cm(1.5)
    section.top_margin = Cm(1.8)
    section.bottom_margin = Cm(1.8)
    style = doc.styles["Normal"]
    style.font.name = "DejaVu Serif"
    style.font.size = Pt(10.5)
    style.paragraph_format.space_after = Pt(4)
    style.paragraph_format.line_spacing = 1.15


def extra_docs(lab_dir):
    """Дополнительные Markdown-файлы лаборатории: спецификации, контракты, карты трассировки.
    Пропускаем README и файлы, у которых есть одноимённая диаграмма."""
    pngs = {
        os.path.splitext(f)[0]
        for _root, _dirs, files in os.walk(lab_dir)
        for f in files
        if f.endswith(".png")
    }
    parts = []
    for root, _dirs, files in os.walk(lab_dir):
        for name in sorted(files):
            if not name.endswith(".md") or name.lower() == "readme.md":
                continue
            if os.path.splitext(name)[0] in pngs:
                continue
            path = os.path.join(root, name)
            rel = os.path.relpath(path, lab_dir)
            parts.append(f"<h2>{rel}</h2>")
            parts.append(md_to_html(open(path, encoding="utf-8").read()))
    return "\n".join(parts)


def build(lab_dir, title, work):
    doc = Document()
    setup(doc)
    doc.add_heading(title, level=0)
    readme = os.path.join(lab_dir, "README.md")
    html = ""
    if os.path.exists(readme):
        html += md_to_html(open(readme, encoding="utf-8").read())
    html += extra_docs(lab_dir)
    html += diagram_section(lab_dir, work)
    if os.path.exists(readme):
        html = re.sub(r"<h1>.*?</h1>", "", html, count=1, flags=re.S)

    builder = DocBuilder(doc, work)
    builder.feed(html)
    builder._flush_para()
    return doc


def main():
    work = tempfile.mkdtemp(prefix="fms-docx-")
    for folder, title in LABS:
        lab_dir = os.path.join(REPO, folder)
        if not os.path.isdir(lab_dir):
            print("нет папки:", lab_dir)
            continue
        doc = build(lab_dir, title, work)
        target = os.path.join(OUT, title + ".docx")
        doc.save(target)
        kbytes = round(os.path.getsize(target) / 1024)
        prints = f"{title}.docx — {kbytes} КБ"
        print(prints)
    shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
