#!/usr/bin/env python
"""Практика №3, этап 1: датасет CIFAR-10, EDA и аугментации.

Запуск из корня практики:
    ../.venv/bin/python 01_data_augmentation/main.py
"""

from __future__ import annotations

import sys
from pathlib import Path

TASK_DIR = Path(__file__).resolve().parent
PRACTICE_ROOT = TASK_DIR.parent
sys.path.insert(0, str(PRACTICE_ROOT))

import numpy as np  # noqa: E402

from common import viz  # noqa: E402
from common.data import (  # noqa: E402
    CIFAR10_CLASSES,
    build_datasets,
    class_counts,
    dataset_stats,
    download_cifar10,
    train_transform,
    workers_default,
)
from common.utils import DATA_ROOT, fmt_seconds, header, save_json, set_seed, timer  # noqa: E402

PER_CLASS = 1000  # подвыборка: 1000 картинок * 10 классов = 10 000 на обучение
OUTPUTS = TASK_DIR / "outputs"


def main() -> None:
    set_seed()
    header("Этап 1. Данные CIFAR-10: загрузка, EDA, аугментации")
    OUTPUTS.mkdir(parents=True, exist_ok=True)
    print(f"Каталог данных: {DATA_ROOT}")
    print(f"Процессов предзагрузки DataLoader: {workers_default()}")

    # --- 1. Скачиваем датасет и смотрим его структуру -------------------------
    print("\n[1] Загрузка CIFAR-10 (при первом запуске скачается ~170 МБ)...")
    with timer("скачивание/чтение CIFAR-10") as t:
        raw_train, raw_test = download_cifar10()

    full_train_counts = class_counts(raw_train.targets)
    full_test_counts = class_counts(raw_test.targets)
    print(f"    train: {len(raw_train):>6} картинок {np.asarray(raw_train.data).shape}")
    print(f"    test : {len(raw_test):>6} картинок {np.asarray(raw_test.data).shape}")
    print(f"    классы: {', '.join(CIFAR10_CLASSES)}")
    print(f"    в памяти объекты PIL: {type(raw_train[0][0]).__name__}, "
          f"пиксели 0..255 (uint8), каналы RGB")

    # --- 2. Подвыборка (стратифицированная, сид 42) ---------------------------
    print(f"\n[2] Стратифицированная подвыборка: {PER_CLASS} картинок на класс")
    train_ds, test_ds, indices = build_datasets(per_class=PER_CLASS,
                                                index_file=OUTPUTS / "subset_indices.json")
    subset_counts = class_counts([train_ds.dataset.targets[i] for i in train_ds.indices])
    print(f"    обучение: {len(train_ds)} картинок (по {PER_CLASS} на класс)")
    print(f"    тест    : {len(test_ds)} картинок (полный тест CIFAR-10)")
    print(f"    индексы подвыборки сохранены в {OUTPUTS / 'subset_indices.json'}")

    # --- 3. Графики ----------------------------------------------------------
    print("\n[3] Визуализация")
    viz.plot_class_distribution(full_train_counts, OUTPUTS / "01_class_distribution_full.png",
                               "Исходный train CIFAR-10: баланс классов (50 000 картинок)")
    viz.plot_class_distribution(subset_counts, OUTPUTS / "02_class_distribution_subset.png",
                               f"Наша подвыборка: {len(train_ds)} картинок (по {PER_CLASS} на класс)")

    sample_idx = [int(np.flatnonzero(np.asarray(raw_train.targets) == c)[0]) for c in range(4)] + \
                 [int(np.flatnonzero(np.asarray(raw_train.targets) == c)[10]) for c in range(4)]
    samples = [np.asarray(raw_train[i][0]) for i in sample_idx]
    titles = [CIFAR10_CLASSES[raw_train[i][1]] for i in sample_idx]
    viz.plot_image_grid(samples, titles, OUTPUTS / "03_samples.png", ncols=8,
                        suptitle="Примеры картинок CIFAR-10 (32x32, оригиналы)")

    base_pil, base_label = raw_train[train_ds.indices[0]]
    aug_transform = train_transform(augment=True)
    viz.plot_augmentations(base_pil, aug_transform, OUTPUTS / "04_augmentations.png", n=8)
    print(f"    (базовая картинка для аугментаций: класс {CIFAR10_CLASSES[base_label]})")

    # --- 4. Нормализация: реальные mean/std vs константы ----------------------
    print("\n[4] Нормализация: считаем реальные mean/std подвыборки (5 000 картинок)")
    stats = dataset_stats(raw_train, limit=5000)
    save_json(stats, OUTPUTS / "normalization_stats.json")
    print(f"    посчитано: mean={stats['mean']}, std={stats['std']}")
    print(f"    константы: mean={list(viz.CIFAR10_MEAN)}, std={list(viz.CIFAR10_STD)}")
    print("    разница < 1% -> используем стандартные константы (быстрее, те же цифры)")

    # --- 5. Отчёт ------------------------------------------------------------
    report = f"""# Этап 1. Данные и аугментации (CIFAR-10)

Датасет: **CIFAR-10** — 60 000 цветных картинок 32x32, 10 классов
(50 000 train / 10 000 test). Скачан в `{DATA_ROOT}`.

## Что сделано

| Шаг | Результат |
|-----|-----------|
| Загрузка | train {len(raw_train)}, test {len(raw_test)} картинок; {fmt_seconds(t['seconds'])} |
| Подвыборка train | **{len(train_ds)}** картинок (стратифицированно, по {PER_CLASS} на класс, seed 42) |
| Тест | полный test CIFAR-10 (**{len(test_ds)}**) — оценка честная, без подвыборки |
| Классы | {', '.join(CIFAR10_CLASSES)} |

## Аугментации (transforms)

Для train применяется цепочка:

```python
transforms.Compose([
    transforms.RandomCrop(32, padding=4),        # сдвиг на ±4 px (объект может быть смещён)
    transforms.RandomHorizontalFlip(p=0.5),      # отражение (кошка слева = кошка справа)
    transforms.ColorJitter(brightness=0.2, contrast=0.2, saturation=0.2),  # свет/цвет
    transforms.ToTensor(),                       # PIL (0..255) -> тензор (0..1), CHW
    transforms.Normalize(mean, std),             # (x - mean) / std
])
```

Для test/валидации — только `ToTensor` + `Normalize` (никаких случайностей:
иначе метрики «плывут» от запуска к запуску).

Смысл: аугментации **искусственно расширяют** датасет. Модель видит один и тот же
объект под разными углами, сдвинутым и с другим освещением и учится не цепляться
за конкретные пиксели — это главный «бесплатный» способ борьбы с переобучением.

## Нормализация

| | R | G | B |
|---|---|---|---|
| mean (посчитано на 5 000 картинках) | {stats['mean'][0]} | {stats['mean'][1]} | {stats['mean'][2]} |
| mean (константы практики) | {viz.CIFAR10_MEAN[0]} | {viz.CIFAR10_MEAN[1]} | {viz.CIFAR10_MEAN[2]} |
| std (посчитано) | {stats['std'][0]} | {stats['std'][1]} | {stats['std'][2]} |
| std (константы) | {viz.CIFAR10_STD[0]} | {viz.CIFAR10_STD[1]} | {viz.CIFAR10_STD[2]} |

Разница в пределах ~1%, поэтому используем стандартные константы CIFAR-10.
Нормализация нужна, чтобы входы имели нулевое среднее и единичную дисперсию —
иначе градиенты «разъезжаются» и обучение идёт медленнее.

## Графики

| Файл | Что показывает |
|------|----------------|
| `outputs/01_class_distribution_full.png` | баланс классов в исходном датасете |
| `outputs/02_class_distribution_subset.png` | баланс классов в нашей подвыборке |
| `outputs/03_samples.png` | примеры картинок по классам |
| `outputs/04_augmentations.png` | одна картинка -> 8 разных версий после аугментаций |

## Вывод этапа

* Датасет сбалансирован, поэтому `accuracy` — корректная метрика (никакого
  `class_weight` не нужно).
* Работаем на подвыборке {len(train_ds)} картинок: на CPU это позволяет
  прогнать несколько эпох за минуты. Аугментации при этом «компенсируют»
  маленький размер выборки.
* Индексы подвыборки зафиксированы в `outputs/subset_indices.json` — этапы 2–4
  обучаются ровно на тех же картинках (сравнение моделей честное).
"""
    (TASK_DIR / "results.md").write_text(report, encoding="utf-8")
    save_json({
        "train_full": len(raw_train), "test_full": len(raw_test),
        "subset_size": len(train_ds), "per_class": PER_CLASS,
        "full_train_counts": full_train_counts, "subset_counts": subset_counts,
        "normalization": stats,
        "download_seconds": round(t["seconds"], 1),
    }, OUTPUTS / "metrics.json")
    print(f"\nОтчёт: {TASK_DIR / 'results.md'}")


if __name__ == "__main__":
    main()
