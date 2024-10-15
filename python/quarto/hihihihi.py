import matplotlib.pyplot as plt

# Определите комплексные числа
z1 = complex(8, -6)
z2 = complex(3, -1)
sum_z = z1 + z2
diff_z = z1 - z2
prod_z = z1 * z2
quot_z = z1 / z2

# Список точек для отображения
points = {
    'z1': z1,
    'z2': z2,
    'z1 + z2': sum_z,
    'z1 - z2': diff_z,
    'z1 * z2': prod_z,
    'z1 / z2': quot_z
}

# Создание графика
plt.figure(figsize=(10, 10))

# Нанесение точек на график
for label, point in points.items():
    plt.plot(point.real, point.imag, 'o', label=f'{label} ({point.real}, {point.imag})')

# Добавление сетки и легенды
plt.axhline(0, color='black', lw=0.5, ls='--')
plt.axvline(0, color='black', lw=0.5, ls='--')
plt.grid(color='gray', linestyle='--', linewidth=0.5)
plt.xlim(-5, 20)
plt.ylim(-30, 5)
plt.title('Комплексная плоскость')
plt.xlabel('Действительная часть')
plt.ylabel('Мнимая часть')
plt.legend()
plt.show()


