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
from docx.shared import Cm, Pt, RGBColor
import re as _re
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



COLORS = {
    "g": RGBColor(0x1E, 0x7D, 0x32),  # зелёный: выполнено
    "o": RGBColor(0xC0, 0x62, 0x00),  # оранжевый: требуется действие
    "r": RGBColor(0xC0, 0x2B, 0x1F),  # красный: нарушение или ошибка
    "b": RGBColor(0x1C, 0x35, 0x54),  # синий: служебное
}
COLOR_RE = _re.compile(r"\[\[([gorb]):(.+?)\]\]")


def add_colored_text(paragraph, text, size=None):
    pos = 0
    for match in COLOR_RE.finditer(text):
        if match.start() > pos:
            run = paragraph.add_run(text[pos:match.start()])
            if size:
                run.font.size = size
        run = paragraph.add_run(match.group(2))
        run.font.color.rgb = COLORS[match.group(1)]
        if size:
            run.font.size = size
        pos = match.end()
    if pos < len(text):
        run = paragraph.add_run(text[pos:])
        if size:
            run.font.size = size


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
            add_colored_text(para, part)
            if isinstance(para.runs[-1], object):
                pass
            last_runs = para.runs
            for run in last_runs[-max(1, len(COLOR_RE.findall(part)) + 1):]:
                run.bold = self.bold > 0 or run.bold
                run.italic = self.italic > 0 or run.italic
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
                self.doc.add_picture(path, width=Cm(15.0))
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
                    paragraph = cells[i].paragraphs[0]
                    add_colored_text(paragraph, value, size=Pt(9))
                    if self.first_row:
                        for run in paragraph.runs:
                            run.bold = True
                    self._mark_row(cells, header=self.first_row)
                self.first_row = False
            self.row = []
        elif tag == "table":
            self.table = None

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
    st.paragraph_format.line_spacing = 1.12



def fix_column_widths(doc, total_cm=17.0, number_col_cm=1.7):
    """Растягивает таблицы на всю ширину текста.

    В таблицах со столбцом «№»/«ID» он занимает около 10%, остальные делятся поровну.
    В остальных ширина колонок пропорциональна объёму текста, но не меньше 2 см.
    """
    for table in doc.tables:
        rows = table.rows
        if not rows:
            continue
        header = [cell.text.strip() for cell in rows[0].cells]
        count = len(header)
        if not count:
            continue

        if header[0] in ("№", "ID") and count > 1:
            rest = (total_cm - number_col_cm) / (count - 1)
            widths = [number_col_cm] + [rest] * (count - 1)
        else:
            weights = []
            body = rows[1:] if len(rows) > 1 else rows
            for index in range(count):
                lengths = [
                    len(row.cells[index].text.strip())
                    for row in body
                    if index < len(row.cells)
                ]
                average = sum(lengths) / len(lengths) if lengths else 1
                weights.append(max(5.0, min(average, 80.0)))
            base = 3.2 if count <= 3 else 2.6
            if base * count > total_cm:
                base = total_cm / count
            free = total_cm - base * count
            weight_sum = sum(weights)
            widths = [base + free * weight / weight_sum for weight in weights]

        table.autofit = False
        tbl = table._tbl
        tblPr = tbl.tblPr
        layout = OxmlElement("w:tblLayout")
        layout.set(qn("w:type"), "fixed")
        tblPr.append(layout)

        tblW = OxmlElement("w:tblW")
        tblW.set(qn("w:type"), "dxa")
        tblW.set(qn("w:w"), str(int(Cm(sum(widths)).twips)))
        tblPr.append(tblW)

        for grid_col, width in zip(tbl.tblGrid.gridCol_lst, widths):
            grid_col.set(qn("w:w"), str(int(Cm(width).twips)))

        for row in rows:
            for index, cell in enumerate(row.cells[:count]):
                cell.width = Cm(widths[index])


def drop_empty_before_images(doc):
    """Убирает пустой абзац, который markdown ставит перед рисунком.

    Иначе заголовок с keepNext «держится» за пустой абзац, а рисунок уезжает
    на следующую страницу.
    """
    for paragraph in list(doc.paragraphs):
        has_image = paragraph._p.findall(
            ".//{http://schemas.openxmlformats.org/wordprocessingml/2006/main}drawing"
        )
        if not has_image:
            continue
        previous = paragraph._p.getprevious()
        if previous is None:
            continue
        text = "".join(previous.itertext()).strip()
        has_content = previous.findall(
            ".//{http://schemas.openxmlformats.org/wordprocessingml/2006/main}drawing"
        )
        if not text and not has_content:
            previous.getparent().remove(previous)


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
    drop_empty_before_images(doc)
    for paragraph in doc.paragraphs:
        if paragraph.style.name.startswith("Heading"):
            paragraph.paragraph_format.keep_with_next = True
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
