"""Цикл обучения/оценки: одна эпоха, метрики, история, classification report."""

from __future__ import annotations

import time
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader

from .data import CIFAR10_CLASSES
from .utils import fmt_seconds, human_params


def train_one_epoch(model: nn.Module,
                    loader: DataLoader,
                    criterion: nn.Module,
                    optimizer: torch.optim.Optimizer,
                    device: torch.device,
                    bn_frozen: bool = False) -> dict[str, float]:
    """Одна эпоха.

    bn_frozen=True — слои BatchNorm остаются в eval-режиме: нормализация идёт по
    статистике ImageNet, признаки замороженного бэкенда не «плывут». Для
    линейного пробника по предобученным признакам это стандартная практика.
    """
    model.train()
    if bn_frozen:
        for module in model.modules():
            if isinstance(module, nn.modules.batchnorm._BatchNorm):
                module.eval()
    loss_sum = 0.0
    correct = 0
    seen = 0
    for images, targets in loader:
        images, targets = images.to(device), targets.to(device)
        optimizer.zero_grad(set_to_none=True)
        logits = model(images)
        loss = criterion(logits, targets)
        loss.backward()
        optimizer.step()

        loss_sum += loss.item() * targets.size(0)
        correct += (logits.argmax(dim=1) == targets).sum().item()
        seen += targets.size(0)
    return {"loss": loss_sum / seen, "acc": correct / seen, "n": seen}


@torch.no_grad()
def evaluate(model: nn.Module,
             loader: DataLoader,
             criterion: nn.Module,
             device: torch.device,
             with_predictions: bool = False) -> dict[str, object]:
    """Loss + accuracy; при with_predictions=True добавляет y_true/y_pred/probs."""
    model.eval()
    loss_sum = 0.0
    correct = 0
    seen = 0
    y_true: list[np.ndarray] = []
    y_pred: list[np.ndarray] = []
    probs: list[np.ndarray] = []

    for images, targets in loader:
        images, targets = images.to(device), targets.to(device)
        logits = model(images)
        loss = criterion(logits, targets)
        pred = logits.argmax(dim=1)

        loss_sum += loss.item() * targets.size(0)
        correct += (pred == targets).sum().item()
        seen += targets.size(0)

        if with_predictions:
            y_true.append(targets.cpu().numpy())
            y_pred.append(pred.cpu().numpy())
            probs.append(torch.softmax(logits, dim=1).cpu().numpy())

    result: dict[str, object] = {"loss": loss_sum / seen, "acc": correct / seen, "n": seen}
    if with_predictions:
        result["y_true"] = np.concatenate(y_true)
        result["y_pred"] = np.concatenate(y_pred)
        result["probs"] = np.concatenate(probs)
    return result


def fit(model: nn.Module,
        train_loader: DataLoader,
        test_loader: DataLoader,
        epochs: int,
        lr: float,
        device: torch.device,
        tag: str = "train",
        optimizer_name: str = "Adam",
        log_every: int | None = None,
        bn_frozen: bool = False,
        scheduler: str | None = None) -> list[dict[str, float]]:
    """Обучает модель и возвращает историю по эпохам (epoch, loss, acc, время).

    scheduler="cosine" — косинусное затухание lr от lr до lr/50 по эпохам
    (для линейного пробника это даёт заметно лучшую сходимость за те же эпохи).
    """
    criterion = nn.CrossEntropyLoss()
    params = [p for p in model.parameters() if p.requires_grad]
    optimizer = torch.optim.Adam(params, lr=lr)
    lr_scheduler = None
    if scheduler == "cosine":
        lr_scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=epochs, eta_min=lr / 50)
    elif scheduler is not None:
        raise ValueError(f"неизвестный scheduler: {scheduler}")
    trainable = sum(p.numel() for p in params)
    print(f"[{tag}] обучаемых параметров: {human_params(trainable)}; "
          f"{optimizer_name}, lr={lr}, эпох={epochs}"
          f"{', BN заморожен' if bn_frozen else ''}"
          f"{', cosine-затухание lr' if lr_scheduler else ''}")

    history: list[dict[str, float]] = []
    for epoch in range(1, epochs + 1):
        start = time.perf_counter()
        train_metrics = train_one_epoch(model, train_loader, criterion, optimizer, device, bn_frozen=bn_frozen)
        test_metrics = evaluate(model, test_loader, criterion, device)
        elapsed = time.perf_counter() - start
        row = {
            "epoch": epoch,
            "lr": round(optimizer.param_groups[0]["lr"], 6),
            "train_loss": round(train_metrics["loss"], 4),
            "train_acc": round(train_metrics["acc"], 4),
            "test_loss": round(float(test_metrics["loss"]), 4),
            "test_acc": round(float(test_metrics["acc"]), 4),
            "seconds": round(elapsed, 1),
        }
        history.append(row)
        if lr_scheduler is not None:
            lr_scheduler.step()
        print(f"[{tag}] эпоха {epoch}/{epochs}: "
              f"train loss {row['train_loss']:.4f}, train acc {row['train_acc']:.4f} | "
              f"test loss {row['test_loss']:.4f}, test acc {row['test_acc']:.4f} | "
              f"{fmt_seconds(elapsed)}")
    return history


def classification_report(y_true: np.ndarray,
                          y_pred: np.ndarray,
                          classes: tuple[str, ...] = CIFAR10_CLASSES) -> dict[str, object]:
    """Precision/recall/F1 по классам (как sklearn.metrics.classification_report)."""
    n_classes = len(classes)
    cm = np.zeros((n_classes, n_classes), dtype=int)
    for t, p in zip(y_true, y_pred):
        cm[int(t), int(p)] += 1

    report: dict[str, object] = {"per_class": [], "accuracy": float((y_true == y_pred).mean())}
    f1_scores = []
    for i, name in enumerate(classes):
        tp = cm[i, i]
        fp = cm[:, i].sum() - tp
        fn = cm[i, :].sum() - tp
        precision = tp / (tp + fp) if tp + fp else 0.0
        recall = tp / (tp + fn) if tp + fn else 0.0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        f1_scores.append(f1)
        report["per_class"].append({
            "class": name,
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1": round(f1, 4),
            "support": int(cm[i, :].sum()),
        })
    report["macro_f1"] = round(float(np.mean(f1_scores)), 4)
    report["confusion_matrix"] = cm.tolist()
    return report


def benchmark_inference(model: nn.Module,
                        loader: DataLoader,
                        device: torch.device,
                        max_batches: int = 20) -> dict[str, float]:
    """Скорость инференса (картинок в секунду) — полезно для CPU-окружения."""
    model.eval()
    images_seen = 0
    start = time.perf_counter()
    with torch.no_grad():
        for i, (images, _) in enumerate(loader):
            if i >= max_batches:
                break
            model(images.to(device))
            images_seen += images.size(0)
    elapsed = time.perf_counter() - start
    return {"images": images_seen, "seconds": round(elapsed, 3),
            "images_per_second": round(images_seen / elapsed, 1)}


def save_model(model: nn.Module, path: Path, meta: dict[str, object] | None = None) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    torch.save({"state_dict": model.state_dict(), "meta": meta or {}}, path)
