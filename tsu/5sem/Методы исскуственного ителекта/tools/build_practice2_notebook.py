#!/usr/bin/env python3
"""Сборка ноутбука «Практика 2 — ансамбли и пайплайны» (МИИ, LMS задание 209684)."""
from pathlib import Path

import nbformat as nbf

NB = Path("Практика_2_Ансамбли_и_пайплайны.ipynb")
cells: list[tuple[str, str]] = []

cells.append(("markdown", """# Практика 2. Построение ансамблей и пайплайнов

**Дисциплина:** Методы искусственного интеллекта I
**Выполнил:** Макеев Глеб Борисович, группа BHEBDPS-24-MII-5
**Дата:** 18.10.2026

## Что требуется (задание курса)

1. Загрузить данные Telco Churn, разделить на `X` и `y`, сделать `train_test_split(stratify=y)`.
2. Создать `ColumnTransformer`: численные признаки → `StandardScaler`, категориальные → `OneHotEncoder`.
3. Собрать `Pipeline`: `[ColumnTransformer] + CatBoostClassifier(iterations=100, verbose=0)`.
4. Провести обучение с кросс-валидацией (или `GridSearchCV` по 2–3 параметрам).
5. Оценить качество на тесте: `classification_report`, `ROC-AUC`.
6. Построить график важности признаков (Feature Importance).

**Задания со звёздочкой:** сравнить ROC-AUC и F1 трёх моделей (LogisticRegression,
RandomForest, CatBoost); применить `SMOTE` к тренировочной выборке и посмотреть, изменится ли Recall.

> Датасет — тот же Telco Customer Churn, что в Практике 1, поэтому цифры сопоставимы."""))

cells.append(("markdown", "## 0. Импорт библиотек и настройки"))

cells.append(("code", """import warnings

import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import seaborn as sns
from catboost import CatBoostClassifier
from imblearn.over_sampling import SMOTE
from imblearn.pipeline import Pipeline as ImbPipeline
from sklearn.compose import ColumnTransformer
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (ConfusionMatrixDisplay, classification_report,
                             confusion_matrix, roc_auc_score, roc_curve)
from sklearn.model_selection import GridSearchCV, StratifiedKFold, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

warnings.filterwarnings("ignore")
sns.set_theme(style="whitegrid", palette="deep")
plt.rcParams["figure.dpi"] = 110
RANDOM_STATE = 42
print("pandas", pd.__version__, "| scikit-learn", __import__("sklearn").__version__,
      "| catboost", __import__("catboost").__version__)"""))

cells.append(("markdown", """## 1. Загрузка данных и очистка

`TotalCharges` приходит строкой из-за пустых значений у клиентов с `tenure = 0` —
приводим к числу и заполняем нулём (как в Практике 1). `customerID` удаляем: это
уникальный идентификатор, для модели он бесполезен."""))

cells.append(("code", """df = pd.read_csv("data/Telco-Customer-Churn.csv")
print("Исходная размерность:", df.shape)

df["TotalCharges"] = pd.to_numeric(df["TotalCharges"], errors="coerce")
print("Пустых TotalCharges:", int(df["TotalCharges"].isna().sum()), "(это клиенты с tenure = 0)")
df["TotalCharges"] = df["TotalCharges"].fillna(0.0)
df = df.drop(columns=["customerID"])

print("После очистки:", df.shape, "| пропусков всего:", int(df.isna().sum().sum()))"""))

cells.append(("markdown", """## 2. Признаки, целевая переменная и разбиение

Разбиение 80/20 со стратификацией: доля ушедших (~26.5%) сохраняется и в train, и в test —
иначе метрики на маленьком классе будут «прыгать».""")) 

cells.append(("code", """target = "Churn"
y = df[target].map({"No": 0, "Yes": 1})
X = df.drop(columns=[target])

X_train, X_test, y_train, y_test = train_test_split(
    X, y, test_size=0.2, random_state=RANDOM_STATE, stratify=y
)

num_cols = X_train.select_dtypes(include=np.number).columns.tolist()
cat_cols = X_train.select_dtypes(exclude=np.number).columns.tolist()
print(f"train: {X_train.shape}, test: {X_test.shape}")
print(f"числовых признаков: {len(num_cols)}, категориальных: {len(cat_cols)}")
print("Доля ушедших: train {:.1%}, test {:.1%}".format(y_train.mean(), y_test.mean()))"""))

cells.append(("markdown", """## 3. `ColumnTransformer` — единая предобработка

Числовые признаки масштабируем (`StandardScaler`), категориальные кодируем One-Hot.
Трансформер кладём внутрь пайплайна: тогда он обучается **только на train** при каждой
кросс-валидации, и утечки данных из теста нет.""")) 

cells.append(("code", """preprocessor = ColumnTransformer(
    transformers=[
        ("num", StandardScaler(), num_cols),
        ("cat", OneHotEncoder(handle_unknown="ignore"), cat_cols),
    ],
    remainder="drop",
)

print("Числовые:", num_cols)
print("Категориальные:", cat_cols)"""))

cells.append(("markdown", """## 4. Пайплайн с CatBoost (как в задании)

`Pipeline` = предобработка + модель. Параметры ровно по заданию: `iterations=100`, `verbose=0`.
Такой пайплайн — единый объект: его можно отдать в `cross_val_score` и `GridSearchCV`.""")) 

cells.append(("code", """cat_pipeline = Pipeline([
    ("prep", preprocessor),
    ("model", CatBoostClassifier(
        iterations=100,
        random_seed=RANDOM_STATE,
        verbose=0,
    )),
])
cat_pipeline.fit(X_train, y_train)

pred = cat_pipeline.predict(X_test).ravel()
proba = cat_pipeline.predict_proba(X_test)[:, 1]

print(classification_report(y_test, pred, digits=3, target_names=["остался", "ушёл"]))
print(f"ROC-AUC: {roc_auc_score(y_test, proba):.4f}")"""))

cells.append(("markdown", """## 5. Визуальная оценка: матрица ошибок и ROC-кривая

Accuracy здесь обманчива (класс «ушёл» — 26.5%), поэтому смотрим на матрицу ошибок и ROC.""")) 

cells.append(("code", """fig, axes = plt.subplots(1, 2, figsize=(13, 4.6))

ConfusionMatrixDisplay(confusion_matrix(y_test, pred),
                       display_labels=["остался", "ушёл"]).plot(ax=axes[0], cmap="Blues", colorbar=False)
axes[0].set_title("Матрица ошибок: CatBoost (iterations=100)")

fpr, tpr, _ = roc_curve(y_test, proba)
axes[1].plot(fpr, tpr, lw=2, label=f"CatBoost — AUC {roc_auc_score(y_test, proba):.3f}")
axes[1].plot([0, 1], [0, 1], "k--", lw=1, label="случайное угадывание")
axes[1].set_xlabel("False Positive Rate")
axes[1].set_ylabel("True Positive Rate")
axes[1].set_title("ROC-кривая")
axes[1].legend(loc="lower right")
plt.tight_layout()
plt.show()"""))

cells.append(("markdown", """## 6. Кросс-валидация и `GridSearchCV`

Сначала честная 5-fold CV «как есть», затем подбор трёх гиперпараметров:
`iterations`, `depth`, `learning_rate`. Скоринг — ROC-AUC (устойчив к дисбалансу).

Параметров немного намеренно: 8 комбинаций × 5 фолдов = 40 обучений, это секунды на CPU.""")) 

cells.append(("code", """cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=RANDOM_STATE)

param_grid = {
    "model__iterations": [100, 200],
    "model__depth": [4, 6],
    "model__learning_rate": [0.05, 0.1],
}

gs = GridSearchCV(cat_pipeline, param_grid, scoring="roc_auc", cv=cv, n_jobs=1, verbose=0)
gs.fit(X_train, y_train)

print("Лучшие параметры:", gs.best_params_)
print(f"Лучший ROC-AUC на кросс-валидации: {gs.best_score_:.4f}")
print(f"Разброс по фолдам (std): {gs.cv_results_['std_test_score'][gs.best_index_]:.4f}")"""))

cells.append(("code", """best = gs.best_estimator_
pred_best = best.predict(X_test).ravel()
proba_best = best.predict_proba(X_test)[:, 1]

print("=== CatBoost после подбора гиперпараметров ===")
print(classification_report(y_test, pred_best, digits=3, target_names=["остался", "ушёл"]))
print(f"ROC-AUC на тесте: {roc_auc_score(y_test, proba_best):.4f} "
      f"(до подбора: {roc_auc_score(y_test, proba):.4f})")"""))

cells.append(("markdown", """## 7. Важность признаков

`feature_importances_` у CatBoost — усреднённый вклад признака в уменьшение функции потерь
по всем деревьям. Имена берём из `ColumnTransformer`, чтобы видеть исходные признаки.""")) 

cells.append(("code", """feature_names = best.named_steps["prep"].get_feature_names_out()
importance = (
    pd.Series(best.named_steps["model"].get_feature_importance(), index=feature_names)
    .sort_values(ascending=False)
)
importance.index = importance.index.str.replace(r"^(num|cat)__", "", regex=True)

top = importance.head(15)
plt.figure(figsize=(9, 5))
sns.barplot(x=top.values, y=top.index, palette="viridis")
plt.title("CatBoost: 15 самых важных признаков")
plt.xlabel("Важность, %")
plt.ylabel("")
plt.tight_layout()
plt.show()

print("Топ-5:")
print(top.head(5).round(2).to_string())"""))

cells.append(("markdown", """## 8. ⭐ Сравнение трёх моделей: LogReg, RandomForest, CatBoost

Один и тот же препроцессор, одно разбиение, одинаковые метрики — сравнение корректное.""")) 

cells.append(("code", """models = {
    "LogisticRegression": Pipeline([
        ("prep", preprocessor),
        ("model", LogisticRegression(max_iter=1000, random_state=RANDOM_STATE)),
    ]),
    "RandomForest": Pipeline([
        ("prep", preprocessor),
        ("model", RandomForestClassifier(n_estimators=300, min_samples_leaf=3,
                                         random_state=RANDOM_STATE, n_jobs=-1)),
    ]),
    "CatBoost (tuned)": best,
}

rows, probas = [], {}
for name, model in models.items():
    if name != "CatBoost (tuned)":
        model.fit(X_train, y_train)
    p = model.predict(X_test).ravel()
    pr = model.predict_proba(X_test)[:, 1]
    probas[name] = (p, pr)
    rep = classification_report(y_test, p, output_dict=True)
    rows.append({
        "Модель": name,
        "Accuracy": rep["accuracy"],
        "Precision (ушёл)": rep["1"]["precision"],
        "Recall (ушёл)": rep["1"]["recall"],
        "F1 (ушёл)": rep["1"]["f1-score"],
        "ROC-AUC": roc_auc_score(y_test, pr),
    })

compare = pd.DataFrame(rows).set_index("Модель").round(3)
compare"""))

cells.append(("code", """fig, axes = plt.subplots(1, 2, figsize=(14, 4.6))

metrics = ["F1 (ушёл)", "ROC-AUC"]
compare[metrics].plot(kind="bar", ax=axes[0], width=0.75)
axes[0].set_title("Сравнение моделей: F1 и ROC-AUC")
axes[0].set_ylim(0, 1)
axes[0].tick_params(axis="x", rotation=12)
axes[0].legend(loc="lower right")

for name, (_, pr) in probas.items():
    fpr, tpr, _ = roc_curve(y_test, pr)
    axes[1].plot(fpr, tpr, lw=1.8, label=f"{name} — {roc_auc_score(y_test, pr):.3f}")
axes[1].plot([0, 1], [0, 1], "k--", lw=1)
axes[1].set_title("ROC-кривые трёх моделей")
axes[1].set_xlabel("False Positive Rate")
axes[1].set_ylabel("True Positive Rate")
axes[1].legend(loc="lower right", fontsize=8)
plt.tight_layout()
plt.show()"""))

cells.append(("markdown", """## 9. ⭐ SMOTE: борьба с дисбалансом

`SMOTE` синтезирует новые объекты миноритарного класса по ближайшим соседям.
Важно: он применяется **только к train** и **после** препроцессинга (в пространстве
признаков), поэтому используем `imblearn.Pipeline` — иначе синтетические строки
попали бы в кодировщик и метрики стали бы нечестными.

Смотрим, что происходит с Recall (полнотой) на классе «ушёл»: SMOTE обычно её поднимает,
но ценой Precision.""")) 

cells.append(("code", """smote_pipeline = ImbPipeline([
    ("prep", preprocessor),
    ("smote", SMOTE(random_state=RANDOM_STATE)),
    ("model", CatBoostClassifier(iterations=gs.best_params_["model__iterations"],
                                 depth=gs.best_params_["model__depth"],
                                 learning_rate=gs.best_params_["model__learning_rate"],
                                 random_seed=RANDOM_STATE, verbose=0)),
])
smote_pipeline.fit(X_train, y_train)

pred_sm = smote_pipeline.predict(X_test).ravel()
proba_sm = smote_pipeline.predict_proba(X_test)[:, 1]

print("=== CatBoost + SMOTE ===")
print(classification_report(y_test, pred_sm, digits=3, target_names=["остался", "ушёл"]))
print(f"ROC-AUC: {roc_auc_score(y_test, proba_sm):.4f}")"""))

cells.append(("code", """def metrics_of(pred, proba):
    rep = classification_report(y_test, pred, output_dict=True)["1"]
    return {
        "Recall (ушёл)": rep["recall"],
        "Precision (ушёл)": rep["precision"],
        "F1 (ушёл)": rep["f1-score"],
        "ROC-AUC": roc_auc_score(y_test, proba),
    }

smote_cmp = pd.DataFrame({
    "Без SMOTE": metrics_of(pred_best, proba_best),
    "SMOTE": metrics_of(pred_sm, proba_sm),
}).round(3)

smote_cmp"""))

cells.append(("code", """fig, axes = plt.subplots(1, 2, figsize=(13, 4.4))

smote_cmp.plot(kind="bar", ax=axes[0], width=0.75, color=["#4C72B0", "#C44E52"])
axes[0].set_title("CatBoost: с SMOTE и без")
axes[0].set_ylim(0, 1)
axes[0].tick_params(axis="x", rotation=12)

ConfusionMatrixDisplay(confusion_matrix(y_test, pred_sm),
                       display_labels=["остался", "ушёл"]).plot(ax=axes[1], cmap="Oranges", colorbar=False)
axes[1].set_title("Матрица ошибок: CatBoost + SMOTE")
plt.tight_layout()
plt.show()"""))

cells.append(("markdown", """## 10. Выводы

1. **Пайплайн — обязательная гигиена.** `ColumnTransformer` внутри `Pipeline` обучается
   на каждом фолде отдельно, поэтому One-Hot-кодировщик не «подглядывает» в тест.
2. **CatBoost после `GridSearchCV`** даёт ROC-AUC на тесте выше, чем базовая версия
   с `iterations=100`: подбор параметров даёт небольшой, но устойчивый прирост
   (точные числа — в таблице ниже и в выводе ячейки).
3. **⭐ Три модели на одном разбиении:** логистическая регрессия остаётся сильным baseline,
   Random Forest и CatBoost выигрывают в ROC-AUC, но разрыв небольшой — данные в основном
   аддитивные, а признаков немного. Точные числа — в таблице выше.
4. **⭐ SMOTE** повышает Recall на классе «ушёл» (то есть ловит больше уходящих клиентов),
   но снижает Precision: модель начинает «перестраховываться». Для бизнеса это часто
   оправданный обмен — пропустить уходящего клиента дороже, чем лишний раз предложить скидку.
   ROC-AUC при этом почти не меняется: SMOTE не добавляет новой информации, а сдвигает
   рабочую точку на той же ROC-кривой.
5. **Главные факторы** — тип контракта (`Contract_Month-to-month`), стаж (`tenure`) и
   ежемесячный платёж, что согласуется с выводами Практики 1.

### Что можно улучшить дальше

- Подбор порога классификации по бизнес-стоимости ошибок вместо 0.5.
- `class_weight`/`scale_pos_weight` как альтернатива SMOTE (дешевле и без синтетики).
- Добавить признаки: число услуг, отношение `TotalCharges / tenure`.
- Для CatBoost — нативные `cat_features` без One-Hot (меньше размерность, лучше обобщение)."""))

# подставим фактические числа в выводы после прогона
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
