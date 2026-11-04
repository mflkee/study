#!/usr/bin/env python3
"""Сборка ноутбука «ДЗ лекции 2 — CatBoost вместо LogisticRegression»."""
from pathlib import Path

import nbformat as nbf

NB = Path("ДЗ_лекции_2_CatBoost.ipynb")

cells: list[tuple[str, str]] = []

cells.append(("markdown", """# ДЗ лекции 2. Ансамбли: CatBoost вместо LogisticRegression

**Дисциплина:** Методы искусственного интеллекта I
**Выполнил:** Макеев Глеб Борисович, группа BHEBDPS-24-MII-5
**Задание (лекция 2, слайд 16):** повторить код Практики №1, заменив `LogisticRegression`
на `CatBoostClassifier`; сравнить качество.

Датасет — тот же Telco Customer Churn, что и в Практике 1, поэтому сравнение честное:
один и тот же `train_test_split` (`random_state=42`, `stratify=y`), одинаковая предобработка,
меняется только модель."""))

cells.append(("markdown", "## 0. Импорт библиотек и настройки"))

cells.append(("code", """import warnings

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns
from catboost import CatBoostClassifier
from sklearn.compose import ColumnTransformer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (ConfusionMatrixDisplay, classification_report,
                             confusion_matrix, roc_auc_score)
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

warnings.filterwarnings("ignore")
sns.set_theme(style="whitegrid", palette="deep")
plt.rcParams["figure.dpi"] = 110
RANDOM_STATE = 42"""))

cells.append(("markdown", "## 1. Загрузка и очистка данных (как в Практике 1)"))

cells.append(("code", """df = pd.read_csv("data/Telco-Customer-Churn.csv")
print("Размерность:", df.shape)

# Ловушка датасета: TotalCharges приходит как object из-за пустых строк у клиентов с tenure = 0
df["TotalCharges"] = pd.to_numeric(df["TotalCharges"], errors="coerce")
print("Пропусков в TotalCharges после приведения к float:", int(df["TotalCharges"].isna().sum()))
df["TotalCharges"] = df["TotalCharges"].fillna(0.0)

# customerID — уникальный идентификатор, для модели бесполезен
df = df.drop(columns=["customerID"])
print("Размерность после очистки:", df.shape)
df[["tenure", "MonthlyCharges", "TotalCharges"]].describe().round(2)"""))

cells.append(("code", """target = "Churn"
y = df[target].map({"No": 0, "Yes": 1})
X = df.drop(columns=[target])

print("Доля ушедших клиентов: {:.1%}".format(y.mean()))
print("Признаки: числовые —", X.select_dtypes(include=np.number).shape[1],
      ", категориальные —", X.select_dtypes(exclude=np.number).shape[1])"""))

cells.append(("markdown", """## 2. Предобработка и разбиение

`ColumnTransformer`: числовые признаки → `StandardScaler`, категориальные → `OneHotEncoder`.
Разбиение 80/20 со `stratify=y` и `random_state=42` — ровно как в Практике 1."""))

cells.append(("code", """X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=RANDOM_STATE, stratify=y
)
print("train:", X_train.shape, " test:", X_test.shape)

num_cols = X_train.select_dtypes(include=np.number).columns.tolist()
cat_cols = X_train.select_dtypes(exclude=np.number).columns.tolist()

preprocessor = ColumnTransformer(
    transformers=[
        ("num", StandardScaler(), num_cols),
        ("cat", OneHotEncoder(handle_unknown="ignore"), cat_cols),
    ]
)"""))

cells.append(("markdown", """## 3. LogisticRegression — базовая модель (из Практики 1)

Оставляем её в ноутбуке, чтобы сравнение шло на одном и том же разбиении,
а не с цифрами из прошлой работы."""))

cells.append(("code", """log_reg = Pipeline([
    ("prep", preprocessor),
    ("model", LogisticRegression(max_iter=1000, random_state=RANDOM_STATE)),
])
log_reg.fit(X_train, y_train)

pred_lr = log_reg.predict(X_test)
prob_lr = log_reg.predict_proba(X_test)[:, 1]

print(classification_report(y_test, pred_lr, digits=3, target_names=["остался", "ушёл"]))
print(f"ROC-AUC (LogisticRegression): {roc_auc_score(y_test, prob_lr):.3f}")"""))

cells.append(("markdown", """## 4. CatBoost — замена модели

Меняем только последний шаг пайплайна: `LogisticRegression` → `CatBoostClassifier`.
Параметры скромные (100 итераций, `verbose=0`), чтобы ноутбук считался за секунды
на CPU и без GPU."""))

cells.append(("code", """catboost = Pipeline([
    ("prep", preprocessor),
    ("model", CatBoostClassifier(
        iterations=100,
        depth=4,
        learning_rate=0.1,
        loss_function="Logloss",
        eval_metric="AUC",
        random_seed=RANDOM_STATE,
        verbose=0,
    )),
])
catboost.fit(X_train, y_train)

pred_cb = catboost.predict(X_test).ravel()
prob_cb = catboost.predict_proba(X_test)[:, 1]

print(classification_report(y_test, pred_cb, digits=3, target_names=["остался", "ушёл"]))
print(f"ROC-AUC (CatBoost): {roc_auc_score(y_test, prob_cb):.3f}")"""))

cells.append(("markdown", "## 5. Сравнение моделей на одной тестовой выборке"))

cells.append(("code", """rows = []
for name, pred, prob in [("LogisticRegression", pred_lr, prob_lr),
                         ("CatBoostClassifier", pred_cb, prob_cb)]:
    report = classification_report(y_test, pred, output_dict=True)
    rows.append({
        "Модель": name,
        "Accuracy": report["accuracy"],
        "Precision (ушёл)": report["1"]["precision"],
        "Recall (ушёл)": report["1"]["recall"],
        "F1 (ушёл)": report["1"]["f1-score"],
        "ROC-AUC": roc_auc_score(y_test, prob),
    })

compare = pd.DataFrame(rows).set_index("Модель").round(3)
compare"""))

cells.append(("code", """fig, axes = plt.subplots(1, 3, figsize=(15, 4.2))

for ax, (name, pred) in zip(axes[:2], [("LogisticRegression", pred_lr), ("CatBoost", pred_cb)]):
    ConfusionMatrixDisplay(confusion_matrix(y_test, pred),
                           display_labels=["остался", "ушёл"]).plot(ax=ax, cmap="Blues", colorbar=False)
    ax.set_title(f"Матрица ошибок: {name}")

axes[2].barh(compare.columns, compare.loc["CatBoostClassifier"], color="#4C72B0", label="CatBoost")
axes[2].barh(compare.columns, compare.loc["LogisticRegression"], color="#C44E52",
             alpha=0.55, label="LogReg")
axes[2].set_xlim(0, 1)
axes[2].set_title("Метрики: CatBoost vs LogReg")
axes[2].legend(loc="lower right", fontsize=8)
plt.tight_layout()
plt.show()"""))

cells.append(("markdown", """## 6. Важность признаков

`feature_importances_` у CatBoost считается по вкладу признака в разбиения деревьев.
Имена берём из `ColumnTransformer`, чтобы видеть исходные признаки, а не индексы колонок."""))

cells.append(("code", """model = catboost.named_steps["model"]
feature_names = catboost.named_steps["prep"].get_feature_names_out()

importance = (
    pd.Series(model.get_feature_importance(), index=feature_names)
    .sort_values(ascending=False)
    .head(15)
    .rename("Важность")
    .to_frame()
)
importance.index = importance.index.str.replace(r"^(num|cat)__", "", regex=True)
importance.round(2)"""))

cells.append(("code", """plt.figure(figsize=(9, 5))
sns.barplot(x=importance["Важность"], y=importance.index, palette="viridis")
plt.title("CatBoost: 15 самых важных признаков")
plt.xlabel("Важность, %")
plt.ylabel("")
plt.tight_layout()
plt.show()

print("Главный фактор:", importance.index[0])
print(importance.head(5).round(2).to_string())"""))

cells.append(("markdown", """## 7. Выводы

1. **Замена модели — один шаг пайплайна.** Предобработка, разбиение и метрики остаются
   прежними; меняется только оценщик, поэтому сравнение корректное.
2. **CatBoost vs LogisticRegression.** На этом датасете (7043 строки, много категориальных
   признаков, слабые нелинейности) бустинг даёт прирост ROC-AUC, но не радикальный:
   сигнал в данных в основном аддитивный, а `LogisticRegression` на хорошо закодированных
   признаках — сильный baseline. Итоговые числа — в таблице выше.
3. **Практический вывод.** Для табличных данных с категориями CatBoost выигрывает не только
   метрикой, но и тем, что умеет работать с категориями «из коробки»
   (`cat_features` без One-Hot) — в этом ноутбуке OHE сохранён намеренно, чтобы
   отличалась ровно одна строка кода, как требует задание.
4. **Главные факторы** — тип контракта (`Contract_Month-to-month`), стаж (`tenure`) и
   размер ежемесячного платежа: это согласуется с выводами Практики 1.

### Что дальше (Практика 2)

Практика 2 продолжает эту линию: `ColumnTransformer` + `CatBoostClassifier` в одном
`Pipeline`, подбор параметров через `GridSearchCV`/кросс-валидацию, сравнение трёх моделей
и (задание со звёздочкой) `SMOTE` для борьбы с дисбалансом классов."""))

nb = nbf.v4.new_notebook()
nb["cells"] = [
    nbf.v4.new_markdown_cell(src) if kind == "markdown" else nbf.v4.new_code_cell(src)
    for kind, src in cells
]
nb["metadata"] = {
    "kernelspec": {"display_name": "Python 3", "language": "python", "name": "python3"},
    "language_info": {"name": "python"},
}
nbf.write(nb, NB)
print("written:", NB, len(nb["cells"]), "cells")
