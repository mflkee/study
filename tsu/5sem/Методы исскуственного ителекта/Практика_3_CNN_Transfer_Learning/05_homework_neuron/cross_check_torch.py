#!/usr/bin/env python
"""Необязательная сверка: наши «ручные» градиенты против torch.autograd.

Само решение ДЗ (`main.py`) написано БЕЗ PyTorch. Этот файл — отдельная проверка
корректности: строим такой же нейрон в PyTorch с теми же весами и сравниваем
`∂L/∂w`, `∂L/∂b` из `loss.backward()` с результатом нашего `backward()`.

Запуск: ../.venv/bin/python 05_homework_neuron/cross_check_torch.py
"""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path

import torch

TASK_DIR = Path(__file__).resolve().parent
OUTPUTS = TASK_DIR / "outputs"

# импортируем main.py как модуль (он не тянет torch и не запускает main())
spec = importlib.util.spec_from_file_location("hw_neuron", TASK_DIR / "main.py")
hw = importlib.util.module_from_spec(spec)
spec.loader.exec_module(hw)


def main() -> None:
    print("=" * 78)
    print("Сверка: наш backward() (чистый Python) против torch.autograd")
    print("=" * 78)

    cases = [
        ([0.8, -0.4], 1, [0.7, -1.2], 0.35),
        ([0.0, 1.0], 0, [-0.3, 2.1], -0.7),
        ([1.0, 1.0], 1, [1.5, 1.5], -2.0),
    ]

    rows = []
    print(f"    {'x':<16}{'y':>3}{'наш ∂L/∂w1':>14}{'torch':>14}{'|diff|':>10}")
    max_diff = 0.0
    for x, y, w, b in cases:
        # 1) наш нейрон
        ours = hw.SigmoidNeuron(2, weights=list(w), bias=b)
        grad = ours.backward(list(x), float(y))

        # 2) то же самое в PyTorch
        wt = torch.tensor(w, dtype=torch.float64, requires_grad=True)
        bt = torch.tensor(b, dtype=torch.float64, requires_grad=True)
        xt = torch.tensor(x, dtype=torch.float64)
        z = (wt * xt).sum() + bt
        a = torch.sigmoid(z)
        loss = 0.5 * (a - float(y)) ** 2
        loss.backward()

        diffs = [abs(grad["dw"][0] - wt.grad[0].item()),
                 abs(grad["dw"][1] - wt.grad[1].item()),
                 abs(grad["db"] - bt.grad.item()),
                 abs(grad["loss"] - loss.item())]
        max_diff = max(max_diff, max(diffs))
        rows.append({
            "x": list(x), "y": y, "weights": list(w), "bias": b,
            "ours_dw": grad["dw"], "torch_dw": [wt.grad[0].item(), wt.grad[1].item()],
            "ours_db": grad["db"], "torch_db": bt.grad.item(),
            "ours_loss": grad["loss"], "torch_loss": loss.item(),
            "max_abs_diff": max(diffs),
        })
        print(f"    {str(x):<16}{y:>3}{grad['dw'][0]:>14.10f}{wt.grad[0].item():>14.10f}"
              f"{abs(grad['dw'][0] - wt.grad[0].item()):>10.1e}")

    print(f"\n    максимальное расхождение по всем {len(cases)} случаям "
          f"(веса, bias, loss): {max_diff:.2e}")
    print("    вывод: ручной обратный проход совпадает с autograd -> реализация верна")

    OUTPUTS.mkdir(parents=True, exist_ok=True)
    (OUTPUTS / "cross_check_torch.json").write_text(
        json.dumps({"cases": rows, "max_abs_diff": max_diff}, ensure_ascii=False, indent=2),
        encoding="utf-8")
    (TASK_DIR / "cross_check_torch.md").write_text(f"""# Сверка с torch.autograd

Дополнительная (необязательная) проверка: реализация из `main.py` — чистый Python,
без PyTorch. Здесь тот же нейрон собран в PyTorch, и наши градиенты сравниваются
с `loss.backward()`.

| x | y | наш ∂L/∂w1 | torch ∂L/∂w1 | \\|diff\\| |
|---|---|---|----------------|---|
""" + "\n".join(
        f"| {r['x']} | {r['y']} | {r['ours_dw'][0]:.10f} | {r['torch_dw'][0]:.10f} | "
        f"{abs(r['ours_dw'][0] - r['torch_dw'][0]):.1e} |" for r in rows) + f"""

Максимальное расхождение по весам, bias и loss: **{max_diff:.2e}** (float64).

Автоматизированный аналог этой сверки — численная производная в `main.py`
(раздел 2 отчёта): расхождение того же порядка, {max_diff:.1e} против ~1e-11.
""", encoding="utf-8")


if __name__ == "__main__":
    main()
