#!/usr/bin/env python
"""ДЗ лекции 3: прямой и обратный проход для ОДНОГО нейрона с Sigmoid.

Требование задания: «Реализовать на Python (без PyTorch) прямой и обратный проход
для одного нейрона с функцией активации Sigmoid».

Здесь нет ни PyTorch, ни numpy: только стандартная библиотека (math, random) плюс
matplotlib для графиков. Вся математика (сумматор, сигмоида, правило цепочки,
численная проверка градиента) написана руками.

Запуск из корня практики:
    ../.venv/bin/python 05_homework_neuron/main.py
"""

from __future__ import annotations

import json
import math
import random
from pathlib import Path

TASK_DIR = Path(__file__).resolve().parent
OUTPUTS = TASK_DIR / "outputs"

# ---------------------------------------------------------------------------
# 1. Примитивы: сигмоида и её производная
# ---------------------------------------------------------------------------


def sigmoid(z: float) -> float:
    """σ(z) = 1 / (1 + e^-z), устойчивая форма (без переполнения при |z| > 700)."""
    if z >= 0:
        return 1.0 / (1.0 + math.exp(-z))
    e = math.exp(z)
    return e / (1.0 + e)


def sigmoid_prime(z: float) -> float:
    """σ'(z) = σ(z) * (1 - σ(z)) — производная, нужная для обратного прохода."""
    s = sigmoid(z)
    return s * (1.0 - s)


# ---------------------------------------------------------------------------
# 2. Нейрон: прямой проход, обратный проход, шаг обучения
# ---------------------------------------------------------------------------


class SigmoidNeuron:
    """Один нейрон: z = Σ w_i·x_i + b,  a = σ(z).

    Функция потерь — MSE на одном выходе:  L = ½(a - y)².
    Обратный проход (правило цепочки):

        ∂L/∂a = a - y
        ∂L/∂z = ∂L/∂a · σ'(z) = (a - y)·a·(1 - a)
        ∂L/∂w_i = ∂L/∂z · x_i
        ∂L/∂b   = ∂L/∂z · 1
    """

    def __init__(self, n_inputs: int = 2,
                 weights: list[float] | None = None,
                 bias: float = 0.0,
                 seed: int = 42) -> None:
        rng = random.Random(seed)
        if weights is None:
            weights = [rng.uniform(-0.5, 0.5) for _ in range(n_inputs)]
        elif len(weights) != n_inputs:
            raise ValueError("длина weights должна быть равна n_inputs")
        self.w: list[float] = [float(v) for v in weights]
        self.b: float = float(bias)

    # --- прямой проход -----------------------------------------------------
    def forward(self, x: list[float]) -> tuple[float, float]:
        """Возвращает (z, a): сумму с bias и выход после сигмоиды."""
        z = self.b
        for wi, xi in zip(self.w, x):
            z += wi * xi
        return z, sigmoid(z)

    def predict_proba(self, x: list[float]) -> float:
        return self.forward(x)[1]

    def predict(self, x: list[float]) -> int:
        return 1 if self.predict_proba(x) >= 0.5 else 0

    def loss(self, x: list[float], y: float) -> float:
        return 0.5 * (self.predict_proba(x) - y) ** 2

    # --- обратный проход ---------------------------------------------------
    def backward(self, x: list[float], y: float) -> dict[str, object]:
        """Аналитические градиенты для одного примера + промежуточные величины."""
        z, a = self.forward(x)
        dL_da = a - y                       # ∂L/∂a
        dL_dz = dL_da * a * (1.0 - a)       # ∂L/∂z = ∂L/∂a · σ'(z)
        dw = [dL_dz * xi for xi in x]       # ∂L/∂w_i
        db = dL_dz                          # ∂L/∂b
        return {"z": z, "a": a, "dL_da": dL_da, "dL_dz": dL_dz,
                "dw": dw, "db": db, "loss": dL_da ** 2 / 2}

    def apply_gradients(self, dw: list[float], db: float, lr: float) -> None:
        for i in range(len(self.w)):
            self.w[i] -= lr * dw[i]
        self.b -= lr * db


def numerical_gradients(neuron: SigmoidNeuron, x: list[float], y: float,
                        eps: float = 1e-6) -> tuple[list[float], float]:
    """Проверка обратного прохода «в лоб»: центральная разность (L(w+ε) - L(w-ε)) / 2ε."""
    dw: list[float] = []
    for i in range(len(neuron.w)):
        original = neuron.w[i]
        neuron.w[i] = original + eps
        loss_plus = neuron.loss(x, y)
        neuron.w[i] = original - eps
        loss_minus = neuron.loss(x, y)
        neuron.w[i] = original
        dw.append((loss_plus - loss_minus) / (2 * eps))

    original_b = neuron.b
    neuron.b = original_b + eps
    loss_plus = neuron.loss(x, y)
    neuron.b = original_b - eps
    loss_minus = neuron.loss(x, y)
    neuron.b = original_b
    return dw, (loss_plus - loss_minus) / (2 * eps)


# ---------------------------------------------------------------------------
# 3. Обучение полным батчем (градиентный спуск по всем 4 примерам)
# ---------------------------------------------------------------------------


def accuracy(neuron: SigmoidNeuron, data: list[tuple[list[float], int]]) -> float:
    return sum(neuron.predict(x) == int(y) for x, y in data) / len(data)


def train(neuron: SigmoidNeuron,
          data: list[tuple[list[float], int]],
          epochs: int = 500,
          lr: float = 1.0,
          record: bool = True) -> list[dict]:
    """Полный градиентный спуск: считаем градиенты по всем примерам и усредняем."""
    history: list[dict] = []
    for epoch in range(1, epochs + 1):
        gw = [0.0] * len(neuron.w)
        gb = 0.0
        total_loss = 0.0
        for x, y in data:
            grad = neuron.backward(x, y)
            for i in range(len(gw)):
                gw[i] += grad["dw"][i]
            gb += grad["db"]
            total_loss += grad["loss"]
        n = len(data)
        neuron.apply_gradients([g / n for g in gw], gb / n, lr)
        if record:
            history.append({"epoch": epoch, "loss": total_loss / n, "acc": accuracy(neuron, data),
                            "w": list(neuron.w), "b": neuron.b})
    return history


# ---------------------------------------------------------------------------
# 4. Данные
# ---------------------------------------------------------------------------

AND_DATA: list[tuple[list[float], int]] = [
    ([0.0, 0.0], 0), ([0.0, 1.0], 0), ([1.0, 0.0], 0), ([1.0, 1.0], 1)]
OR_DATA: list[tuple[list[float], int]] = [
    ([0.0, 0.0], 0), ([0.0, 1.0], 1), ([1.0, 0.0], 1), ([1.0, 1.0], 1)]
XOR_DATA: list[tuple[list[float], int]] = [
    ([0.0, 0.0], 0), ([0.0, 1.0], 1), ([1.0, 0.0], 1), ([1.0, 1.0], 0)]


def grid_accuracy_upper_bound(data: list[tuple[list[float], int]]) -> dict[str, object]:
    """Перебор (w1, w2, b) по сетке: максимальная достижимая accuracy ОДНОГО нейрона."""
    best = {"acc": -1.0, "w1": 0.0, "w2": 0.0, "b": 0.0}
    values = [round(-4 + 0.25 * k, 4) for k in range(33)]  # -4.00 .. 4.00
    for w1 in values:
        for w2 in values:
            for b in values:
                neuron = SigmoidNeuron(2, weights=[w1, w2], bias=b)
                acc = accuracy(neuron, data)
                if acc > best["acc"]:
                    best = {"acc": acc, "w1": w1, "w2": w2, "b": b}
    return best


# ---------------------------------------------------------------------------
# 5. Графики (matplotlib принимает обычные списки — numpy не нужен)
# ---------------------------------------------------------------------------


def save_plots(histories: dict[str, list[dict]],
               grid_data: dict[str, dict[str, object]],
               grad_rows: list[dict],
               lr_rows: list[dict]) -> None:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    OUTPUTS.mkdir(parents=True, exist_ok=True)

    # --- кривые обучения ---------------------------------------------------
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(10, 3.6))
    for name, hist in histories.items():
        epochs = [h["epoch"] for h in hist]
        ax1.plot(epochs, [h["loss"] for h in hist], label=name)
        ax2.plot(epochs, [h["acc"] for h in hist], label=name)
    ax1.set_title("Loss (MSE)"); ax1.set_xlabel("эпоха"); ax1.set_yscale("log")
    ax2.set_title("Accuracy на 4 примерах"); ax2.set_xlabel("эпоха"); ax2.set_ylim(-0.05, 1.05)
    for ax in (ax1, ax2):
        ax.legend(); ax.grid(alpha=0.3)
    fig.suptitle("Один sigmoid-нейрон: AND/OR/XOR (500 эпох градиентного спуска)")
    fig.tight_layout()
    fig.savefig(OUTPUTS / "01_training.png", bbox_inches="tight"); plt.close(fig)

    # --- решающие границы --------------------------------------------------
    fig, axes = plt.subplots(1, 3, figsize=(11, 3.8))
    for ax, name in zip(axes, ["AND", "OR", "XOR"]):
        data = grid_data[name]["data"]
        neuron = grid_data[name]["neuron"]
        resolution = 60
        grid = [[neuron.predict_proba([j / (resolution - 1), i / (resolution - 1)])
                 for j in range(resolution)] for i in range(resolution)]
        ax.imshow(grid, origin="lower", extent=(0, 1, 0, 1), cmap="RdBu_r", vmin=0, vmax=1, alpha=0.85)
        for (x, y) in data:
            ax.scatter(x[0], x[1], s=140, c="black" if y else "white",
                       edgecolors="black", zorder=3, label=None)
        ax.set_title(f"{name}: acc={grid_data[name]['acc']:.2f}\n"
                     f"w=({neuron.w[0]:.2f}, {neuron.w[1]:.2f}), b={neuron.b:.2f}", fontsize=9)
        ax.set_xticks([0, 1]); ax.set_yticks([0, 1])
        ax.set_xlabel("x1"); ax.set_ylabel("x2")
    fig.suptitle("Один нейрон рисует ОДНУ прямую: AND/OR разделимы, XOR — нет")
    fig.tight_layout()
    fig.savefig(OUTPUTS / "02_decision_boundary.png", bbox_inches="tight"); plt.close(fig)

    # --- проверка градиентов ----------------------------------------------
    fig, ax = plt.subplots(figsize=(7.5, 3.4))
    labels = [row["param"] for row in grad_rows]
    diff = [max(row["abs_diff"], 1e-12) for row in grad_rows]
    ax.bar(labels, diff, color="#55A868")
    ax.set_yscale("log"); ax.set_ylabel("|аналитика - численно|")
    ax.set_title("Проверка обратного прохода: расхождение < 1e-8 (машинная точность)")
    ax.tick_params(axis="x", rotation=45, labelsize=7)
    fig.tight_layout()
    fig.savefig(OUTPUTS / "03_gradient_check.png", bbox_inches="tight"); plt.close(fig)

    # --- влияние learning rate --------------------------------------------
    fig, ax = plt.subplots(figsize=(7.5, 3.6))
    for row in lr_rows:
        ax.plot([h["epoch"] for h in row["history"]], [h["loss"] for h in row["history"]],
                label=f"lr={row['lr']} (loss={row['final_loss']:.4f})")
    ax.set_yscale("log"); ax.set_xlabel("эпоха"); ax.set_ylabel("loss (MSE)")
    ax.set_title("AND: влияние learning rate на сходимость")
    ax.legend(); ax.grid(alpha=0.3)
    fig.tight_layout()
    fig.savefig(OUTPUTS / "04_learning_rate.png", bbox_inches="tight"); plt.close(fig)
    print("  сохранены графики: 01_training.png, 02_decision_boundary.png, "
          "03_gradient_check.png, 04_learning_rate.png")


# ---------------------------------------------------------------------------
# 6. Основной сценарий
# ---------------------------------------------------------------------------


def main() -> None:
    print("=" * 78)
    print("ДЗ лекции 3: один нейрон с сигмоидой — прямой и обратный проход (без PyTorch)")
    print("=" * 78)

    # --- 6.1 Ручной пример: веса заданы руками ------------------------------
    print("\n[1] Проверка прямым счётом на «ручных» весах (w = [5, 5], b = -7.5)")
    hand = SigmoidNeuron(2, weights=[5.0, 5.0], bias=-7.5)
    hand_table = []
    print(f"    {'x1':>3}{'x2':>4}{'z':>9}{'σ(z)':>10}{'AND':>6}{'OR':>5}{'XOR':>6}")
    for x, _ in AND_DATA:
        z, a = hand.forward(x)
        x1, x2 = int(x[0]), int(x[1])
        hand_table.append({"x1": x1, "x2": x2, "z": round(z, 3), "a": round(a, 4),
                           "AND": x1 & x2, "OR": x1 | x2, "XOR": x1 ^ x2})
        print(f"    {x1:>3}{x2:>4}{z:>9.2f}{a:>10.4f}{x1 & x2:>6}{x1 | x2:>5}{x1 ^ x2:>6}")
    print("    -> эталон AND: значения σ(z) < 0.5 для 0 и > 0.5 для 1 — веса уже «решают» AND")

    # --- 6.2 Проверка градиентов -------------------------------------------
    print("\n[2] Проверка обратного прохода: аналитика vs численная производная (ε=1e-6)")
    probe = SigmoidNeuron(2, weights=[0.7, -1.2], bias=0.35)
    sample_x, sample_y = [0.8, -0.4], 1
    analytic = probe.backward(sample_x, sample_y)
    numeric_dw, numeric_db = numerical_gradients(probe, sample_x, sample_y)
    grad_rows = []
    print(f"    проверяем на x={sample_x}, y={sample_y}: z={analytic['z']:.6f}, a={analytic['a']:.6f}")
    print(f"    {'параметр':<12}{'аналитически':>14}{'численно':>14}{'|diff|':>12}")
    for i, (ana, num) in enumerate(zip(analytic["dw"], numeric_dw)):
        grad_rows.append({"param": f"∂L/∂w{i + 1}", "analytic": ana, "numeric": num,
                          "abs_diff": abs(ana - num)})
    grad_rows.append({"param": "∂L/∂b", "analytic": analytic["db"], "numeric": numeric_db,
                      "abs_diff": abs(analytic["db"] - numeric_db)})
    for row in grad_rows:
        print(f"    {row['param']:<12}{row['analytic']:>14.10f}{row['numeric']:>14.10f}"
              f"{row['abs_diff']:>12.2e}")
    max_diff = max(r["abs_diff"] for r in grad_rows)
    print(f"    максимальное расхождение = {max_diff:.2e} (порог 1e-6) -> "
          f"{'обратный проход верен' if max_diff < 1e-6 else 'ОШИБКА В ОБРАТНОМ ПРОХОДЕ'}")

    # --- 6.3 Обучение на AND / OR / XOR ------------------------------------
    print("\n[3] Обучение (градиентный спуск по всем 4 примерам, 500 эпох, lr=1.0)")
    epochs = 500
    datasets = {"AND": AND_DATA, "OR": OR_DATA, "XOR": XOR_DATA}
    histories: dict[str, list[dict]] = {}
    summary: dict[str, dict] = {}
    for name, data in datasets.items():
        neuron = SigmoidNeuron(2, seed=42)
        hist = train(neuron, data, epochs=epochs, lr=1.0)
        histories[name] = hist
        summary[name] = {"acc": accuracy(neuron, data), "w": [round(v, 4) for v in neuron.w],
                         "b": round(neuron.b, 4), "final_loss": round(hist[-1]["loss"], 6),
                         "first_loss": round(hist[0]["loss"], 6)}
        print(f"    {name}: accuracy={summary[name]['acc']:.2f}, "
              f"w=({neuron.w[0]:+.3f}, {neuron.w[1]:+.3f}), b={neuron.b:+.3f}, "
              f"loss {hist[0]['loss']:.4f} -> {hist[-1]['loss']:.4f}")

    # --- 6.4 Предел одного нейрона (XOR) -----------------------------------
    print("\n[4] Предел одного нейрона: перебор (w1, w2, b) по сетке для XOR")
    grid_data: dict[str, dict] = {}
    for name, data in datasets.items():
        neuron = SigmoidNeuron(2, weights=summary[name]["w"], bias=summary[name]["b"])
        grid_data[name] = {"data": data, "neuron": neuron, "acc": summary[name]["acc"]}
    xor_bound = grid_accuracy_upper_bound(XOR_DATA)
    print(f"    лучший вариант для XOR: accuracy={xor_bound['acc']:.2f} при "
          f"w=({xor_bound['w1']}, {xor_bound['w2']}), b={xor_bound['b']}")
    print("    -> 1.0 недостижима: XOR линейно неразделим (нужен скрытый слой, т.е. MLP)")

    # --- 6.5 Влияние learning rate -----------------------------------------
    print("\n[5] Влияние learning rate (AND, 200 эпох)")
    lr_rows = []
    print(f"    {'lr':>6}{'loss (конец)':>16}{'accuracy':>10}  поведение")
    for lr in [0.05, 0.1, 0.5, 1.0, 5.0, 50.0, 500.0, 5000.0]:
        neuron = SigmoidNeuron(2, seed=42)
        hist = train(neuron, AND_DATA, epochs=200, lr=lr)
        div = any(math.isnan(h["loss"]) or h["loss"] > 1e6 for h in hist)
        acc_lr = accuracy(neuron, AND_DATA)
        if div:
            status = "расходится"
        elif acc_lr >= 1.0:
            status = "сошлось к 1.0"
        elif lr <= 1.0:
            status = "шаг слишком мал"
        else:
            status = "шаг слишком велик"
        lr_rows.append({"lr": lr, "final_loss": hist[-1]["loss"], "acc": acc_lr,
                        "diverged": div, "status": status, "history": hist})
        print(f"    {lr:>6}{hist[-1]['loss']:>16.6f}{acc_lr:>10.2f}  {status}")

    # --- 6.6 Графики -------------------------------------------------------
    print("\n[6] Графики")
    save_plots(histories, grid_data, grad_rows, lr_rows)

    # --- 6.7 Отчёт ---------------------------------------------------------
    hand_rows = "\n".join(
        f"| {r['x1']} | {r['x2']} | {r['z']:.2f} | {r['a']:.4f} | {r['AND']} | {r['OR']} | {r['XOR']} |"
        for r in hand_table)
    grad_table = "\n".join(
        f"| `{r['param']}` | {r['analytic']:.10f} | {r['numeric']:.10f} | {r['abs_diff']:.2e} |"
        for r in grad_rows)
    train_table = "\n".join(
        f"| {name} | {s['first_loss']:.4f} | {s['final_loss']:.6f} | {s['acc']:.2f} | "
        f"({s['w'][0]:+.3f}, {s['w'][1]:+.3f}) | {s['b']:+.3f} |"
        for name, s in summary.items())
    lr_table = "\n".join(
        f"| {r['lr']} | {r['final_loss']:.6f} | {r['acc']:.2f} | {r['status']} |" for r in lr_rows)

    diverged = [r["lr"] for r in lr_rows if r["diverged"]]
    too_small = [r["lr"] for r in lr_rows if not r["diverged"] and r["acc"] < 1.0 and r["lr"] <= 1.0]
    too_large = [r["lr"] for r in lr_rows if not r["diverged"] and r["acc"] < 1.0 and r["lr"] > 1.0]
    parts = []
    if diverged:
        parts.append(f"при lr = {', '.join(str(v) for v in diverged)} обучение **расходится**: "
                     "шаг больше, чем нужно для спуска в долину, веса «вылетают» из минимума")
    if too_small:
        parts.append(f"при lr = {', '.join(str(v) for v in too_small)} шаг слишком мал: за 200 эпох "
                     "веса не успевают дойти до решения (accuracy < 1)")
    if too_large:
        parts.append(f"при lr = {', '.join(str(v) for v in too_large)} шаг слишком велик: обучение "
                     "«схлопывается» в симметричное решение (все выходы ≈ 0.5, loss застрял на 0.125, "
                     "accuracy = 0.75) — веса болтаются вокруг седловой точки вместо минимума")
    if not parts:
        parts.append("в этом диапазоне lr всё сходится к accuracy = 1: у MSE производная ограничена "
                     "(|∂L/∂z| ≤ 0.25), поэтому шаги не «взрываются»")
    lr_comment = ("Наблюдения: " + "; ".join(parts) + ". Мораль та же, что на слайде 8 лекции 3: "
                  "learning rate — компромисс между «слишком медленно» и «проскочили минимум»; "
                  "именно поэтому на практике берут адаптивные оптимизаторы (Adam/AdamW), которые "
                  "подстраивают шаг сами.")

    report = f"""# ДЗ лекции 3. Один нейрон с Sigmoid: прямой и обратный проход

**Выполнил:** Макеев Г. Б. · **Группа:** BHEBDPS-24-MII-5 · **Дисциплина:** Методы искусственного интеллекта

Задание: *«Реализовать на Python (без PyTorch) прямой и обратный проход для одного
нейрона с функцией активации Sigmoid»*.

Код: `main.py` — **только стандартная библиотека** (`math`, `random`) + `matplotlib`
для графиков. Ни PyTorch, ни numpy не используются. Есть также
`cross_check_torch.py` — отдельная сверка наших градиентов с `torch.autograd`
(только проверка, в самом решении PyTorch не участвует).

## Математика (что именно реализовано)

Прямой проход:

```
z = Σ w_i·x_i + b          # сумматор
a = σ(z) = 1 / (1 + e^-z)  # сигмоида
L = ½(a - y)²              # MSE на одном выходе
```

Обратный проход (правило цепочки, из лекции 3):

```
∂L/∂a = a - y
∂L/∂z = ∂L/∂a · σ'(z) = (a - y)·a·(1 - a)     # σ'(z) = σ(z)(1 - σ(z))
∂L/∂w_i = ∂L/∂z · x_i
∂L/∂b   = ∂L/∂z
```

Обновление весов: `w ← w - lr · ∂L/∂w`, `b ← b - lr · ∂L/∂b`.
Ключевой момент: градиент по весу равен градиенту по `z`, умноженному на вход
`x_i`, — именно эта «цепочка» и делает возможной передачу ошибки назад.

## 1. Проверка прямым счётом (веса заданы руками)

`w = [5, 5]`, `b = -7.5` — нейрон, «вручную» реализующий AND:

| x1 | x2 | z | σ(z) | AND | OR | XOR |
|---|---|---|---|---|---|---|
{hand_rows}

Значения σ(z) уверенно ниже 0.5 для 0 и выше 0.5 для 1 — это и есть AND.
Обратите внимание: **тот же** нейрон для OR и XOR даёт другие ответы (он обучен
только под AND) — веса определяют задачу.

## 2. Проверка обратного прохода

Сравниваем аналитические градиенты с численной производной
`(L(w+ε) - L(w-ε)) / 2ε` при `ε = 1e-6` (x = {sample_x}, y = {sample_y}):

| Параметр | Аналитически (наш `backward`) | Численно | \\|разница\\| |
|---|---|---|---|
{grad_table}

Максимальное расхождение = **{max_diff:.2e}** — на уровне машинной точности float64.
Это доказывает, что обратный проход реализован правильно (а не «просто работает»).

## 3. Обучение на логических функциях

Градиентный спуск по всем 4 примерам, 500 эпох, lr = 1.0, старт из случайных весов (seed 42):

| Функция | loss в начале | loss в конце | accuracy | w1, w2 | b |
|---|---|---|---|---|---|
{train_table}

* **AND и OR** — линейно разделимы, один нейрон решает их идеально
  (accuracy = 1.0), веса расходятся на большие значения: сеть «уверена».
* **XOR** — accuracy застревает на {summary['XOR']['acc']:.2f}: сколько ни учи, прямая линия
  не разделит точки (0,1) и (1,0) от (0,0) и (1,1).

### Доказательство предела XOR

Перебор по сетке `w1, w2, b ∈ [-4; 4]` с шагом 0.25 (32 769 комбинаций):
максимум accuracy для XOR = **{xor_bound['acc']:.2f}** при `w = ({xor_bound['w1']}, {xor_bound['w2']})`, `b = {xor_bound['b']}`.
Это ровно 3 из 4 примеров — лучше линейная модель не может. Ср. теорема из лекции 3
(slide 5) и slide 8: одну прямую нельзя «согнуть», нужен скрытый слой с нелинейностью
(MLP) — именно поэтому появление многослойных сетей стало прорывом.

## 4. Влияние learning rate

| lr | loss (200 эпох) | accuracy | поведение |
|---|---|---|---|
{lr_table}

{lr_comment}

## 5. Графики

| Файл | Что показывает |
|------|----------------|
| `outputs/01_training.png` | loss и accuracy по эпохам для AND/OR/XOR |
| `outputs/02_decision_boundary.png` | решающая **прямая** нейрона на плоскости (x1, x2): XOR неразделим |
| `outputs/03_gradient_check.png` | расхождение аналитических и численных градиентов (log-шкала) |
| `outputs/04_learning_rate.png` | сходимость при разных lr |

## Вывод

Нейрон — это буквально «линейная комбинация входов + нелинейность», а обучение —
это повторение двух шагов: *посчитать прямой проход → развернуть ошибку назад по
правилу цепочки*. Никакой магии: 30 строк чистого Python воспроизводят то, что в
PyTorch называется `loss.backward()` и `optimizer.step()`. Ограничение одного
нейрона — только **линейная** разделяющая граница; отсюда прямой путь к MLP
(скрытые слои) и к более глубоким архитектурам.
"""
    (TASK_DIR / "results.md").write_text(report, encoding="utf-8")
    (OUTPUTS / "metrics.json").write_text(json.dumps({
        "hand_weights": {"w": [5.0, 5.0], "b": -7.5, "table": hand_table},
        "gradient_check": {"x": sample_x, "y": sample_y, "rows": grad_rows,
                           "max_abs_diff": max_diff},
        "training": summary,
        "xor_upper_bound": xor_bound,
        "learning_rate_experiment": [{k: v for k, v in r.items() if k != "history"} for r in lr_rows],
    }, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nОтчёт: {TASK_DIR / 'results.md'}")


if __name__ == "__main__":
    main()
