"""ResNet18 из torchvision: предобученные веса, заморозка, замена головы, разморозка."""

from __future__ import annotations

import torch
import torch.nn as nn
from torchvision import models

from .data import CIFAR10_CLASSES
from .utils import human_params


def build_resnet18(num_classes: int = len(CIFAR10_CLASSES),
                   pretrained: bool = True,
                   freeze_backbone: bool = True,
                   stem: str = "torchvision") -> nn.Module:
    """ResNet18 + новая голова `Linear(512, num_classes)`.

    pretrained=True  -> веса ImageNet (1000 классов) => Transfer Learning;
    pretrained=False -> случайная инициализация => обучение «с нуля» (baseline).
    freeze_backbone=True -> градиенты считаются только для нового FC-слоя.

    `stem` — как обходиться с «входом» сети (стандартный ResNet18 рассчитан на 224x224):
      * `"torchvision"` — как в torchvision: conv 7x7 stride 2 + MaxPool.
        Для CIFAR-10 (32x32) это агрессивно: карта перед avgpool = **1x1**;
      * `"nopool"` — убираем MaxPool (веса conv1 остаются ImageNet-овскими),
        карта = 2x2 -> больше пространственной информации;
      * `"cifar"` — «правильный» CIFAR-stem: conv 3x3 stride 1 без MaxPool,
        веса 7x7 обрезаются до центрального кропа 3x3.
    """
    weights = models.ResNet18_Weights.IMAGENET1K_V1 if pretrained else None
    model = models.resnet18(weights=weights)

    if stem == "nopool":
        model.maxpool = nn.Identity()
    elif stem == "cifar":
        old_conv = model.conv1.weight.data
        model.conv1 = nn.Conv2d(3, 64, kernel_size=3, stride=1, padding=1, bias=False)
        if pretrained:
            # центральный кроп 7x7 -> 3x3: сохраняем структуру детекторов ImageNet
            model.conv1.weight.data = old_conv[:, :, 2:5, 2:5].clone()
        model.maxpool = nn.Identity()
    elif stem != "torchvision":
        raise ValueError(f"неизвестный stem: {stem}")

    if freeze_backbone:
        for param in model.parameters():
            param.requires_grad = False

    # Замена головы: было nn.Linear(512, 1000) -> стало nn.Linear(512, 10).
    # Новый слой по умолчанию обучаемый (requires_grad=True).
    model.fc = nn.Linear(model.fc.in_features, num_classes)
    return model


def unfreeze_last_block(model: nn.Module) -> nn.Module:
    """Fine-tuning «последних слоёв»: размораживаем layer4 (и голову)."""
    for name, param in model.named_parameters():
        if name.startswith("layer4") or name.startswith("fc"):
            param.requires_grad = True
    return model


def unfreeze_all(model: nn.Module) -> nn.Module:
    """Разморозить всю сеть (полный fine-tuning, маленький learning rate)."""
    for param in model.parameters():
        param.requires_grad = True
    return model


def trainable_parameters(model: nn.Module) -> list[nn.Parameter]:
    """Список параметров, которые действительно обучаются (для оптимизатора)."""
    return [p for p in model.parameters() if p.requires_grad]


def param_stats(model: nn.Module) -> dict[str, object]:
    total = sum(p.numel() for p in model.parameters())
    trainable = sum(p.numel() for p in model.parameters() if p.requires_grad)
    return {
        "total_params": total,
        "total_human": human_params(total),
        "trainable_params": trainable,
        "trainable_human": human_params(trainable),
        "frozen_params": total - trainable,
        "trainable_share_pct": round(100 * trainable / total, 3),
    }


def module_table(model: nn.Module) -> list[dict[str, object]]:
    """Таблица «блок -> параметры -> обучается ли» для отчёта."""
    rows: list[dict[str, object]] = []
    for name, module in model.named_children():
        params = sum(p.numel() for p in module.parameters())
        if params == 0 and not isinstance(module, (nn.Linear, nn.Conv2d)):
            continue
        trainable = all(p.requires_grad for p in module.parameters()) if params else False
        rows.append({
            "block": name,
            "type": module.__class__.__name__,
            "params": params,
            "params_human": human_params(params),
            "trainable": bool(trainable),
        })
    return rows


@torch.no_grad()
def describe_forward(model: nn.Module, input_shape: tuple[int, int, int, int] = (2, 3, 32, 32)) -> list[dict[str, object]]:
    """Прогоняет тензор через блоки и печатает форму выхода каждого блока.

    Заодно показывает «агрессивность» стандартного ResNet18 на картинках 32x32:
    после layer4 остаётся карта 1x1 (7x7-свёртка со stride=2 + maxpool).
    """
    model.eval()
    x = torch.zeros(*input_shape)
    rows: list[dict[str, object]] = [{"block": "input", "shape": tuple(x.shape)}]
    for name, module in model.named_children():
        if name == "fc":
            x = torch.flatten(x, 1)
            rows.append({"block": "flatten", "shape": tuple(x.shape)})
        x = module(x)
        rows.append({"block": name, "shape": tuple(x.shape)})
    return rows


def feature_maps(model: nn.Module, x: torch.Tensor, layers: tuple[str, ...] = ("conv1", "layer1", "layer2", "layer3")):
    """Промежуточные активации (как CNN «видит» картинку) через forward-hooks."""
    model.eval()
    acts: dict[str, torch.Tensor] = {}
    handles = []
    for name in layers:
        module = dict(model.named_children())[name]
        handles.append(module.register_forward_hook(
            lambda mod, inp, out, name=name: acts.__setitem__(name, out.detach())))
    try:
        with torch.no_grad():
            model(x)
    finally:
        for h in handles:
            h.remove()
    return acts
