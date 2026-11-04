"""Общие утилиты для практики №3 (CNN + Transfer Learning).

Все задания лежат в соседних папках (`01_*`, `02_*`, ...) и используют этот
пакет: пути, сид, устройство, таймер, сохранение метрик.
"""

from __future__ import annotations

import json
import random
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Any

import numpy as np
import torch

#: корень практики (папка, где лежат 01_*, 02_*, ... и README.md)
PRACTICE_ROOT = Path(__file__).resolve().parents[1]
#: куда torchvision складывает CIFAR-10
DATA_ROOT = PRACTICE_ROOT / "data"

#: единый сид для воспроизводимости всех заданий
SEED = 42


def set_seed(seed: int = SEED) -> None:
    """Фиксируем все источники случайности (python, numpy, torch)."""
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed_all(seed)


def get_device() -> torch.device:
    """GPU, если есть, иначе CPU (в этом окружении — Intel Iris Xe => CPU)."""
    if torch.cuda.is_available():
        return torch.device("cuda")
    if getattr(torch.backends, "mps", None) is not None and torch.backends.mps.is_available():
        return torch.device("mps")
    return torch.device("cpu")


def device_info() -> str:
    device = get_device()
    if device.type == "cuda":
        return f"cuda: {torch.cuda.get_device_name(0)}"
    return f"cpu: {torch.get_num_threads()} потоков, {torch.get_num_interop_threads()} inter-op"


@contextmanager
def timer(label: str = ""):
    """Замер времени блока: `with timer('эпоха'): ...` -> печатает и отдаёт секунды."""
    start = time.perf_counter()
    box: dict[str, float] = {}
    try:
        yield box
    finally:
        box["seconds"] = time.perf_counter() - start
        print(f"[timer] {label}: {fmt_seconds(box['seconds'])}")


def fmt_seconds(seconds: float) -> str:
    if seconds < 60:
        return f"{seconds:.1f} с"
    minutes, sec = divmod(int(seconds), 60)
    if minutes < 60:
        return f"{minutes} мин {sec} с"
    hours, minutes = divmod(minutes, 60)
    return f"{hours} ч {minutes} мин {sec} с"


def save_json(obj: Any, path: Path) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2), encoding="utf-8")


def load_json(path: Path) -> Any:
    return json.loads(Path(path).read_text(encoding="utf-8"))


def human_params(n: int) -> str:
    if n >= 1_000_000:
        return f"{n / 1_000_000:.2f} млн"
    if n >= 1_000:
        return f"{n / 1_000:.1f} тыс."
    return str(n)


def header(title: str, char: str = "=") -> None:
    """Простой заголовок в консоли (без внешних зависимостей)."""
    line = char * max(len(title), 60)
    print(f"\n{line}\n{title}\n{line}")
