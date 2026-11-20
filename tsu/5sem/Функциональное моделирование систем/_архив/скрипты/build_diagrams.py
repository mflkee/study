#!/usr/bin/env python3
"""Рендерит диаграммы ФМС из репозитория fms-course в защита/диаграммы.

- источник: ~/projects/study/tsu/5sem/Функциональное моделирование систем/fms-course
  (переопределяется переменной FMS_COURSE)
- рендер: PlantUML, PNG, 200 dpi, PLANTUML_LIMIT_SIZE=20000 (иначе PlantUML
  обрезает большие диаграммы квадратом 4096×4096)
- имена файлов — русские, с номерами, чтобы сортировались по порядку
- заодно пересобирает индекс защита/диаграммы/README.md

Состав: 1 (прецеденты) + 1 (предметная область) + 8 (SSD) + 10 (коммуникации)
+ 3 (классы проектирования) = 23 диаграммы.

Запуск:  python3 build_diagrams.py
"""

import os
import shutil
import subprocess
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
BASE = os.path.normpath(os.path.join(HERE, "../../защита/диаграммы"))
REPO = os.environ.get(
    "FMS_COURSE",
    os.path.expanduser(
        "~/projects/study/tsu/5sem/Функциональное моделирование систем/fms-course"
    ),
)

L3_TITLES = [
    ("01-p01-osnovnoy", "П01 Учёт патента — основной сценарий"),
    ("02-p01-alternativa-dubl", "П01 — альтернатива: дубль патента"),
    ("03-p02-osnovnoy", "П02 Трудоустройство — основной сценарий"),
    ("04-p02-alternativa-otkaz", "П02 — альтернатива: отказ работодателя"),
    ("05-p03-osnovnoy", "П03 Уведомление МВД — основной сценарий (ЕПГУ)"),
    ("06-p03-alternativa-pochta", "П03 — альтернатива: почта и возврат отправления"),
    ("07-p04-osnovnoy", "П04 Контроль срока — основной сценарий"),
    ("08-p04-alternativa-prosrochka", "П04 — альтернатива: просрочка срока"),
]

L4_TITLES = [
    ("01-zaregistrirovat-patent", "зарегистрировать патент"),
    ("02-pometit-patent-arhivnym", "пометить патент архивным"),
    ("03-poluchit-svedeniya-o-patente", "получить сведения о патенте"),
    ("04-zaklyuchit-dogovor", "заключить договор"),
    ("05-otmenit-dogovor", "отменить договор"),
    ("06-napravit-uvedomlenie-mvd", "направить уведомление МВД"),
    ("07-zafiksirovat-vozvrat-uvedomleniya", "зафиксировать возврат уведомления"),
    ("08-pereschitat-sroki-patenta", "пересчитать сроки патента"),
    ("09-nastroit-napominaniya", "настроить напоминания"),
    ("10-zafiksirovat-narushenie-sroka", "зафиксировать нарушение срока"),
]

L5_TITLES = [
    ("01-layers", "Слои модели проектирования (BCE, хранилища, шлюзы)"),
    ("02-patent-dogovor", "Классы: патент и договор"),
    ("03-uvedomleniya-sroki", "Классы: уведомления и сроки"),
]

GROUPS = {
    "1-прецеденты": "Лаб. 1 · модель прецедентов",
    "2-модель": "Лаб. 2 · модель предметной области",
    "3-последовательности": "Лаб. 3 · диаграммы последовательностей (SSD)",
    "4-коммуникации": "Лаб. 4 · диаграммы коммуникаций",
    "5-классы": "Лаб. 5 · классы проектирования",
}

work = tempfile.mkdtemp(prefix="fms-puml-")
items = []
counter = 0


def render(src, group, filename, caption):
    """PlantUML берёт имя файла из @startuml, поэтому рендерим под ASCII-именем
    и переименовываем результат (кириллица и запятые в именах ломают вывод)."""
    global counter
    counter += 1
    key = f"fms{counter:03d}"
    d = os.path.join(BASE, group)
    os.makedirs(d, exist_ok=True)
    tmp = os.path.join(work, key + ".puml")
    text = open(src, encoding="utf-8").read()
    head, rest = text.split("\n", 1)
    if head.startswith("@startuml"):
        text = f"@startuml {key}\n" + rest
    open(tmp, "w", encoding="utf-8").write(text)
    env = dict(os.environ, PLANTUML_LIMIT_SIZE="20000")
    subprocess.run(["plantuml", "-tpng", "-Sdpi=200", "-o", d, tmp], check=True, env=env)
    target = os.path.join(d, filename + ".png")
    os.replace(os.path.join(d, key + ".png"), target)
    items.append((group, caption, target))


render(
    os.path.join(REPO, "lab1-usecases/usecase-diagram.puml"),
    "1-прецеденты",
    "Диаграмма прецедентов",
    "Лаб. 1 · диаграмма прецедентов — 4 прецедента, 4 актора",
)

render(
    os.path.join(REPO, "lab2-domain-model/class-diagram.puml"),
    "2-модель",
    "Диаграмма классов предметной области",
    "Лаб. 2 · диаграмма классов — 13 классов, семь инвариантов",
)

for num, (code, title) in enumerate(L3_TITLES, start=1):
    render(
        os.path.join(REPO, "lab3-sequence-diagrams/diagrams", code + ".puml"),
        "3-последовательности",
        f"{num:02d} — {title}",
        f"Лаб. 3 · SSD: {title}",
    )

for num, (code, title) in enumerate(L4_TITLES, start=1):
    render(
        os.path.join(REPO, "lab4-communication-diagrams/diagrams", code + ".puml"),
        "4-коммуникации",
        f"{num:02d} — {title}",
        f"Лаб. 4 · коммуникации: операция {num} — {title}",
    )

for num, (code, title) in enumerate(L5_TITLES, start=1):
    render(
        os.path.join(REPO, "lab5-design-classes/diagrams", code + ".puml"),
        "5-классы",
        f"{num:02d} — {title}",
        f"Лаб. 5 · классы: {title}",
    )

shutil.rmtree(work, ignore_errors=True)

total = len(items)
lines = [
    f"# Диаграммы ФМС — все {total}",
    "",
    "Отсюда открывается любая диаграмма во время защиты. Это те же файлы, что в репозитории",
    "[fms-course](https://github.com/mflkee/fms-course), но отрендерены в **200 dpi**",
    "и названы по-русски.",
    "",
    f"Листать одним файлом удобнее: `../ФМС — все диаграммы.pdf` — те же {total} диаграммы,",
    "по одной на страницу, с подписями.",
    "",
    "Пересборка (если в fms-course правились `.puml`): `_архив/скрипты/build_diagrams.py`.",
    "",
]

for group, headline in GROUPS.items():
    lines += [f"## {headline}", ""]
    for g, caption, path in items:
        if g == group:
            short = caption.split(" · ", 1)[1]
            link = f"{group}/{os.path.basename(path)}"
            lines.append(f"- [{short}](<{link}>)")
    lines.append("")

open(os.path.join(BASE, "README.md"), "w").write("\n".join(lines))
print("готово:", total, "диаграмм в", BASE)
