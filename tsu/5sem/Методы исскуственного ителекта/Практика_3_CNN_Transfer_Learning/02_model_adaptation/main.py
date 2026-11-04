#!/usr/bin/env python
"""Практика №3, этап 2: адаптация предобученного ResNet18 под CIFAR-10.

Запуск из корня практики:
    ../.venv/bin/python 02_model_adaptation/main.py
"""

from __future__ import annotations

import sys
from pathlib import Path

TASK_DIR = Path(__file__).resolve().parent
PRACTICE_ROOT = TASK_DIR.parent
sys.path.insert(0, str(PRACTICE_ROOT))

import numpy as np  # noqa: E402
import torch  # noqa: E402

from common import viz  # noqa: E402
from common.data import CIFAR10_CLASSES, build_datasets  # noqa: E402
from common.model import build_resnet18, describe_forward, feature_maps, module_table, param_stats  # noqa: E402
from common.utils import device_info, get_device, header, human_params, save_json, set_seed  # noqa: E402

OUTPUTS = TASK_DIR / "outputs"
SUBSET_INDEX_FILE = PRACTICE_ROOT / "01_data_augmentation" / "outputs" / "subset_indices.json"


def md_table(rows: list[dict], columns: list[str]) -> str:
    head = "| " + " | ".join(columns) + " |\n|" + "|".join(["---"] * len(columns)) + "|\n"
    body = ""
    for row in rows:
        cells = []
        for col in columns:
            value = row[col]
            if isinstance(value, bool):
                value = "✅ да" if value else "❌ нет"
            cells.append(str(value))
        body += "| " + " | ".join(cells) + " |\n"
    return head + body


def main() -> None:
    set_seed()
    header("Этап 2. Адаптация предобученного ResNet18 (Transfer Learning)")
    OUTPUTS.mkdir(parents=True, exist_ok=True)
    device = get_device()
    print(f"Устройство: {device_info()}")

    # --- 1. Модель: ImageNet-веса + заморозка + новая голова ------------------
    print("\n[1] Загружаем ResNet18 с весами ImageNet (1000 классов)")
    model = build_resnet18(pretrained=True, freeze_backbone=True).to(device)
    stats = param_stats(model)
    print(f"    всего параметров:          {stats['total_human']} ({stats['total_params']:,})")
    print(f"    обучаемых (только fc):     {stats['trainable_human']} ({stats['trainable_params']:,}) "
          f"= {stats['trainable_share_pct']}%")
    print(f"    заморожено:                {human_params(int(stats['frozen_params']))}")
    print(f"    новая голова: model.fc = nn.Linear({model.fc.in_features}, {model.fc.out_features})")
    print(f"    было (ImageNet):           nn.Linear(512, 1000)")

    # --- 2. Проверка прямого прохода на реальных данных ----------------------
    print("\n[2] Проверка прямого прохода на 8 картинках CIFAR-10")
    train_ds, test_ds, _ = build_datasets(per_class=1000, index_file=SUBSET_INDEX_FILE)
    images = torch.stack([test_ds[i][0] for i in range(8)]).to(device)
    labels = torch.tensor([test_ds[i][1] for i in range(8)])
    with torch.no_grad():
        model.eval()
        logits = model(images)
        probs = torch.softmax(logits, dim=1)
    print(f"    вход: {tuple(images.shape)} -> выход: {tuple(logits.shape)}")
    print(f"    сумма вероятностей по классам: {probs.sum(dim=1).cpu().numpy().round(4)}")
    print(f"    (форма верная: 8 картинок x 10 классов CIFAR-10)")
    print("    ВНИМАНИЕ: это ещё НЕобученная голова — предсказания случайные.")

    # --- 3. Как ResNet18 «проходит» картинку 32x32 ---------------------------
    print("\n[3] Формы тензоров по блокам (вход 32x32, как в CIFAR-10)")
    shapes = describe_forward(model)
    for row in shapes:
        print(f"    {row['block']:<10} {str(row['shape']):<22}")

    # --- 4. Что «видит» сеть -------------------------------------------------
    print("\n[4] Визуализация: фильтры conv1 и карты активаций")
    viz.plot_filters(model.conv1.weight, OUTPUTS / "01_conv1_filters.png")

    demo_idx = [i for i in range(len(test_ds)) if test_ds[i][1] == 7][0]  # класс "horse"
    demo = test_ds[demo_idx][0].unsqueeze(0).to(device)
    viz.plot_image_grid([demo[0]], [f"вход: {CIFAR10_CLASSES[test_ds[demo_idx][1]]}"],
                        OUTPUTS / "02_input_image.png", ncols=1, figsize_scale=3)

    acts_pretrained = feature_maps(model, demo)
    viz.plot_feature_maps(acts_pretrained, OUTPUTS / "03_feature_maps_pretrained.png",
                          title="Активации ImageNet-модели: от краёв (conv1) к объектам (layer3)")

    random_model = build_resnet18(pretrained=False, freeze_backbone=False).to(device)
    acts_random = feature_maps(random_model, demo)
    viz.plot_feature_maps(acts_random, OUTPUTS / "04_feature_maps_random.png",
                          title="Те же слои со СЛУЧАЙНЫМИ весами: осмысленных признаков нет")

    # --- 5. Отчёт ------------------------------------------------------------
    rows = module_table(model)
    for row in rows:
        print(f"    {row['block']:<10} {row['params_human']:>10}  обучается: {row['trainable']}")

    shape_rows = "\n".join(f"| `{r['block']}` | `{r['shape']}` |" for r in shapes)
    report = f"""# Этап 2. Адаптация предобученного ResNet18

## Идея Transfer Learning

ResNet18 обучен на **ImageNet** (1.2 млн картинок, 1000 классов). Его свёрточные
слои уже умеют находить края, текстуры и формы — это универсальные признаки,
полезные и для CIFAR-10. Поэтому мы:

1. загружаем веса ImageNet (`ResNet18_Weights.IMAGENET1K_V1`);
2. **замораживаем** все свёрточные слои (`requires_grad = False`);
3. заменяем классификатор: `nn.Linear(512, 1000)` -> `nn.Linear(512, 10)`;
4. обучаем **только** новый слой — 5 130 параметров вместо 11 млн.

## Параметры модели

| | Значение |
|---|---|
| Всего параметров | {stats['total_human']} ({stats['total_params']:,}) |
| Обучаемых (голова `fc`) | {stats['trainable_human']} ({stats['trainable_params']:,}) = **{stats['trainable_share_pct']}%** |
| Заморожено | {human_params(int(stats['frozen_params']))} |
| Устройство | {device_info()} |

Обучение 0.05% весов занимает минуты на CPU и не требует миллиона картинок —
это и есть «стоять на плечах гигантов».

## Структура модели и обучаемость

{md_table(rows, ['block', 'type', 'params_human', 'trainable'])}

## Формы тензоров по блокам (вход 32x32)

| Блок | Выход |
|------|-------|
{shape_rows}

**Важная инженерная деталь.** Стандартный ResNet18 спроектирован под ImageNet
(224x224): первый слой — свёртка 7x7 со `stride=2` плюс `MaxPool`, то есть
размер картинки уменьшается сразу в 4 раза. Для 32x32 это значит, что уже после
`layer4` остаётся карта **1x1** — сеть видит изображение «в целом», теряя
пространственную детализацию. Варианты на будущее (не требуются заданием):
первый слой 3x3 со `stride=1` без `MaxPool` или `replace_stride_with_dilation`.
Для учебной задачи transfer learning мы оставляем «как в torchvision» — так
честнее видно, что даже огрублённые ImageNet-признаки дают хороший результат.

## Графики

| Файл | Что показывает |
|------|----------------|
| `outputs/01_conv1_filters.png` | 16 фильтров 7x7x3 первого слоя — детекторы краёв/градиентов/цветов |
| `outputs/02_input_image.png` | входная картинка 32x32 |
| `outputs/03_feature_maps_pretrained.png` | активации ImageNet-модели: от простых паттернов к сложным |
| `outputs/04_feature_maps_random.png` | те же слои со случайными весами — «шум», осмысленных признаков нет |

Иерархия признаков: `conv1` (16x16) — края и цветовые переходы, `layer1`
(8x8) — текстуры, `layer2` (4x4) и `layer3` (2x2) — части объектов. Сравнение с
`outputs/04_feature_maps_random.png` наглядно показывает, что **предобученный**
фильтр уже является осмысленным детектором, а случайный — нет: именно поэтому
fine-tuning c нуля требует миллионов картинок.

## Вывод этапа

Готовая модель: замороженный ImageNet-бэкенд + обучаемая голова `Linear(512, 10)`.
Обучаем только 0.05% параметров — это ускоряет обучение на порядки и снижает
риск переобучения на подвыборке {len(train_ds)} картинок. Обучение — этап 3.
"""
    (TASK_DIR / "results.md").write_text(report, encoding="utf-8")
    save_json({
        "param_stats": stats,
        "modules": rows,
        "forward_shapes": [{"block": r["block"], "shape": list(r["shape"])} for r in shapes],
        "fc": {"in_features": model.fc.in_features, "out_features": model.fc.out_features},
        "backbone_pretrained": True,
        "backbone_frozen": True,
        "demo_image": {"index": int(demo_idx), "class": CIFAR10_CLASSES[test_ds[demo_idx][1]]},
    }, OUTPUTS / "metrics.json")
    print(f"\nОтчёт: {TASK_DIR / 'results.md'}")


if __name__ == "__main__":
    main()
