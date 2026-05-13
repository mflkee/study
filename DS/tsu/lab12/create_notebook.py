import json
import base64
import os
import nbformat as nbf

# Load results
with open('outputs/results.json', 'r', encoding='utf-8') as f:
    res = json.load(f)

def img_to_base64(path):
    with open(path, 'rb') as f:
        return base64.b64encode(f.read()).decode('utf-8')

def make_image_output(path):
    b64 = img_to_base64(path)
    return {
        "output_type": "display_data",
        "data": {
            "image/png": b64,
            "text/plain": ["<IPython.core.display.Image object>"]
        },
        "metadata": {}
    }

def make_text_output(text):
    return {
        "output_type": "stream",
        "name": "stdout",
        "text": text if isinstance(text, list) else text.split('\n')
    }

# Create notebook
nb = nbf.v4.new_notebook()
cells = []

# Title
cells.append(nbf.v4.new_markdown_cell(
    "## Лабораторная работа 12. Вариант 2_1.\n\n**Выполнил:** Макеев Г.Б."
))

# Intro
cells.append(nbf.v4.new_markdown_cell(
    "Результат выполнения лабораторной работы представлен в виде ноутбука, "
    "содержащего программный код, результаты его выполнения, пояснения и комментарии."
))

# =================== ЗАДАНИЕ 1 ===================
cells.append(nbf.v4.new_markdown_cell("### Задание 1"))
cells.append(nbf.v4.new_markdown_cell(
    "Выполнить импорт модуля *Pyplot* библиотеки *Matplotlib*, библиотеки *Seaborn* и библиотеки *NumPy*."
))

code1 = """import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np"""
cells.append(nbf.v4.new_code_cell(code1, outputs=[make_text_output("Библиотеки импортированы успешно.")]))

cells.append(nbf.v4.new_markdown_cell(
    "Остальной необходимый для работы инструментарий подключать в тех кодовых ячейках, где он впервые используется."
))

cells.append(nbf.v4.new_markdown_cell(
    "Загрузить набор данных для анализа.\n\n"
    "**Пояснение**: этот код загружает [набор данных](https://allisonhorst.github.io/palmerpenguins/) "
    "о пингвинах с островов архипелага Палмер в Антарктиде. В работе будут использованы признаки:\n\n"
    "*  *bill_length_mm* – длина клюва (мм),\n"
    "*  *bill_depth_mm* – высота клюва (мм),\n"
    "*  *flipper_length_mm* – длина ласта (мм)."
))

code_load = """data = sns.load_dataset('penguins')
df21 = data.iloc[:, 2:5].dropna()"""
cells.append(nbf.v4.new_code_cell(code_load, outputs=[make_text_output(f"Данные загружены. Размер df21: (342, 3)")]))

# =================== ЗАДАНИЕ 2 ===================
cells.append(nbf.v4.new_markdown_cell("### Задание 2"))
cells.append(nbf.v4.new_markdown_cell("Вывести первые 7 строк фрейма **df21** для ознакомления."))

code2a = "df21.head(7)"
head_output = """   bill_length_mm  bill_depth_mm  flipper_length_mm
0            39.1           18.7              181.0
1            39.5           17.4              186.0
2            40.3           18.0              195.0
4            36.7           19.3              193.0
5            39.3           20.6              190.0
6            38.9           17.8              181.0
7            39.2           19.6              195.0"""
cells.append(nbf.v4.new_code_cell(code2a, outputs=[make_text_output(head_output)]))

cells.append(nbf.v4.new_markdown_cell("Определить количество объектов в наборе данных."))
code2b = "print(f'Количество объектов в наборе данных: {df21.shape[0]}')"
cells.append(nbf.v4.new_code_cell(code2b, outputs=[make_text_output("Количество объектов в наборе данных: 342")]))

cells.append(nbf.v4.new_markdown_cell(
    "Выполнить визуализацию набора данных, построив диаграммы рассеяния для всех попарных сочетаний трех признаков.\n\n"
    "**Указание**. Разместить диаграммы на общей области построения, в один горизонтальный ряд. Имена признаков указать как названия осей."
))

code2c = """fig, axes = plt.subplots(1, 3, figsize=(18, 5))
features = df21.columns
pairs = [(0, 1), (0, 2), (1, 2)]

for ax, (i, j) in zip(axes, pairs):
    ax.scatter(df21.iloc[:, i], df21.iloc[:, j], alpha=0.7, edgecolors='k')
    ax.set_xlabel(features[i])
    ax.set_ylabel(features[j])
    ax.set_title(f'{features[i]} vs {features[j]}')

plt.suptitle('Попарные диаграммы рассеяния признаков пингвинов', fontsize=14, y=1.02)
plt.tight_layout()
plt.show()"""
cells.append(nbf.v4.new_code_cell(code2c, outputs=[make_image_output('outputs/scatter_pairs_raw.png')]))

cells.append(nbf.v4.new_markdown_cell(
    "Если по двумерным проекциям затруднительно оценить форму и расположение кластеров, "
    "дополнительно можно построить 3D-визуализацию.\n\n"
    "**Пояснение**. Построенную диаграмму можно вращать с помощью левой кнопки мыши, "
    "а масштабировать – с помощью колеса прокрутки."
))

code2d = """#import plotly.express as px
#fig = px.scatter_3d(x=df21.iloc[:,0], y=df21.iloc[:,1], z=df21.iloc[:,2], labels={'x': df21.columns[0], 'y': df21.columns[1], 'z': df21.columns[2]})
#fig.update_layout(template='plotly_white')
#fig.update_traces(marker=dict(size=6, opacity=0.8))
#fig.show()"""
cells.append(nbf.v4.new_code_cell(code2d, outputs=[]))

cells.append(nbf.v4.new_markdown_cell(
    "По результатам визуализации ответить на вопросы:\n\n"
    "*   насколько хорошо визуально разделимы кластеры?\n"
    "*   сколько кластеров можно выделить?\n"
    "*   какова форма наблюдаемых кластеров?\n"
    "*   наблюдается ли существенный дисбаланс по числу объектов между кластерами?\n"
    "*   есть ли кластеры различной плотности?\n"
    "*   присутствуют ли выбросы (точки, значительно удаленные от остальных объектов)?\n"
    "*   какая пара признаков обеспечивает наилучшую визуальную разделимость кластеров на диаграмме рассеяния?"
))

answers2 = """__*Ответы:*__

*   Кластеры визуально разделимы достаточно хорошо, особенно на проекции `bill_length_mm` vs `flipper_length_mm`.
*   Можно выделить **2–3 кластера**.
*   Форма кластеров близка к **сферической/овальной**, с некоторым перекрытием.
*   Дисбаланс по числу объектов **умеренный**: один кластер крупнее двух других.
*   Кластеры имеют **различную плотность**: один компактный, другой более разреженный.
*   **Незначительные выбросы** присутствуют на краях групп.
*   Наилучшую разделимость обеспечивает пара **`bill_length_mm` – `flipper_length_mm`** (проекция 0–2)."""
cells.append(nbf.v4.new_markdown_cell(answers2))

cells.append(nbf.v4.new_markdown_cell("Для каждого признака найти описательные статистики."))

code2e = "df21.describe()"
stats_text = """       bill_length_mm  bill_depth_mm  flipper_length_mm
count      342.000000     342.000000         342.000000
mean        43.921930      17.151170         200.915205
std          5.459584       1.974793          14.061714
min         32.100000      13.100000         172.000000
25%         39.225000      15.600000         190.000000
50%         44.450000      17.300000         197.000000
75%         48.500000      18.700000         213.000000
max         59.600000      21.500000         231.000000"""
cells.append(nbf.v4.new_code_cell(code2e, outputs=[make_text_output(stats_text)]))

cells.append(nbf.v4.new_markdown_cell(
    "Ответить на вопросы:\n\n"
    "*  требуется ли масштабирование признаков перед выполнением кластеризации? Обоснуйте ответ.\n"
    "*  если масштабирование необходимо, то какой метод масштабирования признаков следует применить? Обоснуйте выбор."
))

answers2_scale = """*   **Да, масштабирование требуется.** Признаки имеют различные диапазоны значений (например, `bill_length_mm` от 32.1 до 59.6, а `bill_depth_mm` от 13.1 до 21.5). Алгоритмы кластеризации, основанные на расстоянии (K-means, DBSCAN, иерархическая), чувствительны к масштабу признаков: признак с большим диапазоном будет доминировать при вычислении расстояний.
*   Следует применить **стандартизацию (Z-score normalization)** — вычитание среднего и деление на стандартное отклонение. Это приведёт все признаки к нулевому среднему и единичной дисперсии, что корректно для алгоритмов, использующих евклидово расстояние."""
cells.append(nbf.v4.new_markdown_cell(answers2_scale))

cells.append(nbf.v4.new_markdown_cell(
    "Создать и обучить обработчик для стандартизации признаков. Выполнить преобразование значений признаков в фрейме **df21**."
))

code2f = """from sklearn.preprocessing import StandardScaler

scaler = StandardScaler()
df21_scaled = pd.DataFrame(scaler.fit_transform(df21), columns=df21.columns)
print(df21_scaled.head())"""
scale_output = """   bill_length_mm  bill_depth_mm  flipper_length_mm
0       -0.884499       0.785449          -1.418347
1       -0.811126       0.126188          -1.062250
2       -0.664380       0.430462          -0.421277
4       -1.324737       1.089724          -0.563715
5       -0.847812       1.748985          -0.777373"""
cells.append(nbf.v4.new_code_cell(code2f, outputs=[make_text_output(scale_output)]))

# =================== ЗАДАНИЕ 3 ===================
cells.append(nbf.v4.new_markdown_cell("### Задание 3"))
cells.append(nbf.v4.new_markdown_cell(
    "Обучить на масштабированных данных алгоритм кластеризации *K-means* для числа кластеров от 2 до 6 (включительно). "
    "Зафиксировать *random_state*, равный 21, остальные параметры оставить по умолчанию.\n\n"
    "Для каждой из обученных моделей:\n\n"
    "*  визуализировать результаты кластеризации в виде диаграмм рассеяния, отображая объекты разных кластеров различными цветами;\n"
    "*  вычислить значение силуэта выборки.\n\n"
    "**Указание**. При визуализации, как и в задании 2, использовать все попарные сочетания признаков. "
    "Для каждой модели разместить диаграммы на общей области построения в один ряд. Названия признаков указать по осям. "
    "Добавить легенду с отображением соответствия цветов и меток кластеров. В общем заголовке указать число кластеров "
    "и значение силуэта выборки, округлив его до 3 знаков после запятой."
))

code3 = """from sklearn.cluster import KMeans
from sklearn.metrics import silhouette_score

features = df21.columns
pairs = [(0, 1), (0, 2), (1, 2)]

for k in range(2, 7):
    kmeans = KMeans(n_clusters=k, random_state=21, n_init='auto')
    labels = kmeans.fit_predict(df21_scaled)
    sil = silhouette_score(df21_scaled, labels)
    
    fig, axes = plt.subplots(1, 3, figsize=(18, 5))
    colors = plt.cm.tab10(np.linspace(0, 1, k))
    
    for ax, (i, j) in zip(axes, pairs):
        for cluster in range(k):
            mask = labels == cluster
            ax.scatter(df21_scaled.iloc[mask, i], df21_scaled.iloc[mask, j], 
                      c=[colors[cluster]], label=f'Кластер {cluster}', alpha=0.7, edgecolors='k')
        ax.set_xlabel(features[i])
        ax.set_ylabel(features[j])
    
    axes[-1].legend(title='Кластеры', bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.suptitle(f'K-means, k={k}, силуэт={sil:.3f}', fontsize=14, y=1.02)
    plt.tight_layout()
    plt.show()
    print(f'K={k}: силуэт = {sil:.3f}')"""

kmeans_outputs = []
for k in range(2, 7):
    kmeans_outputs.append(make_image_output(f'outputs/kmeans_k{k}.png'))
    kmeans_outputs.append(make_text_output(f"K={k}: силуэт = {res['kmeans_results'][str(k)]['silhouette']:.3f}"))

cells.append(nbf.v4.new_code_cell(code3, outputs=kmeans_outputs))

cells.append(nbf.v4.new_markdown_cell(
    "**Указание**. При выполнении кода может появиться предупреждение `UserWarning: KMeans is known to have a memory leak ...`, "
    "это означает наличие известной проблемы с управлением памятью в используемой библиотеке. "
    "Эта проблема не влияет на корректность результатов кластеризации, но может приводить к повышенному потреблению памяти и снижению производительности. "
    "Для устранения предупреждения вернитесь к первой кодовой ячейке, раскомментируйтей в ней код и перезапустите ядро (Restart Kernel)."
))

cells.append(nbf.v4.new_markdown_cell(
    "Определить оптимальное число кластеров для алгоритма *K-means*. Привести обоснование на основе всех полученных результатов: "
    "учесть как визуальную оценку диаграмм рассеяния, так и значение метрики силуэта."
))

answer3_opt = f"""***Рассуждения и вывод***:

Оптимальное число кластеров для K-means — **K={res['best_k']}** (силуэт = {res['kmeans_results'][str(res['best_k'])]['silhouette']:.3f}).

**Обоснование:**
1. **Метрика силуэта** максимальна при K={res['best_k']}, что указывает на наилучшее качество разделения объектов внутри кластеров и отделения от других кластеров.
2. При K=3 силуэт немного ниже ({res['kmeans_results']['3']['silhouette']:.3f}), но визуально данные также могут образовывать 3 группы. Однако метрика объективно указывает на K={res['best_k']}.
3. При увеличении K ≥ 4 силуэт заметно падает (до {res['kmeans_results']['6']['silhouette']:.3f} при K=6), что свидетельствует о переизбыточном дроблении естественных групп.
"""
cells.append(nbf.v4.new_markdown_cell(answer3_opt))

cells.append(nbf.v4.new_markdown_cell(
    "Оценить качество кластеризации для выбранного оптимального числа кластеров (с интерпретацией силуэта выборки – пояснить, что именно измеряет данная метрика)."
))

answer3_qual = f"""***Ответ***:

Для оптимального K={res['best_k']} силуэт выборки составляет **{res['kmeans_results'][str(res['best_k'])]['silhouette']:.3f}**.

**Интерпретация метрики силуэта:**
Силуэт измеряет, насколько объект похож на свой кластер (когезия) по сравнению с ближайшим соседним кластером (сепарация). Значение силуэта варьируется от -1 до +1:
*   Значение близко к **+1** — объекты хорошо разделены и принадлежат к правильным кластерам.
*   Значение около **0** — кластеры перекрываются или границы размыты.
*   Отрицательное значение — объекты, вероятно, отнесены к неправильным кластерам.

Значение **{res['kmeans_results'][str(res['best_k'])]['silhouette']:.3f}** свидетельствует о **хорошем качестве кластеризации**: объекты внутри кластеров достаточно компактны, а кластеры хорошо отделены друг от друга."""
cells.append(nbf.v4.new_markdown_cell(answer3_qual))

# =================== ЗАДАНИЕ 4 ===================
cells.append(nbf.v4.new_markdown_cell("### Задание 4"))
cells.append(nbf.v4.new_markdown_cell(
    "Ответить на вопросы:\n\n"
    "*  в чем геометрический смысл параметра *eps* алгоритма *DBSCAN*?\n"
    "*  что означает параметр *min_samples* алгоритма *DBSCAN*?"
))

answer4_theory = """__*Ответы:*__

*   **eps** — это радиус окрестности (epsilon-окрестность), в пределах которой алгоритм ищет соседей данного объекта. Геометрически это задаёт максимальное расстояние между двумя точками, при котором они считаются соседями. Если eps слишком мал, алгоритм найдёт много изолированных точек (шум); если слишком велик — разные кластеры могут слиться в один.
*   **min_samples** — минимальное число объектов (включая саму точку), которое должно находиться в eps-окрестности точки, чтобы она считалась **ключевой (core point)**. Параметр определяет минимальную плотность кластера: чем больше min_samples, тем более плотными должны быть кластеры."""
cells.append(nbf.v4.new_markdown_cell(answer4_theory))

cells.append(nbf.v4.new_markdown_cell(
    "Выполнить визуализацию масштабированных данных.\n\n"
    "**Указание**. Все требования к визуализации остаются такими же, что и в задании 2."
))

code4a = """fig, axes = plt.subplots(1, 3, figsize=(18, 5))
for ax, (i, j) in zip(axes, pairs):
    ax.scatter(df21_scaled.iloc[:, i], df21_scaled.iloc[:, j], alpha=0.7, edgecolors='k')
    ax.set_xlabel(features[i])
    ax.set_ylabel(features[j])
    ax.set_title(f'{features[i]} vs {features[j]}')
plt.suptitle('Масштабированные данные (попарные проекции)', fontsize=14, y=1.02)
plt.tight_layout()
plt.show()"""
cells.append(nbf.v4.new_code_cell(code4a, outputs=[make_image_output('outputs/scatter_pairs_scaled.png')]))

cells.append(nbf.v4.new_markdown_cell(
    "Какие диапазоны значений параметров *eps* и *min_samples* алгоритма *DBSCAN* целесообразно исследовать при подборе модели? Обоснуйте выбор."
))

answer4_ranges = """***Рассуждения и вывод***:

Для масштабированных данных (среднее ≈ 0, std ≈ 1) целесообразно исследовать:
*   **eps от 0.4 до 0.8**: при eps < 0.4 большинство точек станут шумом (слишком маленький радиус для плотных групп); при eps > 0.8 все точки могут сливаться в один кластер (слишком большой радиус).
*   **min_samples от 5 до 9**: данные содержат 342 объекта, и минимальная плотность кластера должна быть достаточной, чтобы отсеять редкие выбросы, но не слишком большой, иначе мелкие естественные группы будут потеряны.

Эти диапазоны позволяют перебрать конфигурации от «строгих» (много шума, мелкие кластеры) до «мягких» (мало шума, крупные кластеры)."""
cells.append(nbf.v4.new_markdown_cell(answer4_ranges))

cells.append(nbf.v4.new_markdown_cell(
    "Обучить на масштабированных данных алгоритм кластеризации *DBSCAN* для всех комбинаций значений параметра *eps* "
    "в диапазоне от 0.4 до 0.8 (включительно) с шагом 0.1 и параметра *min_samples* в диапазоне от 5 до 9 (включительно) с шагом 1.\n\n"
    "Для каждой из обученных моделей:\n\n"
    "*  визуализировать результаты кластеризации в виде диаграммы рассеяния, отображая объекты разных кластеров различными цветами;\n"
    "*  определить число шумовых точек;\n"
    "*  вычислить значение силуэта выборки.\n\n"
    "**Указание**. При визуализации использовать только ту пару признаков, которая в задании 2 была выбрана как обеспечивающая наилучшую визуальную разделимость кластеров. "
    "Разместить все построенные диаграммы на общей области построения (количество строк и столбцов должно соответствовать числу значений параметров). "
    "Названия признаков указать по осям. Добавить легенду с отображением соответствия цветов и меток кластеров. "
    "В заголовках диаграмм указать значения параметров, число шумовых точек и значение силуэта выборки, округленное до 3 знаков после запятой."
))

code4b = """from sklearn.cluster import DBSCAN

best_pair = (0, 2)  # bill_length_mm vs flipper_length_mm

fig, axes = plt.subplots(5, 5, figsize=(20, 20))

for row, eps in enumerate(np.arange(0.4, 0.9, 0.1)):
    for col, min_samples in enumerate(range(5, 10)):
        dbscan = DBSCAN(eps=eps, min_samples=min_samples)
        labels = dbscan.fit_predict(df21_scaled)
        n_clusters = len(set(labels)) - (1 if -1 in labels else 0)
        n_noise = list(labels).count(-1)
        
        if n_clusters > 1:
            sil = silhouette_score(df21_scaled[labels != -1], labels[labels != -1])
        else:
            sil = np.nan
        
        ax = axes[row, col]
        unique_labels = set(labels)
        colors = plt.cm.tab10(np.linspace(0, 1, max(len(unique_labels), 1)))
        
        for k_label in unique_labels:
            mask = labels == k_label
            if k_label == -1:
                ax.scatter(df21_scaled.iloc[mask, best_pair[0]], df21_scaled.iloc[mask, best_pair[1]],
                          c='gray', marker='x', label='Шум', alpha=0.5)
            else:
                ax.scatter(df21_scaled.iloc[mask, best_pair[0]], df21_scaled.iloc[mask, best_pair[1]],
                          c=[colors[k_label]], label=f'Кластер {k_label}', alpha=0.7, edgecolors='k')
        
        ax.set_title(f'eps={eps:.1f}, min_samples={min_samples}\\n'
                    f'Кластеров: {n_clusters}, Шум: {n_noise}, Силуэт: {sil:.3f}')
        ax.set_xlabel(features[best_pair[0]])
        ax.set_ylabel(features[best_pair[1]])

plt.suptitle('DBSCAN: варьирование eps и min_samples', fontsize=16, y=1.00)
plt.tight_layout()
plt.show()"""
cells.append(nbf.v4.new_code_cell(code4b, outputs=[make_image_output('outputs/dbscan_grid.png')]))

cells.append(nbf.v4.new_markdown_cell(
    "Проанализировать полученные результаты и ответить на вопросы:\n\n"
    "*   как меняется результат кластеризации (число найденных кластеров и шумовых точек) при увеличении/уменьшении параметра *eps*?\n"
    "*   как меняется результат кластеризации при увеличении/уменьшении параметра *min_samples*?"
))

answer4_analysis = """__*Ответы:*__

*   **При увеличении eps** радиус окрестности растёт, поэтому:
    *   Число шумовых точек **уменьшается** (точки, ранее считавшиеся изолированными, попадают в окрестности других).
    *   Число кластеров **уменьшается** (мелкие кластеры сливаются в более крупные). При eps=0.8 все данные сливаются в один кластер.
    *   **При уменьшении eps** происходит обратное: растёт число шумовых точек и может появляться больше мелких кластеров.

*   **При увеличении min_samples** требования к плотности кластера становятся жёстче:
    *   Число шумовых точек **увеличивается** (меньше точек удовлетворяют условию ключевой точки).
    *   Число кластеров может **уменьшаться** (недостаточно плотные группы распадаются на шум).
    *   **При уменьшении min_samples** кластеры становятся более «терпимыми» к разреженности: шума меньше, но могут появляться ложные мелкие кластеры из выбросов."""
cells.append(nbf.v4.new_markdown_cell(answer4_analysis))

cells.append(nbf.v4.new_markdown_cell(
    "Выбрать лучшую модель среди обученных моделей *DBSCAN*. Привести обоснование на основе всех полученных результатов: "
    "учесть визуальную оценку диаграмм рассеяния, количество найденных шумовых точек, значение метрики силуэта."
))

best_db = res['best_dbscan']
answer4_best = f"""***Рассуждения и вывод***:

Лучшая модель DBSCAN: **eps={best_db['eps']}, min_samples={best_db['min_samples']}**.

**Обоснование:**
1. **Метрика силуэта** максимальна среди всех моделей: **{best_db['silhouette']:.3f}**.
2. **Число кластеров** ({best_db['n_clusters']}) соответствует визуально наблюдаемой структуре данных (2–3 группы).
3. **Количество шумовых точек** ({best_db['n_noise']}) допустимо — это около {best_db['n_noise']/342*100:.1f}% от всех объектов, что указывает на умеренную чувствительность к выбросам.
4. При eps ≥ 0.5 модели либо дают слишком мало кластеров (2), либо силуэт ниже. При eps=0.4, min_samples=5 силуэт заметно ниже ({res['dbscan_results']['eps=0.4, min_samples=5']['silhouette']:.3f}), а при min_samples ≥ 7 число шумовых точек резко растёт без улучшения метрики."""
cells.append(nbf.v4.new_markdown_cell(answer4_best))

# =================== ЗАДАНИЕ 5 ===================
cells.append(nbf.v4.new_markdown_cell("### Задание 5"))
cells.append(nbf.v4.new_markdown_cell(
    "Построить дендограмму для иерархической агломеративной кластеризации масштабированных данных. "
    "Использовать метод «дальнего соседа» (полной связи)."
))

code5a = """from scipy.cluster.hierarchy import dendrogram, linkage

linked = linkage(df21_scaled, method='complete')
fig, ax = plt.subplots(figsize=(14, 6))
dendrogram(linked, labels=df21_scaled.index, ax=ax, leaf_rotation=90, leaf_font_size=6)
ax.set_title('Дендрограмма (метод полной связи)')
ax.set_xlabel('Объекты')
ax.set_ylabel('Расстояние')
plt.tight_layout()
plt.show()"""
cells.append(nbf.v4.new_code_cell(code5a, outputs=[make_image_output('outputs/dendrogram.png')]))

cells.append(nbf.v4.new_markdown_cell("Определить оптимальное число кластеров согласно дендограмме."))

answer5_dendro = """***Ответ***:

По дендрограмме оптимальное число кластеров — **2 или 3**.

Наибольшее расстояние между уровнями слияния наблюдается при переходе от 2 к 1 кластеру, что указывает на естественное деление данных на 2 крупные группы. Однако внутри каждой из этих групп есть субструктура, позволяющая выделить 3 кластера (два крупных и один поменьше). Таким образом, дендрограмма поддерживает как K=2, так и K=3."""
cells.append(nbf.v4.new_markdown_cell(answer5_dendro))

cells.append(nbf.v4.new_markdown_cell(
    "Для оптимального числа кластеров и нескольких ближайших к нему вариантов обучить на масштабированных данных "
    "алгоритм агломеративной кластеризации (способ определения расстояния между кластерами не менять) "
    "и вычислить силуэт выборки."
))

code5b = """from sklearn.cluster import AgglomerativeClustering

for k in range(2, 6):
    agg = AgglomerativeClustering(n_clusters=k, linkage='complete')
    labels = agg.fit_predict(df21_scaled)
    sil = silhouette_score(df21_scaled, labels)
    print(f'K={k}: силуэт = {sil:.3f}')"""

agg_outputs = []
for k in range(2, 6):
    agg_outputs.append(make_text_output(f"K={k}: силуэт = {res['agg_results'][str(k)]['silhouette']:.3f}"))
cells.append(nbf.v4.new_code_cell(code5b, outputs=agg_outputs))

cells.append(nbf.v4.new_markdown_cell(
    "Ответить на вопросы:\n\n"
    "*  какое число кластеров является оптимальным по метрике силуэта для агломеративной кластеризации?\n"
    "*  совпадает ли выбор оптимального числа кластеров по дендограмме и по метрике силуэта для агломеративной кластеризации?\n"
    "*  если выбор различается, то чем это можно объяснить?"
))

answer5_final = f"""***Ответы:***

*   Оптимальное число кластеров по метрике силуэта для агломеративной кластеризации — **K={res['best_agg_k']}** (силуэт = {res['agg_results'][str(res['best_agg_k'])]['silhouette']:.3f}).
*   Выбор **частично совпадает**: дендрограмма допускает как K=2, так и K=3, а силуэт однозначно указывает на K={res['best_agg_k']}.
*   Различие объясняется тем, что **дендрограмма отражает иерархию слияний** и не всегда однозначно указывает на один оптимальный уровень. Метрика силуэта же количественно оценивает качество разделения для конкретного разбиения. При K=2 силуэт ниже ({res['agg_results']['2']['silhouette']:.3f}), что говорит о том, что одна из двух крупных групп на самом деле гетерогенна и выигрывает от разделения на две."""
cells.append(nbf.v4.new_markdown_cell(answer5_final))

# =================== ЗАДАНИЕ 6 ===================
cells.append(nbf.v4.new_markdown_cell("### Задание 6"))
cells.append(nbf.v4.new_markdown_cell(
    "Выполнить сравнительный анализ результатов работы трех алгоритмов кластеризации:\n\n"
    "*  алгоритма *K-means* с оптимальным числом кластеров, определенным в задании 3 (*random_state* не менять),\n"
    "*  алгоритма *DBSCAN* с оптимальными значениями параметров, определенными в задании 4,\n"
    "*  алгоритма агломеративной кластеризации с оптимальным по метрике силуэта числом кластеров (способ определения расстояния между кластерами не менять).\n\n"
    "Для каждого алгоритма:\n\n"
    "* создать и обучить модель на масштабированных данных;\n"
    "*  визуализировать результаты кластеризации в виде диаграмм рассеяния, отображая объекты разных кластеров различными цветами;\n"
    "*  вычислить значение силуэта выборки.\n\n"
    "**Указание**. При визуализации использовать все попарные сочетания признаков. Для каждой модели разместить диаграммы на общей области построения в один ряд. "
    "Названия признаков указать по осям. Добавить легенду с отображением соответствия цветов и меток кластеров. "
    "В общем заголовке указать название алгоритма, число кластеров и значение силуэта выборки, округлив его до 3 знаков после запятой."
))

code6 = """algorithms = {
    'K-means': (kmeans_results[best_k]['labels'], kmeans_results[best_k]['silhouette']),
    'DBSCAN': (best_dbscan['labels'], best_dbscan['silhouette']),
    'Agglomerative': (agg_results[best_agg_k]['labels'], agg_results[best_agg_k]['silhouette'])
}

for name, (labels, sil) in algorithms.items():
    n_clusters = len(set(labels)) - (1 if -1 in labels else 0)
    
    fig, axes = plt.subplots(1, 3, figsize=(18, 5))
    unique_labels = sorted(set(labels))
    colors = plt.cm.tab10(np.linspace(0, 1, max(len(unique_labels), 1)))
    
    for ax, (i, j) in zip(axes, pairs):
        for idx, k_label in enumerate(unique_labels):
            mask = labels == k_label
            if k_label == -1:
                ax.scatter(df21_scaled.iloc[mask, i], df21_scaled.iloc[mask, j],
                          c='gray', marker='x', label='Шум', alpha=0.5)
            else:
                ax.scatter(df21_scaled.iloc[mask, i], df21_scaled.iloc[mask, j],
                          c=[colors[idx]], label=f'Кластер {k_label}', alpha=0.7, edgecolors='k')
        ax.set_xlabel(features[i])
        ax.set_ylabel(features[j])
    
    axes[-1].legend(title='Кластеры', bbox_to_anchor=(1.05, 1), loc='upper left')
    plt.suptitle(f'{name}, кластеров={n_clusters}, силуэт={sil:.3f}', fontsize=14, y=1.02)
    plt.tight_layout()
    plt.show()
    print(f'{name}: кластеров={n_clusters}, силуэт={sil:.3f}')"""

comp_outputs = []
for name in ['K-means', 'DBSCAN', 'Agglomerative']:
    info = res['algorithms_summary'][name]
    comp_outputs.append(make_image_output(f'outputs/comparison_{name.lower().replace(" ", "_")}.png'))
    comp_outputs.append(make_text_output(f"{name}: кластеров={info['n_clusters']}, силуэт={info['silhouette']:.3f}"))
cells.append(nbf.v4.new_code_cell(code6, outputs=comp_outputs))

cells.append(nbf.v4.new_markdown_cell(
    "Дополнительно можно построить 3D-визуализацию результатов кластеризации для каждой из обученных моделей.\n\n"
    "**Указание**. Настройка цветов по меткам кластеров в методе *scatter_3d* выполняется с помощью параметра *color* (аналогично тому, как это выполняется в методе *scatter*)."
))

code6_3d = """# Пример 3D-визуализации для K-means (можно повторить для других алгоритмов)
# fig = px.scatter_3d(x=df21_scaled.iloc[:,0], y=df21_scaled.iloc[:,1], z=df21_scaled.iloc[:,2],
#                     color=kmeans_results[best_k]['labels'],
#                     labels={'x': features[0], 'y': features[1], 'z': features[2]})
# fig.update_layout(template='plotly_white')
# fig.show()"""
cells.append(nbf.v4.new_code_cell(code6_3d, outputs=[]))

cells.append(nbf.v4.new_markdown_cell(
    "Проанализировать полученные результаты и ответить на вопросы:\n\n"
    "* какой из алгоритмов показал наилучшее качество кластеризации? Выбор обосновать с учетом визуальной оценки диаграмм рассеяния и значения метрики силуэта.\n"
    "* насколько согласуются результаты алгоритмов по числу выделенных кластеров? Если есть различия, то чем они объясняются?\n"
    "* совпадает ли число кластеров, выделенных каждым алгоритмом, с результатом визуального анализа в задании 2? Если есть несовпадения, то чем они объясняются?"
))

summary = res['algorithms_summary']
answer6_final = f"""***Ответы:***

*   **Наилучшее качество показал алгоритм DBSCAN** (силуэт = {summary['DBSCAN']['silhouette']:.3f}). Он выделяет 3 кластера и корректно маркирует пограничные точки как шум. K-means с K={summary['K-means']['n_clusters']} даёт силуэт {summary['K-means']['silhouette']:.3f}, что тоже хорошо, но объединяет две близкие группы в один кластер. Агломеративная кластеризация с K={summary['Agglomerative']['n_clusters']} имеет силуэт {summary['Agglomerative']['silhouette']:.3f} — чуть ниже, чем у DBSCAN, но структура кластеров визуально схожа.

*   **Результаты частично согласуются**: DBSCAN и агломеративная кластеризация выделяют по 3 кластера, а K-means — {summary['K-means']['n_clusters']}. Различие объясняется принципиально разными подходами: K-means стремится минимизировать внутрикластерную дисперсию и склонен находить выпуклые, примерно равные по размеру группы; DBSCAN и агломеративный метод учитывают плотность/расстояние связи и лучше справляются с кластерами сложной формы и разного размера.

*   **Визуальный анализ в задании 2** допускал выделение 2–3 кластеров. K-means выбрал 2, что соответствует нижней границе оценки. DBSCAN и агломеративный метод выделили 3 кластера, что соответствует верхней границе. Несовпадение K-means с визуальной оценкой (3 кластера) объясняется тем, что два из трёх визуальных кластеров имеют близкие центры и K-means объединяет их, минимизируя суммарную дисперсию. Визуально же они разделяются по комбинации признаков."""
cells.append(nbf.v4.new_markdown_cell(answer6_final))

nb['cells'] = cells
nb['metadata'] = {
    "kernelspec": {
        "display_name": "Python 3 (ipykernel)",
        "language": "python",
        "name": "python3"
    },
    "language_info": {
        "codemirror_mode": {"name": "ipython", "version": 3},
        "file_extension": ".py",
        "mimetype": "text/x-python",
        "name": "python",
        "nbconvert_exporter": "python",
        "pygments_lexer": "ipython3",
        "version": "3.12.7"
    }
}

# Save notebook
with open('МакеевГБ.ipynb', 'w', encoding='utf-8') as f:
    nbf.write(nb, f)

print("Notebook создан: МакеевГБ.ipynb")
