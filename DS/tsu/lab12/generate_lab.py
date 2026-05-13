import json
import base64
import io
import os

import matplotlib.pyplot as plt
import seaborn as sns
import numpy as np
import pandas as pd
from sklearn.preprocessing import StandardScaler
from sklearn.cluster import KMeans, DBSCAN, AgglomerativeClustering
from sklearn.metrics import silhouette_score
from scipy.cluster.hierarchy import dendrogram, linkage
import plotly.express as px
import plotly.io as pio

# Fix random seed for reproducibility
np.random.seed(21)
os.makedirs('outputs', exist_ok=True)

# ============================================================
# ЗАДАНИЕ 1: Импорт
# ============================================================
print("=" * 60)
print("ЗАДАНИЕ 1: Импорт библиотек")
print("=" * 60)

# Загрузка данных
data = sns.load_dataset('penguins')
df21 = data.iloc[:, 2:5].dropna()

print(f"Данные загружены. Размер df21: {df21.shape}")

# ============================================================
# ЗАДАНИЕ 2: Ознакомление с данными
# ============================================================
print("\n" + "=" * 60)
print("ЗАДАНИЕ 2: Ознакомление с данными")
print("=" * 60)

print("\nПервые 7 строк df21:")
print(df21.head(7))

n_objects = df21.shape[0]
print(f"\nКоличество объектов в наборе данных: {n_objects}")

# Диаграммы рассеяния (попарные)
fig, axes = plt.subplots(1, 3, figsize=(18, 5))
features = df21.columns
pairs = [(0, 1), (0, 2), (1, 2)]

for ax, (i, j) in zip(axes, pairs):
    ax.scatter(df21.iloc[:, i], df21.iloc[:, j], alpha=0.7, edgecolors='k')
    ax.set_xlabel(features[i])
    ax.set_ylabel(features[j])
    ax.set_title(f'{features[i]} vs {features[j]}')

plt.suptitle('Попарные диаграммы рассеяния признаков пингвинов', fontsize=14, y=1.02)
plt.tight_layout()
plt.savefig('outputs/scatter_pairs_raw.png', dpi=150, bbox_inches='tight')
plt.close()

# 3D визуализация (plotly -> static image via kaleido if available, else skip)
try:
    fig3d = px.scatter_3d(
        x=df21.iloc[:, 0], y=df21.iloc[:, 1], z=df21.iloc[:, 2],
        labels={'x': features[0], 'y': features[1], 'z': features[2]}
    )
    fig3d.update_layout(template='plotly_white')
    fig3d.update_traces(marker=dict(size=6, opacity=0.8))
    fig3d.write_image('outputs/scatter_3d_raw.png', width=800, height=600, scale=2)
    has_3d_raw = True
except Exception as e:
    print(f"3D визуализация пропущена (нужен kaleido): {e}")
    has_3d_raw = False

# Описательные статистики
desc_stats = df21.describe()
print("\nОписательные статистики:")
print(desc_stats)

# Стандартизация
scaler = StandardScaler()
df21_scaled = pd.DataFrame(scaler.fit_transform(df21), columns=df21.columns)

print("\nСтандартизация выполнена. Первые 5 строк масштабированных данных:")
print(df21_scaled.head())

# ============================================================
# ЗАДАНИЕ 3: K-means
# ============================================================
print("\n" + "=" * 60)
print("ЗАДАНИЕ 3: K-means кластеризация")
print("=" * 60)

kmeans_results = {}
for k in range(2, 7):
    kmeans = KMeans(n_clusters=k, random_state=21, n_init='auto')
    labels = kmeans.fit_predict(df21_scaled)
    sil = silhouette_score(df21_scaled, labels)
    kmeans_results[k] = {'model': kmeans, 'labels': labels, 'silhouette': sil}
    
    # Визуализация
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
    plt.savefig(f'outputs/kmeans_k{k}.png', dpi=150, bbox_inches='tight')
    plt.close()
    
    print(f"K={k}: силуэт = {sil:.3f}")

# Оптимальное k
best_k = max(kmeans_results, key=lambda x: kmeans_results[x]['silhouette'])
print(f"\nОптимальное число кластеров по силуэту: K={best_k} (силуэт={kmeans_results[best_k]['silhouette']:.3f})")

# ============================================================
# ЗАДАНИЕ 4: DBSCAN
# ============================================================
print("\n" + "=" * 60)
print("ЗАДАНИЕ 4: DBSCAN кластеризация")
"=" * 60

# Визуализация масштабированных данных
fig, axes = plt.subplots(1, 3, figsize=(18, 5))
for ax, (i, j) in zip(axes, pairs):
    ax.scatter(df21_scaled.iloc[:, i], df21_scaled.iloc[:, j], alpha=0.7, edgecolors='k')
    ax.set_xlabel(features[i])
    ax.set_ylabel(features[j])
    ax.set_title(f'{features[i]} vs {features[j]}')
plt.suptitle('Масштабированные данные (попарные проекции)', fontsize=14, y=1.02)
plt.tight_layout()
plt.savefig('outputs/scatter_pairs_scaled.png', dpi=150, bbox_inches='tight')
plt.close()

# DBSCAN grid: eps 0.4..0.8 step 0.1, min_samples 5..9
best_pair = (0, 2)  # bill_length_mm vs flipper_length_mm (предварительно)

dbscan_results = {}
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
        
        dbscan_results[(eps, min_samples)] = {
            'labels': labels, 'n_clusters': n_clusters, 
            'n_noise': n_noise, 'silhouette': sil
        }
        
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
        
        ax.set_title(f'eps={eps:.1f}, min_samples={min_samples}\n'
                    f'Кластеров: {n_clusters}, Шум: {n_noise}, Силуэт: {sil:.3f}')
        ax.set_xlabel(features[best_pair[0]])
        ax.set_ylabel(features[best_pair[1]])

plt.suptitle('DBSCAN: варьирование eps и min_samples', fontsize=16, y=1.00)
plt.tight_layout()
plt.savefig('outputs/dbscan_grid.png', dpi=150, bbox_inches='tight')
plt.close()

# Вывод результатов DBSCAN
print("\nРезультаты DBSCAN:")
for (eps, min_samples), res in dbscan_results.items():
    print(f"eps={eps:.1f}, min_samples={min_samples}: кластеров={res['n_clusters']}, "
          f"шум={res['n_noise']}, силуэт={res['silhouette']:.3f}")

# Лучшая модель DBSCAN (по силуэту, исключая случаи с 1 кластером)
valid_dbscan = {k: v for k, v in dbscan_results.items() if v['n_clusters'] > 1 and not np.isnan(v['silhouette'])}
best_dbscan_params = max(valid_dbscan, key=lambda x: valid_dbscan[x]['silhouette'])
best_dbscan = valid_dbscan[best_dbscan_params]
print(f"\nЛучшая модель DBSCAN: eps={best_dbscan_params[0]:.1f}, min_samples={best_dbscan_params[1]}, "
      f"силуэт={best_dbscan['silhouette']:.3f}")

# ============================================================
# ЗАДАНИЕ 5: Иерархическая агломеративная кластеризация
# ============================================================
print("\n" + "=" * 60)
print("ЗАДАНИЕ 5: Иерархическая агломеративная кластеризация")
print("=" * 60)

# Дендрограмма
linked = linkage(df21_scaled, method='complete')
fig, ax = plt.subplots(figsize=(14, 6))
dendrogram(linked, labels=df21_scaled.index, ax=ax, leaf_rotation=90, leaf_font_size=6)
ax.set_title('Дендрограмма (метод полной связи)')
ax.set_xlabel('Объекты')
ax.set_ylabel('Расстояние')
plt.tight_layout()
plt.savefig('outputs/dendrogram.png', dpi=150, bbox_inches='tight')
plt.close()

# Агломеративная кластеризация для разных K
agg_results = {}
for k in range(2, 6):
    agg = AgglomerativeClustering(n_clusters=k, linkage='complete')
    labels = agg.fit_predict(df21_scaled)
    sil = silhouette_score(df21_scaled, labels)
    agg_results[k] = {'labels': labels, 'silhouette': sil}
    print(f"K={k}: силуэт = {sil:.3f}")

best_agg_k = max(agg_results, key=lambda x: agg_results[x]['silhouette'])
print(f"\nОптимальное K по силуэту для агломеративной кластеризации: {best_agg_k} "
      f"(силуэт={agg_results[best_agg_k]['silhouette']:.3f})")

# ============================================================
# ЗАДАНИЕ 6: Сравнительный анализ
# ============================================================
print("\n" + "=" * 60)
print("ЗАДАНИЕ 6: Сравнительный анализ")
print("=" * 60)

algorithms = {
    'K-means': (kmeans_results[best_k]['labels'], kmeans_results[best_k]['silhouette']),
    'DBSCAN': (best_dbscan['labels'], best_dbscan['silhouette']),
    'Agglomerative': (agg_results[best_agg_k]['labels'], agg_results[best_agg_k]['silhouette'])
}

for name, (labels, sil) in algorithms.items():
    n_clusters = len(set(labels)) - (1 if -1 in labels else 0)
    print(f"{name}: кластеров={n_clusters}, силуэт={sil:.3f}")
    
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
    plt.savefig(f'outputs/comparison_{name.lower().replace(" ", "_")}.png', dpi=150, bbox_inches='tight')
    plt.close()

# Сохраним ключевые результаты в JSON для использования при генерации notebook
results = {
    'n_objects': int(n_objects),
    'features': list(features),
    'pairs': pairs,
    'best_pair': best_pair,
    'desc_stats': desc_stats.to_dict(),
    'kmeans_results': {str(k): {'silhouette': round(v['silhouette'], 3)} for k, v in kmeans_results.items()},
    'best_k': int(best_k),
    'dbscan_results': {
        f"eps={k[0]:.1f}, min_samples={k[1]}": {
            'n_clusters': v['n_clusters'],
            'n_noise': v['n_noise'],
            'silhouette': round(v['silhouette'], 3) if not np.isnan(v['silhouette']) else None
        }
        for k, v in dbscan_results.items()
    },
    'best_dbscan': {
        'eps': round(best_dbscan_params[0], 1),
        'min_samples': int(best_dbscan_params[1]),
        'silhouette': round(best_dbscan['silhouette'], 3),
        'n_clusters': best_dbscan['n_clusters'],
        'n_noise': best_dbscan['n_noise']
    },
    'agg_results': {str(k): {'silhouette': round(v['silhouette'], 3)} for k, v in agg_results.items()},
    'best_agg_k': int(best_agg_k),
    'algorithms_summary': {
        name: {
            'n_clusters': int(len(set(labels)) - (1 if -1 in labels else 0)),
            'silhouette': round(sil, 3)
        }
        for name, (labels, sil) in algorithms.items()
    }
}

with open('outputs/results.json', 'w', encoding='utf-8') as f:
    json.dump(results, f, ensure_ascii=False, indent=2)

print("\nВсе результаты сохранены в папку outputs/")
