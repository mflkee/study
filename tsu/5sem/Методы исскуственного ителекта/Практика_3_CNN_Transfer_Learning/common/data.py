"""CIFAR-10: загрузка, аугментации, стратифицированная подвыборка, DataLoader'ы."""

from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Sequence

import numpy as np
import torch
from torch.utils.data import DataLoader, Dataset, Subset
from torchvision import datasets, transforms

from .utils import DATA_ROOT, SEED

#: порядок классов в CIFAR-10 (индекс = метка)
CIFAR10_CLASSES: tuple[str, ...] = (
    "airplane", "automobile", "bird", "cat", "deer",
    "dog", "frog", "horse", "ship", "truck",
)

#: среднее/std по обучающей выборке CIFAR-10 (стандартные константы)
CIFAR10_MEAN: tuple[float, float, float] = (0.4914, 0.4822, 0.4465)
CIFAR10_STD: tuple[float, float, float] = (0.2470, 0.2435, 0.2616)


def test_transform() -> transforms.Compose:
    """Валидация/тест: только тензор + нормализация (никаких случайностей)."""
    return transforms.Compose([
        transforms.ToTensor(),
        transforms.Normalize(CIFAR10_MEAN, CIFAR10_STD),
    ])


def train_transform(augment: bool = True) -> transforms.Compose:
    """Обучение: аугментации (этап 1 практики) + тензор + нормализация."""
    if not augment:
        return test_transform()
    return transforms.Compose([
        transforms.RandomCrop(32, padding=4),                 # сдвиги на ±4 пикселя
        transforms.RandomHorizontalFlip(p=0.5),               # отражение по горизонтали
        transforms.ColorJitter(brightness=0.2,                # яркость/контраст/насыщенность
                               contrast=0.2,
                               saturation=0.2),
        transforms.ToTensor(),
        transforms.Normalize(CIFAR10_MEAN, CIFAR10_STD),      # нулевое среднее, единичная дисперсия
    ])


def download_cifar10() -> tuple[datasets.CIFAR10, datasets.CIFAR10]:
    """Скачивает (однократно) CIFAR-10 в `data/` и возвращает датасеты без transform."""
    root = str(DATA_ROOT)
    DATA_ROOT.mkdir(parents=True, exist_ok=True)
    train = datasets.CIFAR10(root=root, train=True, download=True, transform=None)
    test = datasets.CIFAR10(root=root, train=False, download=True, transform=None)
    return train, test


def stratified_indices(dataset: Dataset, per_class: int, seed: int = SEED) -> list[int]:
    """По `per_class` картинок каждого класса — чтобы подвыборка не была перекошена."""
    rng = np.random.default_rng(seed)
    targets = np.asarray(getattr(dataset, "targets"))
    picked: list[np.ndarray] = []
    for cls in range(len(CIFAR10_CLASSES)):
        cls_idx = np.flatnonzero(targets == cls)
        take = min(per_class, cls_idx.size)
        picked.append(rng.choice(cls_idx, size=take, replace=False))
    idx = np.concatenate(picked)
    rng.shuffle(idx)  # перемешиваем порядок (классы идут подряд после конкатенации)
    return [int(i) for i in idx]


def class_counts(targets: Sequence[int]) -> list[int]:
    targets = np.asarray(targets)
    return [int((targets == c).sum()) for c in range(len(CIFAR10_CLASSES))]


def build_datasets(per_class: int = 1000,
                   seed: int = SEED,
                   augment: bool = True,
                   index_file: Path | None = None,
                   ) -> tuple[Subset, datasets.CIFAR10, list[int]]:
    """Подвыборка train (per_class * 10 картинок) + полный test (10 000 картинок).

    `index_file` — если задан, индексы подвыборки сохраняются в JSON: остальные
    задания читают его и работают ровно на той же подвыборке.
    """
    root = str(DATA_ROOT)
    DATA_ROOT.mkdir(parents=True, exist_ok=True)
    train_full = datasets.CIFAR10(root=root, train=True, download=True, transform=train_transform(augment))
    test_full = datasets.CIFAR10(root=root, train=False, download=True, transform=test_transform())

    if index_file is not None and Path(index_file).exists():
        indices = json.loads(Path(index_file).read_text(encoding="utf-8"))["indices"]
    else:
        indices = stratified_indices(train_full, per_class=per_class, seed=seed)
        if index_file is not None:
            Path(index_file).parent.mkdir(parents=True, exist_ok=True)
            Path(index_file).write_text(
                json.dumps({"per_class": per_class, "seed": seed, "n": len(indices), "indices": indices},
                           ensure_ascii=False, indent=2),
                encoding="utf-8",
            )
    return Subset(train_full, indices), test_full, indices


def workers_default() -> int:
    """Число процессов предзагрузки: не больше 6, чтобы не съесть всю машину."""
    return max(0, min(6, (os.cpu_count() or 4) - 2))


def build_loaders(train_ds: Dataset,
                  test_ds: Dataset,
                  batch_size: int = 128,
                  test_batch_size: int = 256,
                  workers: int | None = None,
                  ) -> tuple[DataLoader, DataLoader]:
    workers = workers_default() if workers is None else workers
    common = dict(num_workers=workers, pin_memory=False,
                  persistent_workers=workers > 0)
    train_loader = DataLoader(train_ds, batch_size=batch_size, shuffle=True, drop_last=False, **common)
    test_loader = DataLoader(test_ds, batch_size=test_batch_size, shuffle=False, **common)
    return train_loader, test_loader


def dataset_stats(dataset: Dataset, limit: int | None = None) -> dict[str, list[float]]:
    """Реальные mean/std картинок датасета (для сравнения с константами нормализации)."""
    n = len(dataset) if limit is None else min(limit, len(dataset))
    s = np.zeros(3)
    s2 = np.zeros(3)
    total = 0
    for i in range(n):
        img = dataset[i]
        if isinstance(img, tuple):
            img = img[0]
        arr = np.asarray(img, dtype=np.float64)
        if arr.ndim == 3 and arr.shape[0] == 3:  # CHW (тензор) -> HWC
            arr = np.moveaxis(arr, 0, -1)
        arr = arr / 255.0
        s += arr.sum(axis=(0, 1))
        s2 += (arr ** 2).sum(axis=(0, 1))
        total += arr.shape[0] * arr.shape[1]
    mean = s / total
    std = np.sqrt(np.maximum(s2 / total - mean ** 2, 0.0))
    return {"mean": [round(float(v), 4) for v in mean],
            "std": [round(float(v), 4) for v in std],
            "n_images": int(n)}
