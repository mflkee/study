class ViscosityMeter:
    def __init__(self, name):
        self.name = name
        self.measurements = self.get_measurements()
        if self.measurements:
            self.average_viscosity = sum(self.measurements) / len(self.measurements)
        else:
            self.average_viscosity = None

    def get_measurements(self):
        measurements = []
        print(f"Введите 3 замера вязкости для {self.name}:")
        for i in range(1, 4):
            try:
                measurement = float(input(f"Замер {i}: "))
                measurements.append(measurement)
            except ValueError:
                print("Пожалуйста, введите число.")
                return []
        return measurements

    def calculate_absolute_error(self):
        if self.average_viscosity is None:
            print(f"Не удалось рассчитать среднюю вязкость для {self.name}.")
            return None

        v = self.average_viscosity
        print(f"Для вискозиметра {self.name} средняя вязкость составляет {v:.2f}.")

        # Определяем диапазон на основе среднего значения вязкости
        if 0.5 <= v < 10:
            return 0.05 * v + 0.30
        elif 10 <= v <= 100:
            return 0.04 * v + 1.33
        else:
            print("Вязкость не попадает в указанные диапазоны.")
            return None


class Areometer:
    def __init__(self, viscosity_meter):
        self.viscosity = viscosity_meter.average_viscosity
        self.temperature_above_15 = self.get_temperature_above_15()

    def get_temperature_above_15(self):
        answer = (
            input("Температура в лаборатории больше 15°C? (yes/no): ").strip().lower()
        )
        return answer == "yes"

    def calculate_absolute_error(self):
        if self.viscosity is None:
            print(
                "Не удалось рассчитать абсолютную погрешность ареометра из-за отсутствия данных о вязкости."
            )
            return None

        print(
            f"Для указанной температуры абсолютная погрешность ареометра будет рассчитана."
        )
        if self.temperature_above_15:
            return 0.0178 * self.viscosity
        else:
            return 0.0283 * self.viscosity


# Создаем объекты вискозиметров и рассчитываем их погрешности
vt531 = ViscosityMeter("VT531")
vt532 = ViscosityMeter("VT532")

vt531_error = vt531.calculate_absolute_error()
vt532_error = vt532.calculate_absolute_error()

print(f"Абсолютная погрешность VT531: {vt531_error}")
print(f"Абсолютная погрешность VT532: {vt532_error}")

# Создаем объекты ареометра для каждого вискозиметра и рассчитываем их погрешности
areometer_vt531 = Areometer(vt531)
areometer_vt532 = Areometer(vt532)

areometer_vt531_error = areometer_vt531.calculate_absolute_error()
areometer_vt532_error = areometer_vt532.calculate_absolute_error()

print(
    f"Абсолютная погрешность ареометра для VT531 при температуре {'>15°C' if areometer_vt531.temperature_above_15 else '≤15°C'}: {areometer_vt531_error}"
)
print(
    f"Абсолютная погрешность ареометра для VT532 при температуре {'>15°C' if areometer_vt532.temperature_above_15 else '≤15°C'}: {areometer_vt532_error}"
)
