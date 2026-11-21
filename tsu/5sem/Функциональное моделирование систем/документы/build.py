#!/usr/bin/env python3
"""Собирает три DOCX (лабы 1-3) из документы/labN.md в документы/.

Диаграммы вставляются там, где стоят в тексте: ![подпись](img/file.png).
Требует /tmp/opencode/mdvenv (markdown, python-docx).
"""

import os
import re
import tempfile
from html.parser import HTMLParser

import markdown
from docx import Document
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Cm, Pt
from docx.oxml import OxmlElement
from docx.oxml.ns import qn

HERE = os.path.dirname(os.path.abspath(__file__))
DOCS = HERE
OUT = os.path.normpath(os.path.join(HERE, "../защита"))

LABS = [
    ("lab1.md", "Лабораторная 1. Прецеденты"),
    ("lab2.md", "Лабораторная 2. Модель предметной области"),
    ("lab3.md", "Лабораторная 3. Системные диаграммы последовательностей"),
]

INLINE = re.compile(r"(</?(?:strong|b|em|i|code)>)", re.I)


class DocBuilder(HTMLParser):
    def __init__(self, doc, base_dir):
        super().__init__(convert_charrefs=True)
        self.doc = doc
        self.base_dir = base_dir
        self.para = None
        self.buf = []
        self.list_depth = 0
        self.table = None
        self.row = []
        self.cell = None
        self.first_row = True
        self.bold = 0
        self.italic = 0
        self.code = 0

    def _flush(self):
        if self.para is None:
            return
        text = re.sub(r"\s+", " ", "".join(self.buf)).strip()
        if text:
            self._runs(self.para, text)
        self.buf = []
        self.para = None

    def _runs(self, para, text):
        for part in INLINE.split(text):
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

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if tag in ("h1", "h2", "h3", "h4"):
            self._flush()
            self.para = self.doc.add_paragraph(style=f"Heading {int(tag[1])}")
            return
        if tag == "p":
            if self.cell is not None:
                return
            self._flush()
            self.para = self.doc.add_paragraph()
            self.para.paragraph_format.first_line_indent = Cm(0)
            self.para.paragraph_format.left_indent = Cm(0)
            return
        if tag in ("ul", "ol"):
            self.list_depth += 1
            return
        if tag == "li":
            self._flush()
            self.para = self.doc.add_paragraph(style="List Bullet")
            return
        if tag == "table":
            self._flush()
            self.table = None
            self.first_row = True
            return
        if tag == "tr":
            self.row = []
            return
        if tag in ("td", "th"):
            self.cell = tag
            self.buf = []
            return
        if tag == "img" and a.get("src"):
            self._flush()
            path = os.path.join(self.base_dir, a["src"])
            if os.path.exists(path):
                self.doc.add_picture(path, width=Cm(16.4))
                self.doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
            return

    def handle_endtag(self, tag):
        if tag in ("h1", "h2", "h3", "h4"):
            self._flush()
        elif tag == "p":
            self._flush()
        elif tag in ("ul", "ol"):
            self.list_depth = max(0, self.list_depth - 1)
        elif tag == "li":
            self._flush()
        elif tag in ("td", "th"):
            self.row.append(re.sub(r"\s+", " ", "".join(self.buf)).strip())
            self.cell = None
            self.buf = []
        elif tag == "tr":
            if self.row:
                if self.table is None:
                    self.table = self.doc.add_table(rows=0, cols=len(self.row))
                    self.table.style = "Table Grid"
                cells = self.table.add_row().cells
                for i, value in enumerate(self.row[: len(cells)]):
                    cells[i].text = ""
                    run = cells[i].paragraphs[0].add_run(value)
                    run.font.size = Pt(9)
                    if self.first_row:
                        run.bold = True
                    self._mark_row(cells, header=self.first_row)
                self.first_row = False
            self.row = []
        elif tag == "table":
            self.table = None
            self.doc.add_paragraph()

    @staticmethod
    def _mark_row(cells, header=False):
        tr = cells[0]._tc.getparent()
        trPr = tr.get_or_add_trPr()
        cant = OxmlElement("w:cantSplit")
        trPr.append(cant)
        if header:
            tbl_header = OxmlElement("w:tblHeader")
            tbl_header.set(qn("w:val"), "true")
            trPr.append(tbl_header)

    def handle_comment(self, data):
        if "pagebreak" in data:
            self._flush()
            self.doc.add_page_break()

    def handle_data(self, data):
        if self.para is not None or self.cell is not None:
            self.buf.append(data)


def setup(doc):
    s = doc.sections[0]
    s.page_width = Cm(21)
    s.page_height = Cm(29.7)
    s.left_margin = Cm(2.2)
    s.right_margin = Cm(1.8)
    s.top_margin = Cm(2)
    s.bottom_margin = Cm(2)
    st = doc.styles["Normal"]
    st.font.name = "DejaVu Serif"
    st.font.size = Pt(10.5)
    st.paragraph_format.space_after = Pt(4)
    st.paragraph_format.line_spacing = 1.15



def fix_column_widths(doc, total_cm=17.0, number_col_cm=1.7):
    """В таблицах со столбцом «№» первый столбец 10% ширины, остальные поровну."""
    from docx.enum.table import WD_TABLE_ALIGNMENT  # noqa: F401
    for table in doc.tables:
        rows = table.rows
        if not rows:
            continue
        header = [cell.text.strip() for cell in rows[0].cells]
        if not header or header[0] not in ("№", "ID"):
            continue
        count = len(header)
        if count < 2:
            continue
        rest = (total_cm - number_col_cm) / (count - 1)
        widths = [number_col_cm] + [rest] * (count - 1)
        table.autofit = False
        tblPr = table._tbl.tblPr
        layout = OxmlElement("w:tblLayout")
        layout.set(qn("w:type"), "fixed")
        tblPr.append(layout)
        for row in rows:
            for index, cell in enumerate(row.cells[:count]):
                cell.width = Cm(widths[index])


def build(md_path, title, work):
    text = open(md_path, encoding="utf-8").read()
    html = markdown.markdown(text, extensions=["tables", "sane_lists"])
    html = re.sub(r"<h1>.*?</h1>", "", html, count=1, flags=re.S)

    doc = Document()
    setup(doc)
    doc.add_heading(title, level=0)
    builder = DocBuilder(doc, os.path.dirname(md_path))
    builder.feed(html)
    builder._flush()
    fix_column_widths(doc)
    out = os.path.join(OUT, title + ".docx")
    doc.save(out)
    return out


def main():
    work = tempfile.mkdtemp(prefix="fms-lab-")
    for md, title in LABS:
        path = os.path.join(DOCS, md)
        out = build(path, title, work)
        print(os.path.basename(out), round(os.path.getsize(out) / 1024), "КБ")


if __name__ == "__main__":
    main()
