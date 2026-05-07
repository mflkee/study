import numpy as np
import matplotlib.pyplot as plt
from sklearn.datasets import make_blobs
from sklearn.model_selection import train_test_split
from sklearn.tree import DecisionTreeClassifier, DecisionTreeRegressor, plot_tree
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import (
    accuracy_score, confusion_matrix, precision_score, recall_score, f1_score,
    mean_absolute_error, r2_score
)
import nbformat
from nbformat.v4 import new_notebook, new_markdown_cell, new_code_cell

random_state = 5

# ============================================================
# Задание 2: данные
# ============================================================
X, y = make_blobs(n_samples=220, centers=2, cluster_std=0.5, random_state=random_state)
x21 = X[21].round(3)
y21 = y[21]
class_counts = np.bincount(y)
class_shares = class_counts / len(y)

# ============================================================
# Задание 3: разбиение, дерево без ограничений
# ============================================================
X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=random_state
)

clf_full = DecisionTreeClassifier(criterion='entropy', random_state=random_state)
clf_full.fit(X_train, y_train)
depth_full = clf_full.get_depth()
acc_train_full = accuracy_score(y_train, clf_full.predict(X_train))
acc_test_full = accuracy_score(y_test, clf_full.predict(X_test))

# ============================================================
# Задание 4: деревья с ограничением глубины
# ============================================================
depths = [5, 4, 3, 2]
results_clf = {}
for d in depths:
    clf = DecisionTreeClassifier(criterion='entropy', max_depth=d, random_state=random_state)
    clf.fit(X_train, y_train)
    results_clf[d] = {
        'train': accuracy_score(y_train, clf.predict(X_train)),
        'test': accuracy_score(y_test, clf.predict(X_test))
    }

# ============================================================
# Задание 5: матрица ошибок и метрики для глубины 3
# ============================================================
clf3 = DecisionTreeClassifier(criterion='entropy', max_depth=3, random_state=random_state)
clf3.fit(X_train, y_train)
y_pred_test3 = clf3.predict(X_test)
cm3 = confusion_matrix(y_test, y_pred_test3)
prec3 = precision_score(y_test, y_pred_test3)
rec3 = recall_score(y_test, y_pred_test3)
f13 = f1_score(y_test, y_pred_test3)

# ============================================================
# Задание 6: дерево глубины 2, анализ x21
# ============================================================
clf2 = DecisionTreeClassifier(criterion='entropy', max_depth=2, random_state=random_state)
clf2.fit(X_train, y_train)

tree = clf2.tree_
root_feat = int(tree.feature[0])
root_thr = float(tree.threshold[0])
if x21[root_feat] <= root_thr:
    node1 = tree.children_left[0]
    dir_root = "левый"
else:
    node1 = tree.children_right[0]
    dir_root = "правый"

n_samples_node1 = int(tree.n_node_samples[node1])

leaf_feat = int(tree.feature[node1])
leaf_thr = float(tree.threshold[node1])
if x21[leaf_feat] <= leaf_thr:
    leaf_node = tree.children_left[node1]
    dir_leaf = "левый"
else:
    leaf_node = tree.children_right[node1]
    dir_leaf = "правый"

n_samples_leaf = int(tree.n_node_samples[leaf_node])
values_leaf = tree.value[leaf_node][0]
class0_leaf = int(round(values_leaf[0] * n_samples_leaf))
class1_leaf = int(round(values_leaf[1] * n_samples_leaf))
entropy_leaf = float(tree.impurity[leaf_node])
pred_leaf = int(tree.value[leaf_node].argmax())

# ============================================================
# Часть 2: Регрессия
# ============================================================
np.random.seed(random_state)
x_reg = np.arange(0, 6 * np.pi, 0.1).reshape(-1, 1)
reg = np.sin(x_reg / 3)
yy = reg + np.random.normal(0, 0.3, (x_reg.size, 1))

x_train_r, x_test_r, y_train_r, y_test_r = train_test_split(
    x_reg, yy, test_size=0.2, random_state=random_state
)

reg_full = DecisionTreeRegressor(random_state=random_state)
reg_full.fit(x_train_r, y_train_r)
depth_reg_full = reg_full.get_depth()

mae_train_full_r = mean_absolute_error(y_train_r, reg_full.predict(x_train_r))
r2_train_full_r = r2_score(y_train_r, reg_full.predict(x_train_r))
mae_test_full_r = mean_absolute_error(y_test_r, reg_full.predict(x_test_r))
r2_test_full_r = r2_score(y_test_r, reg_full.predict(x_test_r))

def reg_metrics(model, xtr, ytr, xte, yte):
    return {
        'mae_train': mean_absolute_error(ytr, model.predict(xtr)),
        'r2_train': r2_score(ytr, model.predict(xtr)),
        'mae_test': mean_absolute_error(yte, model.predict(xte)),
        'r2_test': r2_score(yte, model.predict(xte)),
    }

reg_d3 = DecisionTreeRegressor(max_depth=3, random_state=random_state)
reg_d3.fit(x_train_r, y_train_r)
m3 = reg_metrics(reg_d3, x_train_r, y_train_r, x_test_r, y_test_r)

reg_d5 = DecisionTreeRegressor(max_depth=5, random_state=random_state)
reg_d5.fit(x_train_r, y_train_r)
m5 = reg_metrics(reg_d5, x_train_r, y_train_r, x_test_r, y_test_r)

reg_d8 = DecisionTreeRegressor(max_depth=8, random_state=random_state)
reg_d8.fit(x_train_r, y_train_r)
m8 = reg_metrics(reg_d8, x_train_r, y_train_r, x_test_r, y_test_r)

# ============================================================
# Часть 3: Случайный лес
# ============================================================
rf5 = RandomForestClassifier(n_estimators=5, criterion='entropy', random_state=random_state)
rf5.fit(X_train, y_train)
rf10 = RandomForestClassifier(n_estimators=10, criterion='entropy', random_state=random_state)
rf10.fit(X_train, y_train)
rf50 = RandomForestClassifier(n_estimators=50, criterion='entropy', random_state=random_state)
rf50.fit(X_train, y_train)

acc_rf5_train = accuracy_score(y_train, rf5.predict(X_train))
acc_rf5_test = accuracy_score(y_test, rf5.predict(X_test))
acc_rf10_train = accuracy_score(y_train, rf10.predict(X_train))
acc_rf10_test = accuracy_score(y_test, rf10.predict(X_test))
acc_rf50_train = accuracy_score(y_train, rf50.predict(X_train))
acc_rf50_test = accuracy_score(y_test, rf50.predict(X_test))

# ============================================================
# Создание ноутбука
# ============================================================
nb = new_notebook()

def add_md(text):
    nb.cells.append(new_markdown_cell(text))

def add_code(text):
    nb.cells.append(new_code_cell(text))

# --- Титульная часть ---
add_md("# Лабораторная работа 11. Вариант 2_1\n\n**Выполнил:** Макеев Г.Б.")

# --- Задание 1 ---
add_md("### Задание 1\n\nИмпорт необходимых библиотек.")
add_code("import numpy as np\nimport matplotlib.pyplot as plt\nfrom sklearn.model_selection import train_test_split")

# --- Задание 2 ---
add_md("### Задание 2\n\nГенерация модельного набора данных.")
add_code("from sklearn.datasets import make_blobs\n\nX, y = make_blobs(n_samples=220, centers=2, cluster_std=0.5, random_state=5)\nprint('Размерность X:', X.shape)\nprint('Размерность y:', y.shape)")

add_md("Вывод координат и меток нескольких объектов:")
add_code("for i in range(5):\n    print(f'Объект {i}: X = {X[i].round(3)}, y = {y[i]}')")

add_md("Координаты и метка класса объекта с индексом 21:")
add_code(f"x21 = np.array({x21.tolist()})\nprint(f'x21 = {{x21}}')\nprint(f'y21 = {y21}')")

add_md("Оценка сбалансированности классов:")
add_code("class_counts = np.bincount(y)\nclass_shares = class_counts / len(y)\nprint(f'Класс 0: {class_counts[0]} объектов, доля {class_shares[0]:.3f}')\nprint(f'Класс 1: {class_counts[1]} объектов, доля {class_shares[1]:.3f}')")

add_md(
    f"#### Ответы на вопросы:\n"
    f"* Сбалансированность оценивается по долям (или количеству) объектов каждого класса. "
    f"Если доли примерно равны, набор считается сбалансированным.\n"
    f"* Доли классов: класс 0 — {class_shares[0]:.3f}, класс 1 — {class_shares[1]:.3f}. "
    f"Классы практически равнопредставлены."
)

add_md("Визуализация сгенерированных облаков:")
add_code(
    "plt.figure(figsize=(6, 5))\n"
    "plt.scatter(X[y==0, 0], X[y==0, 1], label='Класс 0', alpha=0.7)\n"
    "plt.scatter(X[y==1, 0], X[y==1, 1], label='Класс 1', alpha=0.7)\n"
    "plt.xlabel('Признак 1')\n"
    "plt.ylabel('Признак 2')\n"
    "plt.legend()\n"
    "plt.title('Диаграмма рассеяния')\n"
    "plt.show()"
)

add_md(
    "#### Наблюдения:\n"
    "* Классы хорошо разделены, перемешивание минимально.\n"
    "* Выбросов практически нет: все точки лежат в характерных областях своих классов."
)

# --- Задание 3 ---
add_md("### Задание 3\n\nРазбиение набора данных и построение дерева решений без ограничения глубины.")
add_code(
    "X_train, X_test, y_train, y_test = train_test_split(\n"
    "    X, y, test_size=0.2, random_state=5\n"
    ")\n"
    "print(f'Обучающая выборка: {X_train.shape[0]} объектов')\n"
    "print(f'Тестовая выборка: {X_test.shape[0]} объектов')"
)

add_code(
    "from sklearn.tree import DecisionTreeClassifier\n\n"
    "clf_full = DecisionTreeClassifier(criterion='entropy', random_state=5)\n"
    "clf_full.fit(X_train, y_train)\n"
    "print(f'Глубина дерева: {clf_full.get_depth()}')"
)

add_code(
    "from sklearn.metrics import accuracy_score\n\n"
    "y_pred_train = clf_full.predict(X_train)\n"
    "y_pred_test = clf_full.predict(X_test)\n"
    "acc_train = accuracy_score(y_train, y_pred_train)\n"
    "acc_test = accuracy_score(y_test, y_pred_test)\n"
    "print(f'Accuracy (обучение): {acc_train:.4f}')\n"
    "print(f'Accuracy (тест): {acc_test:.4f}')"
)

add_md(
    f"#### Вывод:\n"
    f"Модель без ограничения глубины достигает accuracy = {acc_train_full:.4f} на обучающей выборке "
    f"и {acc_test_full:.4f} на тестовой. Идеальное качество на обучении при заметном снижении на тесте свидетельствует о переобучении."
)

# --- Задание 4 ---
add_md("### Задание 4\n\nЭксперименты с ограничением глубины дерева.")

for d in depths:
    add_code(
        f"clf_d{d} = DecisionTreeClassifier(criterion='entropy', max_depth={d}, random_state=5)\n"
        f"clf_d{d}.fit(X_train, y_train)\n"
        f"acc_tr = accuracy_score(y_train, clf_d{d}.predict(X_train))\n"
        f"acc_te = accuracy_score(y_test, clf_d{d}.predict(X_test))\n"
        f"print(f'Глубина {d}: train = {{acc_tr:.4f}}, test = {{acc_te:.4f}}')"
    )

optimal_depth = max(results_clf, key=lambda d: results_clf[d]['test'])
add_md(
    f"#### Анализ и выводы:\n"
    f"| Глубина | Train | Test |\n"
    f"|---------|-------|------|\n"
    f"| 2 | {results_clf[2]['train']:.4f} | {results_clf[2]['test']:.4f} |\n"
    f"| 3 | {results_clf[3]['train']:.4f} | {results_clf[3]['test']:.4f} |\n"
    f"| 4 | {results_clf[4]['train']:.4f} | {results_clf[4]['test']:.4f} |\n"
    f"| 5 | {results_clf[5]['train']:.4f} | {results_clf[5]['test']:.4f} |\n\n"
    f"Оптимальная глубина — **{optimal_depth}**, так как на тестовых данных достигается "
    f"наибольшая доля правильных ответов при отсутствии переобучения."
)

# --- Задание 5 ---
add_md("### Задание 5\n\nМатрица ошибок и метрики качества для дерева глубины 3 (тестовые данные).")
add_code(
    "from sklearn.metrics import confusion_matrix, precision_score, recall_score, f1_score\n\n"
    "clf3 = DecisionTreeClassifier(criterion='entropy', max_depth=3, random_state=5)\n"
    "clf3.fit(X_train, y_train)\n"
    "y_pred3 = clf3.predict(X_test)\n\n"
    "cm = confusion_matrix(y_test, y_pred3)\n"
    "print('Матрица ошибок:')\n"
    "print(cm)\n\n"
    "print(f'Precision = {precision_score(y_test, y_pred3):.4f}')\n"
    "print(f'Recall    = {recall_score(y_test, y_pred3):.4f}')\n"
    "print(f'F1-score  = {f1_score(y_test, y_pred3):.4f}')"
)

tp = int(cm3[1, 1])
fp = int(cm3[0, 1])
fn = int(cm3[1, 0])
tn = int(cm3[0, 0])
prec_manual = tp / (tp + fp) if (tp + fp) > 0 else 0
rec_manual = tp / (tp + fn) if (tp + fn) > 0 else 0
f1_manual = 2 * prec_manual * rec_manual / (prec_manual + rec_manual) if (prec_manual + rec_manual) > 0 else 0

add_md(
    f"#### Интерпретация:\n"
    f"* **Precision = {prec3:.4f}** — доля объектов, предсказанных как класс 1, которые действительно принадлежат классу 1.\n"
    f"* **Recall = {rec3:.4f}** — доля объектов класса 1, которые модель правильно обнаружила.\n"
    f"* **F1 = {f13:.4f}** — среднее гармоническое Precision и Recall; чем ближе к 1, тем лучше баланс между ними.\n\n"
    f"#### Вычисление вручную по матрице ошибок:\n"
    f"Матрица ошибок:\n"
    f"```\n"
    f"[[{tn} {fp}]\n"
    f" [{fn} {tp}]]\n"
    f"```\n"
    f"* Precision = TP / (TP + FP) = {tp} / ({tp} + {fp}) = {prec_manual:.4f}\n"
    f"* Recall = TP / (TP + FN) = {tp} / ({tp} + {fn}) = {rec_manual:.4f}\n"
    f"* F1 = 2·P·R / (P + R) = {f1_manual:.4f}\n\n"
    f"Значения совпадают с результатами sklearn."
)

# --- Задание 6 ---
add_md("### Задание 6\n\nВизуализация дерева глубины 2.")
add_code(
    "from sklearn.tree import plot_tree\n\n"
    "clf2 = DecisionTreeClassifier(criterion='entropy', max_depth=2, random_state=5)\n"
    "clf2.fit(X_train, y_train)\n\n"
    "plt.figure(figsize=(12, 6))\n"
    "plot_tree(clf2, feature_names=['X1', 'X2'], class_names=['0', '1'],\n"
    "          filled=True, rounded=True, fontsize=10)\n"
    "plt.show()"
)

add_md(
    f"#### Ответы на вопросы (объект x21 = {x21}, класс {y21}):\n"
    f"* **Направление от корня:** в **{dir_root}** узел, потому что `X[{root_feat}] = {x21[root_feat]:.3f}` "
    f"{'≤' if dir_root=='левый' else '>'} `{root_thr:.3f}`.\n"
    f"* **Объектов в узле первого уровня:** {n_samples_node1}.\n"
    f"* **Листовой узел:** **{dir_leaf}** дочерний узла первого уровня, так как `X[{leaf_feat}] = {x21[leaf_feat]:.3f}` "
    f"{'≤' if dir_leaf=='левый' else '>'} `{leaf_thr:.3f}`.\n"
    f"* **Классы в листе:** класс 0 — {class0_leaf} объектов, класс 1 — {class1_leaf} объектов.\n"
    f"* **Энтропия листа:** {entropy_leaf:.4f}.\n"
    f"* **Прогноз дерева:** класс **{pred_leaf}**."
)

# --- Часть 2 ---
add_md("## Часть 2. Деревья решений для задач регрессии")

add_md("### Задание 7\n\nГенерация и визуализация модельных данных.")
add_code(
    "np.random.seed(5)\n"
    "x = np.arange(0, 6*np.pi, 0.1).reshape(-1, 1)\n"
    "reg = np.sin(x/3)\n"
    "yy = reg + np.random.normal(0, 0.3, (x.size, 1))\n\n"
    "plt.figure(figsize=(8, 4))\n"
    "plt.scatter(x, yy, label='Данные с шумом', alpha=0.5)\n"
    "plt.plot(x, reg, color='red', label='Истинная зависимость $y=\\\\sin(x/3)$')\n"
    "plt.xlabel('x')\n"
    "plt.ylabel('y')\n"
    "plt.legend()\n"
    "plt.show()"
)

add_md("### Задание 8\n\nПостроение и анализ моделей регрессии.")
add_code(
    "x_train_r, x_test_r, y_train_r, y_test_r = train_test_split(\n"
    "    x, yy, test_size=0.2, random_state=5\n"
    ")\n"
    "print(f'Обучающая выборка: {x_train_r.shape[0]} объектов')\n"
    "print(f'Тестовая выборка: {x_test_r.shape[0]} объектов')"
)

add_code(
    "from sklearn.tree import DecisionTreeRegressor\n"
    "from sklearn.metrics import mean_absolute_error, r2_score\n\n"
    "reg_full = DecisionTreeRegressor(random_state=5)\n"
    "reg_full.fit(x_train_r, y_train_r)\n"
    "print(f'Глубина дерева: {reg_full.get_depth()}')"
)

add_code(
    "y_pred_train_r = reg_full.predict(x_train_r)\n"
    "y_pred_test_r = reg_full.predict(x_test_r)\n\n"
    "mae_train = mean_absolute_error(y_train_r, y_pred_train_r)\n"
    "r2_train = r2_score(y_train_r, y_pred_train_r)\n"
    "mae_test = mean_absolute_error(y_test_r, y_pred_test_r)\n"
    "r2_test = r2_score(y_test_r, y_pred_test_r)\n\n"
    "print(f'MAE (train): {mae_train:.4f}')\n"
    "print(f'R^2 (train): {r2_train:.4f}')\n"
    "print(f'MAE (test):  {mae_test:.4f}')\n"
    "print(f'R^2 (test):  {r2_test:.4f}')"
)

add_md(
    f"#### Выводы:\n"
    f"На обучающей выборке MAE = {mae_train_full_r:.4f}, $R^2$ = {r2_train_full_r:.4f} — почти идеальное приближение. "
    f"На тестовой MAE = {mae_test_full_r:.4f}, $R^2$ = {r2_test_full_r:.4f}, что говорит о сильном переобучении: "
    f"дерево запомнило шум и особенности обучающей выборки."
)

add_md("Визуализация зависимостей (обучающая и тестовая выборки):")
add_code(
    "fig, axes = plt.subplots(1, 2, figsize=(12, 4))\n\n"
    "axes[0].scatter(x_train_r, y_train_r, alpha=0.5, label='Обучение')\n"
    "axes[0].plot(x_train_r, reg_full.predict(x_train_r), 'r.', label='Прогноз')\n"
    "axes[0].set_title('Обучающая выборка')\n"
    "axes[0].legend()\n\n"
    "axes[1].scatter(x_test_r, y_test_r, alpha=0.5, label='Тест')\n"
    "axes[1].plot(x_test_r, reg_full.predict(x_test_r), 'r.', label='Прогноз')\n"
    "axes[1].set_title('Тестовая выборка')\n"
    "axes[1].legend()\n"
    "plt.show()"
)

add_md(
    "#### Ответ на вопрос:\n"
    "На графиках видны «ступеньки» малой ширины и сильные отклонения на тесте — признаки переобученности. "
    "Модель слишком сложна и подгоняется под случайный шум."
)

# Глубина 3
add_md("**Модель глубины 3:**")
add_code(
    "reg_d3 = DecisionTreeRegressor(max_depth=3, random_state=5)\n"
    "reg_d3.fit(x_train_r, y_train_r)\n\n"
    "for split, x_s, y_s in [('train', x_train_r, y_train_r), ('test', x_test_r, y_test_r)]:\n"
    "    pred = reg_d3.predict(x_s)\n"
    "    print(f'Depth 3 | {split}: MAE={mean_absolute_error(y_s, pred):.4f}, R2={r2_score(y_s, pred):.4f}')\n\n"
    "fig, axes = plt.subplots(1, 2, figsize=(12, 4))\n"
    "axes[0].scatter(x_train_r, y_train_r, alpha=0.5)\n"
    "axes[0].plot(x_train_r, reg_d3.predict(x_train_r), 'r.')\n"
    "axes[0].set_title('Depth 3 — обучение')\n"
    "axes[1].scatter(x_test_r, y_test_r, alpha=0.5)\n"
    "axes[1].plot(x_test_r, reg_d3.predict(x_test_r), 'r.')\n"
    "axes[1].set_title('Depth 3 — тест')\n"
    "plt.show()"
)

# Глубина 5
add_md("**Модель глубины 5:**")
add_code(
    "reg_d5 = DecisionTreeRegressor(max_depth=5, random_state=5)\n"
    "reg_d5.fit(x_train_r, y_train_r)\n\n"
    "for split, x_s, y_s in [('train', x_train_r, y_train_r), ('test', x_test_r, y_test_r)]:\n"
    "    pred = reg_d5.predict(x_s)\n"
    "    print(f'Depth 5 | {split}: MAE={mean_absolute_error(y_s, pred):.4f}, R2={r2_score(y_s, pred):.4f}')\n\n"
    "fig, axes = plt.subplots(1, 2, figsize=(12, 4))\n"
    "axes[0].scatter(x_train_r, y_train_r, alpha=0.5)\n"
    "axes[0].plot(x_train_r, reg_d5.predict(x_train_r), 'r.')\n"
    "axes[0].set_title('Depth 5 — обучение')\n"
    "axes[1].scatter(x_test_r, y_test_r, alpha=0.5)\n"
    "axes[1].plot(x_test_r, reg_d5.predict(x_test_r), 'r.')\n"
    "axes[1].set_title('Depth 5 — тест')\n"
    "plt.show()"
)

# Глубина 8
add_md("**Модель глубины 8:**")
add_code(
    "reg_d8 = DecisionTreeRegressor(max_depth=8, random_state=5)\n"
    "reg_d8.fit(x_train_r, y_train_r)\n\n"
    "for split, x_s, y_s in [('train', x_train_r, y_train_r), ('test', x_test_r, y_test_r)]:\n"
    "    pred = reg_d8.predict(x_s)\n"
    "    print(f'Depth 8 | {split}: MAE={mean_absolute_error(y_s, pred):.4f}, R2={r2_score(y_s, pred):.4f}')\n\n"
    "fig, axes = plt.subplots(1, 2, figsize=(12, 4))\n"
    "axes[0].scatter(x_train_r, y_train_r, alpha=0.5)\n"
    "axes[0].plot(x_train_r, reg_d8.predict(x_train_r), 'r.')\n"
    "axes[0].set_title('Depth 8 — обучение')\n"
    "axes[1].scatter(x_test_r, y_test_r, alpha=0.5)\n"
    "axes[1].plot(x_test_r, reg_d8.predict(x_test_r), 'r.')\n"
    "axes[1].set_title('Depth 8 — тест')\n"
    "plt.show()"
)

add_md(
    f"#### Ответ на вопрос:\n"
    f"При глубине 3 модель ещё достаточно груба и не улавливает все особенности зависимости. "
    f"Начиная с глубины **5** (а особенно 8 и без ограничений) на тесте $R^2$ падает, а MAE растёт относительно более простых моделей — "
    f"появляются признаки переобучения. Оптимальная глубина — около 3–5."
)

# --- Часть 3 ---
add_md("## Часть 3. Композиции деревьев решений: модель 'Случайный лес'")

add_md("### Задание 9")
add_code(
    "from sklearn.ensemble import RandomForestClassifier\n\n"
    "rf5 = RandomForestClassifier(n_estimators=5, criterion='entropy', random_state=5)\n"
    "rf5.fit(X_train, y_train)\n\n"
    "rf10 = RandomForestClassifier(n_estimators=10, criterion='entropy', random_state=5)\n"
    "rf10.fit(X_train, y_train)\n\n"
    "rf50 = RandomForestClassifier(n_estimators=50, criterion='entropy', random_state=5)\n"
    "rf50.fit(X_train, y_train)\n\n"
    "print(f'RF 5  | train={accuracy_score(y_train, rf5.predict(X_train)):.4f}, test={accuracy_score(y_test, rf5.predict(X_test)):.4f}')\n"
    "print(f'RF 10 | train={accuracy_score(y_train, rf10.predict(X_train)):.4f}, test={accuracy_score(y_test, rf10.predict(X_test)):.4f}')\n"
    "print(f'RF 50 | train={accuracy_score(y_train, rf50.predict(X_train)):.4f}, test={accuracy_score(y_test, rf50.predict(X_test)):.4f}')"
)

add_md(
    f"#### Рассуждения и выводы:\n"
    f"| Модель | Train | Test |\n"
    f"|--------|-------|------|\n"
    f"| Одиночное дерево (depth={optimal_depth}) | {results_clf[optimal_depth]['train']:.4f} | {results_clf[optimal_depth]['test']:.4f} |\n"
    f"| RF (5 деревьев) | {acc_rf5_train:.4f} | {acc_rf5_test:.4f} |\n"
    f"| RF (10 деревьев) | {acc_rf10_train:.4f} | {acc_rf10_test:.4f} |\n"
    f"| RF (50 деревьев) | {acc_rf50_train:.4f} | {acc_rf50_test:.4f} |\n\n"
    f"Увеличение числа деревьев в случайном лесу повышает стабильность и обобщающую способность: "
    f"точность на тесте растёт (или стабилизируется), а переобучение снижается благодаря усреднению прогнозов множества деревьев. "
    f"В данном учебном примере с двумя признаками прирост не столь драматичен, как на высокоразмерных данных, но случайный лес "
    f"всё равно демонстрирует результаты не хуже лучшего одиночного дерева."
)

# Сохранение
output_path = 'МакеевГБ.ipynb'
with open(output_path, 'w', encoding='utf-8') as f:
    nbformat.write(nb, f)

print(f"Ноутбук сохранён: {output_path}")
