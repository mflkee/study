"""Графики практики №3: распределение классов, аугментации, кривые, confusion matrix."""

from __future__ import annotations

from pathlib import Path

import matplotlib
matplotlib.use("Agg")  # headless: сохраняем в файлы, UI не нужен
import matplotlib.pyplot as plt
import numpy as np
import torch

from .data import CIFAR10_CLASSES, CIFAR10_MEAN, CIFAR10_STD

plt.rcParams.update({"figure.dpi": 120, "font.size": 9, "axes.grid": True,
                     "grid.alpha": 0.3, "axes.titlesize": 10})


def denormalize(image: torch.Tensor) -> torch.Tensor:
    """Тензор CHW (нормализованный) -> CHW в диапазоне [0, 1] для показа."""
    mean = torch.tensor(CIFAR10_MEAN, dtype=image.dtype).view(3, 1, 1)
    std = torch.tensor(CIFAR10_STD, dtype=image.dtype).view(3, 1, 1)
    return (image.detach().cpu() * std + mean).clamp(0, 1)


def to_numpy_image(image) -> np.ndarray:
    """CHW [0,1] (тензор) или PIL/numpy HWC -> numpy HWC для imshow."""
    if torch.is_tensor(image):
        if image.ndim == 3 and image.shape[0] == 3:
            image = image.permute(1, 2, 0)
        return image.detach().cpu().numpy()
    arr = np.asarray(image)
    if arr.ndim == 3 and arr.shape[0] == 3 and arr.shape[-1] != 3:
        arr = np.moveaxis(arr, 0, -1)
    return arr


def savefig(fig: plt.Figure, path: Path) -> Path:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(path, bbox_inches="tight")
    plt.close(fig)
    print(f"  сохранён график: {path.name}")
    return path


def plot_class_distribution(counts: list[int], path: Path, title: str = "Распределение по классам CIFAR-10") -> Path:
    fig, ax = plt.subplots(figsize=(9, 3.2))
    bars = ax.bar(CIFAR10_CLASSES, counts, color="#4C72B0")
    ax.bar_label(bars, fontsize=8)
    ax.set_title(title)
    ax.set_ylabel("картинок")
    ax.tick_params(axis="x", rotation=30)
    ax.grid(axis="x", visible=False)
    return savefig(fig, path)


def plot_image_grid(images: list[torch.Tensor],
                    titles: list[str] | None = None,
                    path: Path | None = None,
                    ncols: int = 8,
                    suptitle: str | None = None,
                    figsize_scale: float = 1.6) -> Path | None:
    """Сетка картинок (CHW, нормализованные или [0,1])."""
    n = len(images)
    nrows = int(np.ceil(n / ncols))
    fig, axes = plt.subplots(nrows, ncols, figsize=(ncols * figsize_scale, nrows * figsize_scale))
    axes = np.atleast_1d(axes).ravel()
    for ax in axes:
        ax.axis("off")
    for ax, img in zip(axes, images):
        ax.imshow(to_numpy_image(denormalize(img) if img.min() < 0 else img))
    if titles:
        for ax, title in zip(axes, titles):
            ax.set_title(title, fontsize=8)
            ax.axis("off")
    if suptitle:
        fig.suptitle(suptitle, fontsize=11)
    fig.tight_layout()
    return savefig(fig, path) if path else None


def plot_augmentations(base_image, transform, path: Path, n: int = 8) -> Path:
    """Одна и та же картинка после N случайных применений обучающих аугментаций."""
    images, titles = [to_numpy_image(base_image)] if isinstance(base_image, torch.Tensor) else [], []
    if not images:
        arr = np.asarray(base_image)
        images, titles = [arr], ["original"]
    for i in range(n):
        images.append(to_numpy_image(denormalize(transform(base_image))))
        titles.append(f"aug #{i + 1}")
    return plot_image_grid(images, titles, path, ncols=n + 1,
                           suptitle="Аугментации: одна картинка -> разные её версии") or path


def plot_training_curves(history: list[dict], path: Path, title: str = "Кривые обучения") -> Path:
    epochs = [row["epoch"] for row in history]
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(10, 3.4))
    ax1.plot(epochs, [r["train_loss"] for r in history], "o-", label="train")
    ax1.plot(epochs, [r["test_loss"] for r in history], "s--", label="test")
    ax1.set_title("Loss")
    ax1.set_xlabel("эпоха")
    ax1.legend()
    ax2.plot(epochs, [r["train_acc"] for r in history], "o-", label="train")
    ax2.plot(epochs, [r["test_acc"] for r in history], "s--", label="test")
    ax2.set_title("Accuracy")
    ax2.set_xlabel("эпоха")
    ax2.legend()
    fig.suptitle(title)
    return savefig(fig, path)


def plot_confusion_matrix(cm: np.ndarray, path: Path, title: str = "Confusion Matrix") -> Path:
    cm = np.asarray(cm)
    fig, ax = plt.subplots(figsize=(7.5, 6.5))
    im = ax.imshow(cm, cmap="Blues")
    ax.set_xticks(range(len(CIFAR10_CLASSES)), CIFAR10_CLASSES, rotation=45, ha="right")
    ax.set_yticks(range(len(CIFAR10_CLASSES)), CIFAR10_CLASSES)
    ax.set_xlabel("предсказано")
    ax.set_ylabel("истина")
    ax.set_title(title)
    ax.grid(visible=False)
    threshold = cm.max() / 2 if cm.max() else 1
    for i in range(cm.shape[0]):
        for j in range(cm.shape[1]):
            if cm[i, j]:
                ax.text(j, i, str(cm[i, j]), ha="center", va="center", fontsize=7,
                        color="white" if cm[i, j] > threshold else "black")
    fig.colorbar(im, ax=ax, fraction=0.046)
    return savefig(fig, path)


def plot_misclassified(images: torch.Tensor,
                       y_true: np.ndarray,
                       y_pred: np.ndarray,
                       probs: np.ndarray,
                       path: Path,
                       n: int = 12,
                       ncols: int = 6,
                       title: str | None = None) -> Path:
    """Картинки, на которых модель ошиблась (с указанием уверенности)."""
    wrong = np.flatnonzero(y_true != y_pred)[:n]
    fig, axes = plt.subplots(int(np.ceil(len(wrong) / ncols)), ncols,
                             figsize=(ncols * 1.9, int(np.ceil(len(wrong) / ncols)) * 2.3))
    axes = np.atleast_1d(axes).ravel()
    for ax in axes:
        ax.axis("off")
    for ax, idx in zip(axes, wrong):
        ax.imshow(to_numpy_image(denormalize(images[idx])))
        conf = probs[idx][y_pred[idx]]
        ax.set_title(f"истина: {CIFAR10_CLASSES[y_true[idx]]}\n"
                     f"предсказ.: {CIFAR10_CLASSES[y_pred[idx]]} ({conf:.0%})", fontsize=7)
        ax.axis("off")
    fig.suptitle(title or f"Ошибки модели (первые {len(wrong)})")
    fig.tight_layout()
    return savefig(fig, path)


def plot_bar_compare(labels: list[str], values: list[float], path: Path,
                     title: str = "Сравнение", ylabel: str = "accuracy",
                     colors: list[str] | None = None) -> Path:
    fig, ax = plt.subplots(figsize=(7, 3.4))
    bars = ax.bar(labels, values, color=colors or "#4C72B0")
    ax.bar_label(bars, fmt="%.4f", fontsize=9)
    ax.set_ylabel(ylabel)
    ax.set_title(title)
    ax.set_ylim(0, max(values) * 1.2 if max(values) > 0 else 1)
    ax.tick_params(axis="x", rotation=12)
    ax.grid(axis="x", visible=False)
    return savefig(fig, path)


def plot_feature_maps(feature_maps: dict[str, torch.Tensor], path: Path,
                      n_channels: int = 8, title: str = "Что «видит» CNN") -> Path:
    """Сетка карт активаций: строка = глубина сети, столбец = канал."""
    names = list(feature_maps)
    nrows = len(names)
    fig, axes = plt.subplots(nrows, n_channels, figsize=(n_channels * 1.2, nrows * 1.5))
    axes = np.atleast_2d(axes)
    for row, name in enumerate(names):
        fm = feature_maps[name][0].detach().cpu()  # берём первое изображение батча
        for col in range(n_channels):
            ax = axes[row, col]
            ax.axis("off")
            if col < fm.shape[0]:
                ax.imshow(fm[col], cmap="viridis")
            if col == 0:
                ax.set_title(f"{name}\n{fm.shape[1]}x{fm.shape[2]}", fontsize=8)
    fig.suptitle(title)
    fig.tight_layout()
    return savefig(fig, path)


def plot_filters(weights: torch.Tensor, path: Path, n: int = 16, ncols: int = 8,
                 title: str = "Фильтры первого свёрточного слоя (conv1)") -> Path:
    """Фильтры 7x7x3 первого слоя — «детекторы» простых паттернов."""
    fig, axes = plt.subplots(int(np.ceil(n / ncols)), ncols,
                             figsize=(ncols * 1.1, int(np.ceil(n / ncols)) * 1.2))
    axes = np.atleast_1d(axes).ravel()
    w = weights.detach().cpu()
    w = (w - w.min()) / (w.max() - w.min() + 1e-8)  # к [0,1] для показа
    for ax in axes:
        ax.axis("off")
    for ax, filt in zip(axes, w[:n]):
        ax.imshow(filt.permute(1, 2, 0))
    fig.suptitle(title)
    fig.tight_layout()
    return savefig(fig, path)
