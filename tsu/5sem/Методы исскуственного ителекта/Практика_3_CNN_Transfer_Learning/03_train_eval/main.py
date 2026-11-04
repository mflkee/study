#!/usr/bin/env python
"""Практика №3, этап 3: обучение головы и оценка на тесте (+ confusion matrix).

Запуск из корня практики:
    ../.venv/bin/python 03_train_eval/main.py                    # основной прогон
    ../.venv/bin/python 03_train_eval/main.py --compare-bn       # + сравнение режимов BatchNorm
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

TASK_DIR = Path(__file__).resolve().parent
PRACTICE_ROOT = TASK_DIR.parent
sys.path.insert(0, str(PRACTICE_ROOT))

import numpy as np  # noqa: E402
import torch  # noqa: E402
import torch.nn as nn  # noqa: E402

from common import viz  # noqa: E402
from common.data import CIFAR10_CLASSES, build_datasets, build_loaders  # noqa: E402
from common.engine import benchmark_inference, classification_report, evaluate, fit, save_model  # noqa: E402
from common.model import build_resnet18, param_stats  # noqa: E402
from common.utils import (device_info, fmt_seconds, get_device, header, save_json,  # noqa: E402
                          set_seed, timer)

OUTPUTS = TASK_DIR / "outputs"
SUBSET_INDEX_FILE = PRACTICE_ROOT / "01_data_augmentation" / "outputs" / "subset_indices.json"


def index_file_for(per_class: int, task_outputs: Path) -> Path:
    """Файл с индексами подвыборки: 1000 на класс — общий (из этапа 1), иначе свой."""
    if per_class == 1000:
        return SUBSET_INDEX_FILE
    return task_outputs / f"subset_indices_{per_class}.json"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Этап 3: обучение головы ResNet18")
    parser.add_argument("--epochs", type=int, default=15, help="число эпох (по умолчанию 15)")
    parser.add_argument("--per-class", type=int, default=1000,
                        help="сколько картинок каждого класса брать в обучение (по умолчанию 1000 -> 10 000)")
    parser.add_argument("--lr", type=float, default=1e-2, help="learning rate для Adam")
    parser.add_argument("--scheduler", default="cosine", choices=["cosine", "none"],
                        help="затухание learning rate")
    parser.add_argument("--bn-mode", default="frozen", choices=["frozen", "train"],
                        help="BatchNorm замороженного бэкенда: eval (по умолчанию) или train")
    parser.add_argument("--compare-bn", action="store_true",
                        help="дополнительно обучить голову с BN в train-режиме и сравнить")
    parser.add_argument("--batch-size", type=int, default=128)
    parser.add_argument("--workers", type=int, default=None)
    return parser.parse_args()


def train_head(bn_frozen: bool, args, train_loader, test_loader, device, tag: str):
    """Обучает только голову (бэкенд заморожен) и возвращает модель + историю + время."""
    set_seed()
    model = build_resnet18(pretrained=True, freeze_backbone=True).to(device)
    with timer(f"обучение {tag} ({args.epochs} эпох)") as t:
        history = fit(model, train_loader, test_loader, epochs=args.epochs, lr=args.lr,
                      device=device, tag=tag, bn_frozen=bn_frozen,
                      scheduler=None if args.scheduler == "none" else args.scheduler)
    return model, history, t["seconds"]


def main() -> None:
    args = parse_args()
    set_seed()
    header("Этап 3. Обучение головы и оценка качества")
    OUTPUTS.mkdir(parents=True, exist_ok=True)
    device = get_device()
    print(f"Устройство: {device_info()}")

    index_file = index_file_for(args.per_class, OUTPUTS)
    train_ds, test_ds, _ = build_datasets(per_class=args.per_class, index_file=index_file)
    train_loader, test_loader = build_loaders(train_ds, test_ds,
                                              batch_size=args.batch_size,
                                              workers=args.workers)
    print(f"Обучение: {len(train_ds)} картинок ({args.per_class} на класс); "
          f"тест: {len(test_ds)} картинок; батч: {args.batch_size}")

    # --- Основной прогон: обучается только голова ---------------------------
    model, history, total_seconds = train_head(args.bn_mode == "frozen", args,
                                               train_loader, test_loader, device, "transfer")
    stats = param_stats(model)

    # --- Оценка на полном тесте ---------------------------------------------
    print("\n[Оценка] полный тест CIFAR-10 (10 000 картинок)")
    criterion = nn.CrossEntropyLoss()
    with timer("оценка на тесте") as t_eval:
        result = evaluate(model, test_loader, criterion, device, with_predictions=True)
    y_true = result["y_true"]
    y_pred = result["y_pred"]
    probs = result["probs"]
    report = classification_report(y_true, y_pred)
    print(f"    accuracy = {result['acc']:.4f}, loss = {result['loss']:.4f}, "
          f"macro-F1 = {report['macro_f1']:.4f}")
    print("    по классам:")
    print(f"    {'класс':<12}{'precision':>10}{'recall':>9}{'f1':>8}{'support':>9}")
    for row in report["per_class"]:
        print(f"    {row['class']:<12}{row['precision']:>10.3f}{row['recall']:>9.3f}"
              f"{row['f1']:>8.3f}{row['support']:>9}")

    # --- Визуализация -------------------------------------------------------
    print("\n[Визуализация]")
    wrong_idx = np.flatnonzero(y_true != y_pred)[:12]
    viz.plot_training_curves(history, OUTPUTS / "01_training_curves.png",
                             f"Transfer Learning: {args.epochs} эпох, обучается только голова fc")
    viz.plot_confusion_matrix(np.array(report["confusion_matrix"]), OUTPUTS / "02_confusion_matrix.png",
                              f"Confusion Matrix, accuracy = {result['acc']:.3f}")
    viz.plot_misclassified(torch.stack([test_ds[int(i)][0] for i in wrong_idx]),
                           y_true[wrong_idx], y_pred[wrong_idx], probs[wrong_idx],
                           OUTPUTS / "03_misclassified.png", n=len(wrong_idx))

    exam = benchmark_inference(model, test_loader, device)
    print(f"    скорость инференса: {exam['images_per_second']} картинок/с")

    save_model(model, OUTPUTS / "model_head_only.pt",
               meta={"epochs": args.epochs, "lr": args.lr, "accuracy": float(result["acc"]),
                     "stage": "frozen_backbone", "bn_mode": args.bn_mode,
                     "scheduler": args.scheduler, "subset": len(train_ds)})

    # --- Эксперимент: BatchNorm в train-режиме ------------------------------
    bn_compare = [{"bn_mode": args.bn_mode, "acc": float(result["acc"]),
                   "macro_f1": float(report["macro_f1"]), "seconds": round(total_seconds, 1)}]
    if args.compare_bn:
        other = "train" if args.bn_mode == "frozen" else "frozen"
        print(f"\n[Эксперимент] то же обучение, но BatchNorm в режиме '{other}'")
        model_other, history_other, seconds_other = train_head(other == "frozen", args,
                                                               train_loader, test_loader, device,
                                                               f"bn-{other}")
        result_other = evaluate(model_other, test_loader, criterion, device, with_predictions=True)
        report_other = classification_report(result_other["y_true"], result_other["y_pred"])
        bn_compare.append({"bn_mode": other, "acc": float(result_other["acc"]),
                           "macro_f1": float(report_other["macro_f1"]),
                           "seconds": round(seconds_other, 1)})
        print(f"    BN '{other}': accuracy = {result_other['acc']:.4f} "
              f"(BN '{args.bn_mode}': {result['acc']:.4f})")
        viz.plot_training_curves(history_other, OUTPUTS / "04_curves_bn_other_mode.png",
                                 f"Для сравнения: BN в режиме '{other}'")
        viz.plot_confusion_matrix(np.array(report_other["confusion_matrix"]),
                                  OUTPUTS / "05_confusion_bn_other.png",
                                  f"Confusion Matrix при BN '{other}' (accuracy = {result_other['acc']:.3f})")

    # --- Отчёт ---------------------------------------------------------------
    history_rows = "\n".join(
        f"| {r['epoch']} | {r['lr']:.2e} | {r['train_loss']:.4f} | {r['train_acc']:.4f} | "
        f"{r['test_loss']:.4f} | {r['test_acc']:.4f} | {fmt_seconds(r['seconds'])} |"
        for r in history)
    class_rows = "\n".join(
        f"| {r['class']} | {r['precision']:.3f} | {r['recall']:.3f} | {r['f1']:.3f} | {r['support']} |"
        for r in report["per_class"])

    lowest = sorted(report["per_class"], key=lambda r: r["f1"])[:2]
    highest = sorted(report["per_class"], key=lambda r: r["f1"], reverse=True)[:2]
    worst_text = " и ".join(f"**{r['class']}** (F1={r['f1']:.3f})" for r in lowest)
    best_text = ", ".join(f"`{r['class']}` (F1={r['f1']:.3f})" for r in highest)

    bn_rows = ""
    bn_section = ""
    if len(bn_compare) > 1:
        bn_rows = "\n".join(f"| {r['bn_mode']} | {r['acc']:.4f} | "
                            f"{r['macro_f1']:.4f} | {r['seconds']} с |"
                            for r in bn_compare)
        best_bn = max(bn_compare, key=lambda r: r["acc"])
        bn_section = f"""
## Эксперимент: почему BatchNorm замороженного бэкенда нельзя оставлять в режиме train

Первый прогон дал accuracy ≈ 0.34 и **не рос** от эпохи к эпохе — при этом отдельный
эталонный линейный пробник (`sklearn.LogisticRegression` на тех же 512-мерных
замороженных признаках) показывал ≈ 0.62. Значит, дело не в признаках, а в том, как
мы обучаем голову. Причина — слои `BatchNorm` внутри заморожённого бэкенда:

* в режиме `train` BN нормирует по статистике **батча** и обновляет `running_mean/var`
  под CIFAR, а «выучить» под это `weight/bias` слой не может (они заморожены) →
  признаки «плывут», распределение входа головы меняется на каждом шаге;
* в режиме `eval` BN использует статистику ImageNet, для которой веса и обучались →
  признаки заморожены по-настоящему, и голова сходится быстро.

Измерено (одинаковые данные, эпохи и lr; меняется только режим BN):

| Режим BatchNorm | Accuracy | Macro-F1 | Время |
|---|---|---|---|
{bn_rows}

Лучший: **BN в режиме `{best_bn['bn_mode']}`** (именно эта конфигурация сохранена в
`outputs/model_head_only.pt` и передана на этап 4). В коде это `--bn-mode` и параметр
`bn_frozen` в `common/engine.fit`; кривые «плохого» варианта —
`outputs/04_curves_bn_other_mode.png`, его confusion matrix — `outputs/05_confusion_bn_other.png`.
"""
    else:
        bn_section = """
## Замечание про BatchNorm (важно для воспроизведения)

Обучение головы идёт при **замороженных слоях BatchNorm предобученного бэкенда**
(`--bn-mode frozen`, `bn_frozen=True` в `common/engine.fit`). Если оставить BN в
режиме `train`, он начнёт пересчитывать статистики под CIFAR-10, а замороженные
`weight/bias` под них не подстроятся — признаки «плывут» и accuracy падает до ≈0.34
(проверяется флагом `--compare-bn`).
"""

    report_md = f"""# Этап 3. Обучение и оценка

## Настройки

| Параметр | Значение |
|---|---|
| Бэкенд | ResNet18, веса ImageNet, **заморожен** (BN в режиме `{args.bn_mode}`) |
| Обучается | только голова `Linear(512, 10)` = {stats['trainable_human']} параметров ({stats['trainable_share_pct']}%) |
| Данные | {len(train_ds)} картинок (по {args.per_class} на класс) + аугментации |
| Тест | {len(test_ds)} картинок (полный тест CIFAR-10) |
| Оптимизатор | Adam, lr = {args.lr}, затухание: {args.scheduler} |
| Функция потерь | CrossEntropyLoss |
| Эпох | {args.epochs} |
| Батч | {args.batch_size} |
| Время обучения | {fmt_seconds(total_seconds)} |
| Устройство | {device_info()} |

## История обучения

| Эпоха | lr | train loss | train acc | test loss | test acc | время |
|---|---|---|---|---|---|---|
{history_rows}

## Итоговые метрики на тесте

* **Accuracy = {result['acc']:.4f}**
* Loss = {result['loss']:.4f}
* Macro-F1 = {report['macro_f1']:.4f}
* Скорость инференса: **{exam['images_per_second']} картинок/с** (CPU, батч, без оптимизаций)

## Метрики по классам

| Класс | Precision | Recall | F1 | Support |
|---|---|---|---|---|
{class_rows}

Лучше всего распознаются {best_text} — у них выразительный силуэт.
Хуже всего — {worst_text}: эти классы визуально похожи и в ImageNet («кошка или собака?»),
и в CIFAR-10 они же дают основную часть ошибок (см. confusion matrix).
{bn_section}
## Графики

| Файл | Что показывает |
|------|----------------|
| `outputs/01_training_curves.png` | loss/accuracy по эпохам: train vs test |
| `outputs/02_confusion_matrix.png` | какие классы модель путает |
| `outputs/03_misclassified.png` | картинки, на которых модель ошиблась (с уверенностью) |

## Вывод этапа

За {args.epochs} эпох на {len(train_ds)} картинках и **без обучения свёрточных слоёв**
получено accuracy **{result['acc']:.4f}** (macro-F1 = {report['macro_f1']:.4f}). Обучение
заняло {fmt_seconds(total_seconds)} на CPU — это иллюстрация слайда 6: тяжёлую часть
(извлечение признаков) обучил кто-то другой на 1.2 млн картинок, а мы подобрали всего
{stats['trainable_human']} весов ({stats['trainable_share_pct']}% модели) под свои 10 классов.

### Инженерные заметки для отчёта

1. **BatchNorm замороженного бэкенда** держим в `eval` (см. раздел выше) — иначе
   точность падает в ~1.8 раза при тех же данных и времени.
2. **ResNet18 рассчитан на 224×224, а у нас 32×32.** Первый слой — свёртка 7×7 со
   `stride=2` плюс `MaxPool`, поэтому после `layer4` остаётся карта признаков **1×1**
   (этап 2, таблица форм тензоров): голова видит «усреднённое» представление картинки.
   Типовое лечение — заменить вход на свёртку 3×3 со `stride=1` без `MaxPool`
   (в `common/model.py` это `stem="cifar"`); в этом прогоне, следуя заданию, оставлен
   стандартный вариант torchvision.
3. **Объём данных:** обучение идёт на подвыборке 10 000 картинок (по 1000 на класс) —
   это компромисс ради CPU. На полном train (50 000) та же конфигурация ожидаемо даёт
   больше (проверяется флагом `--per-class 5000`; на этом CPU ~15 мин вместо 2,5 мин,
   поэтому в основной прогон не включено).

Модель сохранена в `outputs/model_head_only.pt` — её подхватывает этап 4
(задание со звёздочкой) для сравнения «до/после fine-tuning».
"""
    (TASK_DIR / "results.md").write_text(report_md, encoding="utf-8")
    save_json({
        "config": {"epochs": args.epochs, "lr": args.lr, "batch_size": args.batch_size,
                   "scheduler": args.scheduler, "bn_mode": args.bn_mode,
                   "per_class": args.per_class,
                   "train_size": len(train_ds), "test_size": len(test_ds)},
        "param_stats": stats,
        "history": history,
        "test": {"accuracy": round(float(result["acc"]), 4), "loss": round(float(result["loss"]), 4),
                 "macro_f1": report["macro_f1"], "per_class": report["per_class"],
                 "confusion_matrix": report["confusion_matrix"]},
        "inference": exam,
        "bn_compare": bn_compare,
        "total_seconds": round(total_seconds, 1),
        "eval_seconds": round(t_eval["seconds"], 1),
    }, OUTPUTS / "metrics.json")
    print(f"\nОтчёт: {TASK_DIR / 'results.md'}")


if __name__ == "__main__":
    main()
