#!/usr/bin/env python
"""Практика №3, задание со звёздочкой: fine-tuning и сравнение с обучением «с нуля».

Что делает:
  1. берёт модель этапа 3 (обучалась только голова) -> baseline accuracy;
  2. размораживает последний блок (layer4 + fc) и дообучает -> сравнивает;
  3. (--full) размораживает всю сеть и дообучает -> сравнивает;
  4. для контекста обучает ResNet18 со случайной инициализацией (baseline «с нуля»).

Запуск из корня практики:
    ../.venv/bin/python 04_fine_tuning_star/main.py [--epochs 3] [--full] [--skip-scratch]
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
from common.data import build_datasets, build_loaders  # noqa: E402
from common.engine import classification_report, evaluate, fit, save_model  # noqa: E402
from common.model import (build_resnet18, param_stats, unfreeze_all,  # noqa: E402
                          unfreeze_last_block)
from common.utils import (device_info, fmt_seconds, get_device, header, load_json,  # noqa: E402
                          save_json, set_seed, timer)

OUTPUTS = TASK_DIR / "outputs"
SUBSET_INDEX_FILE = PRACTICE_ROOT / "01_data_augmentation" / "outputs" / "subset_indices.json"
HEAD_MODEL_PATH = PRACTICE_ROOT / "03_train_eval" / "outputs" / "model_head_only.pt"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Звёздочка: fine-tuning ResNet18")
    parser.add_argument("--epochs", type=int, default=5)
    parser.add_argument("--per-class", type=int, default=1000,
                        help="должно совпадать с этапом 3 (иначе сравнение нечестное)")
    parser.add_argument("--lr-finetune", type=float, default=1e-4,
                        help="маленький lr для предобученных весов (иначе их «сломает»)")
    parser.add_argument("--lr-scratch", type=float, default=1e-2,
                        help="lr для обучения с нуля")
    parser.add_argument("--batch-size", type=int, default=128)
    parser.add_argument("--bn-mode", default="frozen", choices=["frozen", "train"],
                        help="режим BatchNorm при fine-tuning (см. этап 3)")
    parser.add_argument("--full", action="store_true",
                        help="дополнительно разморозить всю сеть (полный fine-tuning)")
    parser.add_argument("--skip-scratch", action="store_true",
                        help="не обучать модель «с нуля» (экономит время)")
    return parser.parse_args()


def full_eval(model: nn.Module, test_loader, device) -> dict:
    """Accuracy + macro-F1 + метрики по классам на полном тесте."""
    criterion = nn.CrossEntropyLoss()
    result = evaluate(model, test_loader, criterion, device, with_predictions=True)
    report = classification_report(result["y_true"], result["y_pred"])
    return {"acc": float(result["acc"]), "loss": float(result["loss"]),
            "macro_f1": float(report["macro_f1"]), "per_class": report["per_class"],
            "confusion_matrix": report["confusion_matrix"]}


def main() -> None:
    args = parse_args()
    set_seed()
    header("Задание со звёздочкой: Fine-tuning ResNet18")
    OUTPUTS.mkdir(parents=True, exist_ok=True)
    device = get_device()
    print(f"Устройство: {device_info()}")

    if not HEAD_MODEL_PATH.exists():
        raise SystemExit(f"Нет модели {HEAD_MODEL_PATH}. Сначала запустите 03_train_eval/main.py")

    train_ds, test_ds, _ = build_datasets(per_class=args.per_class,
                                          index_file=(SUBSET_INDEX_FILE if args.per_class == 1000
                                                      else OUTPUTS / f"subset_indices_{args.per_class}.json"))
    train_loader, test_loader = build_loaders(train_ds, test_ds, batch_size=args.batch_size)
    print(f"Обучение: {len(train_ds)} картинок; тест: {len(test_ds)} картинок")

    results: dict[str, dict] = {}
    histories: dict[str, list] = {}
    timings: dict[str, float] = {}

    # --- 0. Baseline: модель этапа 3 (обучалась только голова) ---------------
    head_meta = torch.load(HEAD_MODEL_PATH, map_location="cpu").get("meta", {})
    model = build_resnet18(pretrained=True, freeze_backbone=True).to(device)
    model.load_state_dict(torch.load(HEAD_MODEL_PATH, map_location=device)["state_dict"])
    with timer("оценка baseline"):
        results["head"] = full_eval(model, test_loader, device)
    print(f"\n[0] Baseline (голова после этапа 3): accuracy = {results['head']['acc']:.4f}, "
          f"macro-F1 = {results['head']['macro_f1']:.4f}")
    print(f"    (в metrics.json этапа 3 было {head_meta.get('accuracy')})")
    timings["head"] = 0.0

    # --- 1. Fine-tuning последнего блока (layer4 + fc) -----------------------
    bn_frozen = args.bn_mode == "frozen"
    print(f"\n[1] Fine-tuning layer4 + fc: Adam, lr={args.lr_finetune}, {args.epochs} эпох, "
          f"BN в режиме '{args.bn_mode}'")
    model = unfreeze_last_block(model)
    stats_layer4 = param_stats(model)
    print(f"    обучаемых параметров: {stats_layer4['trainable_human']} "
          f"({stats_layer4['trainable_share_pct']}% от модели)")
    with timer("fine-tuning layer4") as t:
        histories["layer4"] = fit(model, train_loader, test_loader, epochs=args.epochs,
                                  lr=args.lr_finetune, device=device, tag="ft-layer4",
                                  bn_frozen=bn_frozen, scheduler="cosine")
    timings["layer4"] = t["seconds"]
    results["layer4"] = full_eval(model, test_loader, device)
    print(f"    => accuracy = {results['layer4']['acc']:.4f} "
          f"(было {results['head']['acc']:.4f}, "
          f"дельта {results['layer4']['acc'] - results['head']['acc']:+.4f})")
    save_model(model, OUTPUTS / "model_finetuned_layer4.pt",
               meta={"stage": "layer4+fc", "epochs": args.epochs, "accuracy": results["layer4"]["acc"],
                     "bn_mode": args.bn_mode})

    # --- 2. Опционально: полный fine-tuning ---------------------------------
    if args.full:
        print(f"\n[2] Полный fine-tuning (вся сеть): Adam, lr={args.lr_finetune}, {args.epochs} эпох")
        model = unfreeze_all(model)
        stats_full = param_stats(model)
        print(f"    обучаемых параметров: {stats_full['trainable_human']} "
              f"({stats_full['trainable_share_pct']}%)")
        with timer("fine-tuning всей сети") as t:
            histories["full"] = fit(model, train_loader, test_loader, epochs=args.epochs,
                                    lr=args.lr_finetune, device=device, tag="ft-full",
                                    bn_frozen=bn_frozen, scheduler="cosine")
        timings["full"] = t["seconds"]
        results["full"] = full_eval(model, test_loader, device)
        print(f"    => accuracy = {results['full']['acc']:.4f}")
        save_model(model, OUTPUTS / "model_finetuned_full.pt",
                   meta={"stage": "all", "epochs": args.epochs, "accuracy": results["full"]["acc"]})

    # --- 3. Контроль: обучение «с нуля» -------------------------------------
    if not args.skip_scratch:
        print(f"\n[3] Обучение С НУЛЯ (случайные веса): Adam, lr={args.lr_scratch}, {args.epochs} эпох")
        set_seed()
        scratch = build_resnet18(pretrained=False, freeze_backbone=False).to(device)
        print(f"    обучаемых параметров: {param_stats(scratch)['trainable_human']} (вся сеть)")
        with timer("обучение с нуля") as t:
            histories["scratch"] = fit(scratch, train_loader, test_loader, epochs=args.epochs,
                                       lr=args.lr_scratch, device=device, tag="scratch",
                                       scheduler="cosine")
        timings["scratch"] = t["seconds"]
        results["scratch"] = full_eval(scratch, test_loader, device)
        print(f"    => accuracy = {results['scratch']['acc']:.4f}")
        save_model(scratch, OUTPUTS / "model_scratch.pt",
                   meta={"stage": "scratch", "epochs": args.epochs, "accuracy": results["scratch"]["acc"]})

    # --- Графики -------------------------------------------------------------
    print("\n[Визуализация]")
    labels = {"head": "Transfer: только голова", "layer4": "Fine-tuning layer4+fc",
              "full": "Fine-tuning вся сеть", "scratch": "С нуля (random init)"}
    colors = {"head": "#8C8C8C", "layer4": "#4C72B0", "full": "#55A868", "scratch": "#C44E52"}
    names = [k for k in ["head", "layer4", "full", "scratch"] if k in results]
    viz.plot_bar_compare([labels[k] for k in names], [results[k]["acc"] for k in names],
                         OUTPUTS / "01_accuracy_compare.png",
                         title="Accuracy на тесте CIFAR-10: Transfer Learning vs обучение с нуля",
                         colors=[colors[k] for k in names])
    for key in ["head", "layer4", "full", "scratch"]:
        if key in results:
            viz.plot_confusion_matrix(np.array(results[key]["confusion_matrix"]),
                                      OUTPUTS / f"confusion_{key}.png",
                                      f"Confusion Matrix — {labels[key]} (acc={results[key]['acc']:.3f})")
    for key in ["layer4", "full", "scratch"]:
        if key in histories:
            viz.plot_training_curves(histories[key], OUTPUTS / f"curves_{key}.png",
                                     f"{labels[key]}: {args.epochs} эпохи, lr="
                                     f"{args.lr_scratch if key == 'scratch' else args.lr_finetune}")

    # --- Отчёт ---------------------------------------------------------------
    def delta_text(key: str) -> str:
        if key == "head":
            return "—"
        return f"{results[key]['acc'] - results['head']['acc']:+.4f}"

    table_rows = "\n".join(
        f"| {labels[k]} | {results[k]['acc']:.4f} | {results[k]['macro_f1']:.4f} | {delta_text(k)} | "
        f"{fmt_seconds(timings[k]) if timings[k] else '—'} |"
        for k in names)

    curves_rows = "\n".join(
        f"| {labels[k]} | " + " -> ".join(f"{r['test_acc']:.3f}" for r in histories[k]) + " |"
        for k in ["layer4", "full", "scratch"] if k in histories)

    scratch_text = ""
    if "scratch" in results:
        delta = results["head"]["acc"] - results["scratch"]["acc"]
        total_params = 11_181_642  # ResNet18 + голова на 10 классов
        scratch_text = f"""
## Transfer Learning против обучения «с нуля»

ResNet18 со случайной инициализацией на той же подвыборке ({len(train_ds)} картинок, {args.epochs} эпох)
даёт **{results['scratch']['acc']:.4f}**, тогда как замороженный ImageNet-бэкенд с одной обученной
головой — **{results['head']['acc']:.4f}**. Разница **{delta:+.4f}** в пользу Transfer Learning, причём
обучаемых параметров в {total_params / 5130:.0f} раз меньше и обучается она в разы быстрее. Это ровно
та идея со слайда 5: «обучать CNN с нуля» на маленьком датасете почти бессмысленно — сеть не
успевает выучить признаки (и веса 11 млн параметров просто нечем «наполнить»).
"""

    report_md = f"""# Задание со звёздочкой. Fine-tuning ResNet18

## Гипотеза

Модель этапа 3 обучала только голову, а свёрточные слои оставались «чужими»
(ImageNet: 1000 классов, 224x224). Если **разморозить последние слои** и дообучить
их на CIFAR-10 с маленьким learning rate, признаки подстроятся под задачу и
accuracy вырастет. Маленький lr нужен, чтобы не разрушить уже хорошие веса.

## Результаты

| Вариант | Accuracy | Macro-F1 | Δ к baseline | Время |
|---|---|---|---|---|
{table_rows}

{scratch_text}
## Accuracy по эпохам (test)

| Вариант | эпоха 1 -> ... |
|---|---|
{curves_rows}

## Что смотреть в графиках

| Файл | Что показывает |
|------|----------------|
| `outputs/01_accuracy_compare.png` | итоговое сравнение всех вариантов |
| `outputs/curves_layer4.png` | кривые дообучения layer4 + fc |
| `outputs/confusion_head.png` | ошибки модели «только голова» |
| `outputs/confusion_layer4.png` | ошибки после fine-tuning (обычно меньше) |

## Вывод

* Fine-tuning последних слоёв даёт **{'прирост' if results['layer4']['acc'] > results['head']['acc'] else 'изменение'}**
  относительно обучения одной головы: {results['head']['acc']:.4f} -> {results['layer4']['acc']:.4f}.
* Обучение с нуля на маленьком датасете проигрывает передаче обучения — при
  {args.epochs} эпохах и {len(train_ds)} картинках сеть просто не успевает выучить фильтры.
* Практический вывод: сначала обучаем голову (быстро, безопасно), затем — если
  данных и времени достаточно — аккуратно размораживаем последние блоки.

Модели сохранены: `outputs/model_finetuned_layer4.pt`{' , `outputs/model_finetuned_full.pt`' if args.full else ''}{' , `outputs/model_scratch.pt`' if 'scratch' in results else ''}.
"""
    (TASK_DIR / "results.md").write_text(report_md, encoding="utf-8")
    metrics_path = OUTPUTS / "metrics.json"
    existing = load_json(metrics_path) if metrics_path.exists() else {}
    existing.update({
        "config": {"epochs": args.epochs, "lr_finetune": args.lr_finetune, "lr_scratch": args.lr_scratch,
                   "full_unfreeze": args.full, "train_size": len(train_ds), "test_size": len(test_ds)},
        "results": {k: {"accuracy": round(v["acc"], 4), "macro_f1": round(v["macro_f1"], 4),
                        "per_class": v["per_class"]} for k, v in results.items()},
        "deltas_vs_head": {k: round(results[k]["acc"] - results["head"]["acc"], 4)
                           for k in results if k != "head"},
        "histories": histories,
        "timings_seconds": {k: round(v, 1) for k, v in timings.items()},
    })
    save_json(existing, metrics_path)
    print(f"\nОтчёт: {TASK_DIR / 'results.md'}")


if __name__ == "__main__":
    main()
