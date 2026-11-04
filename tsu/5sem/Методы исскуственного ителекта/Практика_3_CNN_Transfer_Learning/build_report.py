#!/usr/bin/env python
"""Собирает отчёт по практике №3 в Jupyter-ноутбук (и HTML) из артефактов прогонов.

Требования LMS (курс «Методы ИИ I», раздел «Основное»):
  * в заголовке — номер практического занятия и его название;
  * в заголовке — ФИО сдающего;
  * в тексте раскрыты шаги выполнения работы, приветствуются скриншоты с комментариями;
  * вывод/заключение — по желанию.

Ноутбук не переобучает модели (это заняло бы ~15 минут): он показывает уже полученные
артефакты (графики, метрики, confusion matrix) и сверяет цифры с `outputs/metrics.json`.
Полное воспроизведение — запуском скриптов `01_*` … `05_*` (см. последний раздел).

Запуск (из корня практики):
    ../.venv/bin/python build_report.py
"""

from __future__ import annotations

import json
from pathlib import Path

TASK = Path(__file__).resolve().parent
NOTEBOOK = TASK / "Отчёт_Практика_3.ipynb"

FIO = "Макеев Г. Б."
GROUP = "BHEBDPS-24-MII-5"
DISCIPLINE = "Методы искусственного интеллекта I"
TITLE = "Практика №3. Основы нейросетей и компьютерного зрения (CNN + Transfer Learning)"
DATE = "18 октября 2026"


def load(path: Path) -> dict:
    return json.loads((TASK / path).read_text(encoding="utf-8"))


_counter = {"n": 0}


def _cell_id() -> str:
    _counter["n"] += 1
    return f"cell-{_counter['n']:02d}"


def md(text: str) -> dict:
    return {"cell_type": "markdown", "id": _cell_id(), "metadata": {},
            "source": text.strip().splitlines(keepends=True)}


def code(text: str) -> dict:
    return {"cell_type": "code", "id": _cell_id(), "metadata": {}, "execution_count": None,
            "outputs": [], "source": text.strip().splitlines(keepends=True)}


#: первая ячейка ноутбука: читает артефакты прогонов и рисует «скриншоты»
SHOW_HELPER = """
# служебная ячейка: загружаем метрики реальных прогонов и учимся показывать «скриншоты»
import json
from pathlib import Path
from IPython.display import Image, display, Markdown

ROOT = Path.cwd()

def load(path):
    return json.loads((ROOT / path).read_text(encoding="utf-8"))

m1 = load("01_data_augmentation/outputs/metrics.json")   # данные и аугментации
m2 = load("02_model_adaptation/outputs/metrics.json")    # адаптация ResNet18
m3 = load("03_train_eval/outputs/metrics.json")          # обучение головы и оценка
m4 = load("04_fine_tuning_star/outputs/metrics.json")    # fine-tuning и «с нуля»
m5 = load("05_homework_neuron/outputs/metrics.json")     # ДЗ: нейрон с сигмоидой

def show(*paths, width=820):
    # показать «скриншот» из outputs/: подпись — относительный путь к файлу
    for path in paths:
        p = ROOT / path
        print(f"↓ {path}" if p.exists() else f"[НЕТ ФАЙЛА: {path}]")
        if p.exists():
            display(Image(filename=str(p), width=width))

print("артефакты прогонов загружены")
"""


def main() -> None:
    m1 = load("01_data_augmentation/outputs/metrics.json")
    m2 = load("02_model_adaptation/outputs/metrics.json")
    m3 = load("03_train_eval/outputs/metrics.json")
    m4 = load("04_fine_tuning_star/outputs/metrics.json")
    m5 = load("05_homework_neuron/outputs/metrics.json")

    cells: list[dict] = []

    # --- заголовок ----------------------------------------------------------
    cells.append(md(f"""
# {TITLE}

**Сдающий:** {FIO} &nbsp;·&nbsp; **Группа:** {GROUP}<br>
**Дисциплина:** {DISCIPLINE} (ТюмГУ)<br>
**Дата:** {DATE}<br>
**Инструменты:** Python 3.12, PyTorch 2.14 (CPU), torchvision, matplotlib<br>
**Датасет:** CIFAR-10 — 60 000 цветных картинок 32×32, 10 классов (<https://cave.cs.toronto.edu/kriz/cifar.html>)

---

## Что требовалось (чек-лист задания и где это в отчёте)

| Пункт задания | Где смотреть | Статус |
|---|---|---|
| Этап 1. Загрузка CIFAR-10, аугментации (`RandomHorizontalFlip`, `RandomCrop`, `ColorJitter`), нормализация | раздел 1 | ✅ |
| Этап 2. ResNet18 с весами ImageNet, заморозка свёрточных слоёв, замена `nn.Linear(512, 1000)` → `nn.Linear(512, 10)`, Adam только на новый слой | раздел 2 | ✅ |
| Этап 3. Цикл обучения (эпохи), `CrossEntropyLoss`, accuracy и confusion matrix на тесте, показ картинок с ошибками | раздел 3 | ✅ |
| :star: Разморозить последние слои ResNet18, дообучить сеть (fine-tuning), сравнить accuracy «до» и «после» | раздел 4 | ✅ |
| ДЗ лекции 3: нейрон с Sigmoid без PyTorch (сдаётся отдельным файлом) | раздел 5 | ✅ |

**Организация работы.** Задание разложено по папкам, в каждой — скрипт, графики и свой отчёт
`results.md`: `01_data_augmentation/`, `02_model_adaptation/`, `03_train_eval/`,
`04_fine_tuning_star/`, `05_homework_neuron/`, общий код — в `common/`. Этот ноутбук — сводный отчёт.

> Особенность окружения: GPU нет (Intel Iris Xe), поэтому всё обучение идёт на CPU.
> Из-за этого взята стратифицированная подвыборка обучения — 10 000 картинок (по 1000 на класс),
> а тест — полный (10 000 картинок). Один и тот же сид (`SEED = 42`) и один и тот же файл
> индексов подвыборки используются всеми этапами, поэтому сравнение моделей честное.

## Служебный код отчёта
"""))
    cells.append(code(SHOW_HELPER))
    cells.append(code("""
# сверка: цифры в тексте отчёта против metrics.json реальных прогонов
summary = {
    "01 subset train": f"{m1['subset_size']} картинок (по {m1['per_class']} на класс)",
    "02 всего параметров": m2["param_stats"]["total_human"],
    "02 обучаемых (голова fc)": f"{m2['param_stats']['trainable_human']} = "
                                f"{m2['param_stats']['trainable_share_pct']}% модели",
    "03 accuracy (только голова)": m3["test"]["accuracy"],
    "03 macro-F1": m3["test"]["macro_f1"],
    "03 accuracy при BN train (контроль)": [r["acc"] for r in m3["bn_compare"] if r["bn_mode"] == "train"][0],
    "04 accuracy после fine-tuning layer4+fc": m4["results"]["layer4"]["accuracy"],
    "04 accuracy обучение с нуля": m4["results"]["scratch"]["accuracy"],
    "05 расхождение градиентов (аналитика vs численно)": f"{m5['gradient_check']['max_abs_diff']:.2e}",
}
for k, v in summary.items():
    print(f"{k:<52} {v}")
"""))

    # --- раздел 1 -----------------------------------------------------------
    cells.append(md("""
---
# 1. Этап 1 — загрузка данных и аугментации

**Шаги:** скачиваем CIFAR-10, смотрим структуру (тензор 32×32×3, значения 0…255), делаем
стратифицированную подвыборку, задаём аугментации и нормализацию.

**Аугментации (искусственное расширение датасета):**

```python
transforms.Compose([
    transforms.RandomCrop(32, padding=4),                 # сдвиг ±4 px: объект может быть не в центре
    transforms.RandomHorizontalFlip(p=0.5),               # отражение: кошка слева = кошка справа
    transforms.ColorJitter(brightness=0.2, contrast=0.2, saturation=0.2),  # свет/цвет не должны мешать
    transforms.ToTensor(),                                # PIL 0…255 -> тензор 0…1, CHW
    transforms.Normalize(mean, std),                      # (x - mean) / std
])
```

Для теста — только `ToTensor` + `Normalize` (никаких случайностей, иначе метрики «плывут»).

**Нормализация:** привели пиксели к нулевому среднему и единичной дисперсии. Посчитали реальные
mean/std подвыборки и сравнили со стандартными константами CIFAR-10 — расхождение меньше 1 %,
поэтому используем константы.
"""))
    cells.append(code("""
stats = m1["normalization"]
print(f"посчитано на {stats['n_images']} картинках: mean={stats['mean']}, std={stats['std']}")
print("константы практики:             "
      "mean=[0.4914, 0.4822, 0.4465], std=[0.247, 0.2435, 0.2616]")
print(f"обучение: {m1['subset_size']} картинок, тест: {m1['test_full']} картинок")
show("01_data_augmentation/outputs/03_samples.png",
     "01_data_augmentation/outputs/04_augmentations.png")
"""))
    cells.append(md("""
**Комментарий к «скриншотам».** Первый рисунок — примеры из датасета: видно, что даже 32×32
достаточно, чтобы различать «птица / самолёт / кошка». Второй — одна и та же картинка после
восьми случайных применений аугментаций: объект сдвигается и меняет цвет, но остаётся тем же
классом. Именно это заставляет модель учить **объект**, а не конкретные пиксели.
"""))

    # --- раздел 2 -----------------------------------------------------------
    cells.append(md(f"""
---
# 2. Этап 2 — адаптация предобученной модели (Transfer Learning)

**Идея.** ResNet18 уже обучен на ImageNet (1.2 млн картинок, 1000 классов): его свёрточные слои
умеют находить края, текстуры и формы. Мы берём эти признаки и переучиваем только последний слой
под свои 10 классов.

```python
model = models.resnet18(weights=models.ResNet18_Weights.IMAGENET1K_V1)  # веса ImageNet
for p in model.parameters():
    p.requires_grad = False                       # заморозили свёрточные слои
model.fc = nn.Linear(512, 10)                     # было nn.Linear(512, 1000), стало под CIFAR-10
optimizer = torch.optim.Adam([p for p in model.parameters() if p.requires_grad], lr=1e-2)
```

| | Значение |
|---|---|
| Всего параметров | {m2['param_stats']['total_human']} |
| Обучaемых (голова `fc`) | {m2['param_stats']['trainable_human']} = **{m2['param_stats']['trainable_share_pct']}%** модели |
| Заморожено | {m2['param_stats']['frozen_params']:,} |

Обучаем 0.05 % весов — это и есть «стоять на плечах гигантов»: обучение занимает минуты на CPU
и не требует миллионов картинок.

**Как CNN «видит» картинку.** Ниже — фильтры первого слоя (детекторы краёв и цветовых переходов)
и карты активаций предобученной модели на разной глубине: от простых паттернов (`conv1`) к частям
объектов (`layer3`). Последний рисунок — те же слои со **случайными** весами: осмысленных признаков
нет. Это и есть разница между «обучено на ImageNet» и «чистый лист».
"""))
    cells.append(code("""
show("02_model_adaptation/outputs/01_conv1_filters.png", width=700)
show("02_model_adaptation/outputs/02_input_image.png", width=180)
show("02_model_adaptation/outputs/03_feature_maps_pretrained.png", width=780)
show("02_model_adaptation/outputs/04_feature_maps_random.png", width=780)
"""))
    cells.append(md("""
**Техническая проверка.** Прогнали тензор через блоки и вывели формы:
`input (2,3,32,32)` → `conv1 (2,64,16,16)` → `maxpool (2,64,8,8)` → `layer1 (2,64,8,8)` →
`layer2 (2,128,4,4)` → `layer3 (2,256,2,2)` → `layer4 (2,512,1,1)` → `flatten (2,512)` → `fc (2,10)`.

Отсюда важное инженерное наблюдение: стандартный ResNet18 рассчитан на 224×224 (первый слой —
свёртка 7×7 со `stride=2` плюс `MaxPool`), поэтому на картинке 32×32 после `layer4` остаётся карта
признаков **1×1** — голова видит «усреднённое» представление картинки. В `common/model.py`
предусмотрены варианты лечения (`stem="nopool"`, `stem="cifar"` за счёт свёртки 3×3 со `stride=1`);
в основном прогоне оставлен стандартный вариант из задания.
"""))

    # --- раздел 3 -----------------------------------------------------------
    bn_rows = "\n".join(
        f"| {r['bn_mode']} | {r['acc']:.4f} | {r['macro_f1'] if r['macro_f1'] is not None else '—'} |"
        for r in m3["bn_compare"])
    hist_rows = "\n".join(
        f"| {r['epoch']} | {r['lr']:.2e} | {r['train_loss']:.4f} | {r['train_acc']:.4f} | "
        f"{r['test_loss']:.4f} | {r['test_acc']:.4f} | {r['seconds']:.1f} с |"
        for r in m3["history"])
    class_rows = "\n".join(
        f"| {r['class']} | {r['precision']:.3f} | {r['recall']:.3f} | {r['f1']:.3f} | {r['support']} |"
        for r in m3["test"]["per_class"])
    best = max(m3["test"]["per_class"], key=lambda r: r["f1"])["class"]
    worst = min(m3["test"]["per_class"], key=lambda r: r["f1"])["class"]

    cells.append(md(f"""
---
# 3. Этап 3 — обучение и оценка

**Шаги:** обучаем голову 15 эпох Adam'ом (`lr = 1e-2` с косинусным затуханием), функция потерь —
`CrossEntropyLoss`; затем считаем accuracy на полном тесте (10 000 картинок), матрицу ошибок и
показываем картинки, на которых модель ошиблись.

**История обучения**

| Эпоха | lr | train loss | train acc | test loss | test acc | время |
|---|---|---|---|---|---|---|
{hist_rows}

**Итог на тесте:** **accuracy = {m3['test']['accuracy']:.4f}**, macro-F1 = {m3['test']['macro_f1']:.4f},
loss = {m3['test']['loss']:.4f}. Обучение заняло {m3['total_seconds'] / 60:.1f} мин на CPU
({m3['inference']['images_per_second']} картинок/с при инференсе).

**Метрики по классам**

| Класс | Precision | Recall | F1 | Support |
|---|---|---|---|---|
{class_rows}

Лучше всего распознаётся `{best}`, хуже всего — `{worst}`: в CIFAR-10 «кошка, собака, олень и птица»
похожи и на 32×32, и в этом основная часть ошибок.
"""))
    cells.append(code("""
show("03_train_eval/outputs/01_training_curves.png")
show("03_train_eval/outputs/02_confusion_matrix.png", width=700)
show("03_train_eval/outputs/03_misclassified.png")
"""))
    cells.append(md(f"""
**Комментарий к confusion matrix.** Матрица почти симметрична по «животным» (cat ↔ dog, deer ↔ bird)
и по «технике» (automobile ↔ truck, airplane ↔ ship) — модель путает именно визуально близкие классы,
а не случайные. Внизу — первые 12 ошибок с уверенностью модели: например, размытый олень
определяется как птица, лошадь — как кошка, пожарная машина — как «automobile».

## Эксперимент: режим BatchNorm замороженного бэкенда

Первый прогон давал accuracy ≈ 0.34 и **не рос**. Проверили гипотезу: слои `BatchNorm` внутри
замороженного бэкенда в режиме `train` пересчитывают статистики под CIFAR-10, а их `weight/bias`
остаются от ImageNet и «подстроиться» не могут — признаки «плывут», распределение входа головы
меняется на каждом шаге. Если держать BN в `eval`, признаки по-настоящему заморожены.

| Режим BatchNorm | Accuracy | Macro-F1 |
|---|---|---|
{bn_rows}

Разница в 1.5 раза при тех же данных, эпохах и learning rate. Контрольный запуск
(`--compare-bn`) сохранён в `03_train_eval/outputs/04_curves_bn_other_mode.png` и
`05_confusion_bn_other.png`.
"""))

    # --- раздел 4 -----------------------------------------------------------
    res = m4["results"]
    fin_rows = "\n".join(
        f"| {name} | {res[key]['accuracy']:.4f} | {res[key]['macro_f1']:.4f} | "
        f"{m4['deltas_vs_head'].get(key, '—')} |"
        for key, name in [("head", "Обучение только головы (этап 3)"),
                          ("layer4", "Fine-tuning: разморожены `layer4` + `fc`"),
                          ("scratch", "Обучение с нуля (случайные веса)")] if key in res)

    cells.append(md(f"""
---
# 4. :star: Задание со звёздочкой — Fine-tuning и сравнение accuracy

**Что сделано.** Модель этапа 3 (обучена только голова) взята как baseline. Затем «последние слои»
(`layer4` + `fc`, 8.4 млн параметров) разморожены и дообучены на CIFAR-10 маленьким learning rate
(`lr = 1e-4` с косинусным затуханием, 5 эпох) — так предобученные веса подстраиваются под задачу,
но не разрушаются. Для контроля обучен такой же ResNet18 **с нуля** (случайная инициализация).

| Вариант | Accuracy | Macro-F1 | Δ к baseline |
|---|---|---|---|
{fin_rows}

**Ответ на вопрос задания**: accuracy **до** fine-tuning = {res['head']['accuracy']:.4f},
**после** = {res['layer4']['accuracy']:.4f} → прирост **{m4['deltas_vs_head']['layer4']:+.4f}**.
Сравнение «с нуля» ({res['scratch']['accuracy']:.4f}) показывает, что передача обучения выигрывает у
обучения с нуля **{res['head']['accuracy'] - res['scratch']['accuracy']:+.4f}** — при том, что в первом
случае обучается в ~2000 раз меньше параметров.
"""))
    cells.append(code("""
show("04_fine_tuning_star/outputs/01_accuracy_compare.png")
show("04_fine_tuning_star/outputs/curves_layer4.png",
     "04_fine_tuning_star/outputs/curves_scratch.png")
show("04_fine_tuning_star/outputs/confusion_layer4.png", width=700)
"""))
    cells.append(md("""
**Вывод по этапу.** Схема «сначала быстро обучить голову, затем аккуратно разморозить последние
блоки» даёт лучший результат; обучение всей сети с нуля на 10 000 картинок за 5 эпох заметно
проигрывает — сети банально нечем «наполнить» 11 млн весов.
"""))

    # --- раздел 5 -----------------------------------------------------------
    gc = m5["gradient_check"]
    train5 = m5["training"]
    cells.append(md(f"""
---
# 5. ДЗ лекции 3 — один нейрон с Sigmoid на чистом Python (без PyTorch)

Это домашнее задание сдаётся отдельным файлом (`05_homework_neuron/Вопросы_лекции_3.md` —
ответы на вопросы про Трансформеры/RNN, `05_homework_neuron/main.py` — код). Здесь — краткий итог,
потому что задание напрямую связано с теорией нейросетей из лекции 3.

**Что реализовано (`math` + `random`, без PyTorch и numpy):** прямой проход `z = Σwᵢxᵢ + b`,
`a = σ(z)`; обратный проход по правилу цепочки `∂L/∂z = (a − y)·a·(1 − a)`, `∂L/∂wᵢ = ∂L/∂z · xᵢ`.

**Проверка корректности.** Аналитические градиенты сравнили с численной производной
(`(L(w+ε) − L(w−ε)) / 2ε`): максимальное расхождение **{gc['max_abs_diff']:.2e}** — уровень
машинной точности. Отдельная сверка с `torch.autograd` (`cross_check_torch.py`) даёт 2.8e-17.

**Обучение на логических функциях** (градиентный спуск по 4 примерам, 500 эпох, lr = 1.0):

| Функция | loss (начало → конец) | accuracy | выученные веса |
|---|---|---|---|
| AND | {train5['AND']['first_loss']:.4f} → {train5['AND']['final_loss']:.6f} | {train5['AND']['acc']:.2f} | w=({train5['AND']['w'][0]:+.2f}, {train5['AND']['w'][1]:+.2f}), b={train5['AND']['b']:+.2f} |
| OR | {train5['OR']['first_loss']:.4f} → {train5['OR']['final_loss']:.6f} | {train5['OR']['acc']:.2f} | w=({train5['OR']['w'][0]:+.2f}, {train5['OR']['w'][1]:+.2f}), b={train5['OR']['b']:+.2f} |
| XOR | {train5['XOR']['first_loss']:.4f} → {train5['XOR']['final_loss']:.6f} | {train5['XOR']['acc']:.2f} | w=({train5['XOR']['w'][0]:+.2f}, {train5['XOR']['w'][1]:+.2f}), b={train5['XOR']['b']:+.2f} |

AND и OR линейно разделимы и решаются одним нейроном идеально; XOR — нет: перебор по сетке
`(w1, w2, b)` даёт максимум accuracy **{m5['xor_upper_bound']['acc']:.2f}**, то есть одну прямую
нельзя «согнуть». Это ровно та причина, по которой понадобились скрытые слои (MLP), а затем — CNN.
"""))
    cells.append(code("""
show("05_homework_neuron/outputs/02_decision_boundary.png")
show("05_homework_neuron/outputs/01_training.png", "05_homework_neuron/outputs/03_gradient_check.png")
"""))

    # --- выводы -------------------------------------------------------------
    cells.append(md(f"""
---
# Выводы

1. **Transfer Learning работает на маленьких данных.** За 2.5 минуты обучения на CPU и 5 130
   обучаемых весов (0.05 % модели) получено accuracy **{m3['test']['accuracy']:.4f}** на полном тесте
   CIFAR-10; после fine-tuning последнего блока — **{res['layer4']['accuracy']:.4f}**. Обучение той же
   сети с нуля даёт лишь {res['scratch']['accuracy']:.4f}: предобученные признаки на ImageNet весят
   больше, чем 11 млн случайных весов.
2. **Аугментации + нормализация** расширяют датасет бесплатно: модель учит объект, а не конкретные
   пиксели (см. раздел 1).
3. **Ошибки модели содержательны**: путаются визуально близкие классы
   (cat ↔ dog, deer ↔ bird, automobile ↔ truck) — как и должно быть у модели, а не случайные ответы.
4. **Два инженерных грабля, найденных по ходу работы** (и измеренных, а не «на глаз»):
   * `BatchNorm` замороженного бэкенда обязан быть в режиме `eval` — иначе accuracy падает
     с {m3['test']['accuracy']:.3f} до {[r['acc'] for r in m3['bn_compare'] if r['bn_mode'] == 'train'][0]:.3f};
   * стандартный ResNet18 рассчитан на 224×224, на 32×32 он «схлопывает» карту признаков до 1×1
     (лечится заменой входа на 3×3/`stride=1` или удалением `MaxPool`).
5. **Пределы применимости** видны на ДЗ: один нейрон с сигмоидой рисует **одну прямую** и не решает
   XOR — нелинейность и глубина нужны с самого начала, а CNN добавляет к этому учёт
   пространственной структуры изображения.

## Как воспроизвести

```bash
cd "Методы исскуственного ителекта/Практика_3_CNN_Transfer_Learning"
../.venv/bin/python 01_data_augmentation/main.py       # данные и аугментации (~30 с)
../.venv/bin/python 02_model_adaptation/main.py        # адаптация ResNet18 (~40 с)
../.venv/bin/python 03_train_eval/main.py --compare-bn  # обучение головы (~5.5 мин)
../.venv/bin/python 04_fine_tuning_star/main.py        # fine-tuning + «с нуля» (~11 мин)
../.venv/bin/python 05_homework_neuron/main.py         # ДЗ с лекции 3 (без PyTorch, ~20 с)
../.venv/bin/python build_report.py                    # пересобрать этот отчёт
```

Структура каталогов, все графики и подробные логи — в `README.md` и `results.md` каждой папки.
"""))

    notebook = {
        "cells": cells,
        "metadata": {
            "kernelspec": {"display_name": "Python 3", "language": "python", "name": "python3"},
            "language_info": {"name": "python", "version": "3.12"},
        },
        "nbformat": 4,
        "nbformat_minor": 5,
    }
    NOTEBOOK.write_text(json.dumps(notebook, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"Ноутбук собран: {NOTEBOOK} ({len(cells)} ячеек)")
    print("Дальше: jupyter nbconvert --to notebook --execute --inplace "
          f'"{NOTEBOOK.name}" && jupyter nbconvert --to html "{NOTEBOOK.name}"')


if __name__ == "__main__":
    main()
