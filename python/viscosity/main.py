import tkinter as tk
from tkinter import messagebox
from tkinter import ttk


class ViscosityMeter:
    def __init__(self, name):
        self.name = name
        self.measurements = []
        self.average_viscosity = None
        self.density = 1.0  # Значение по умолчанию

    def set_density(self, density):
        try:
            self.density = float(density)
            return True
        except ValueError:
            return False

    def add_measurement(self, measurement):
        try:
            measurement = float(measurement)
            if measurement <= 0:
                return False
            self.measurements.append(measurement)
            return True
        except ValueError:
            return False

    def calculate_average_viscosity(self):
        if len(self.measurements) >= 3:
            avg = sum(self.measurements[-3:]) / 3
            self.average_viscosity = avg * self.density  # Конвертация в сантипуазы
            return True
        return False

    def calculate_absolute_error(self, temp_above_15):
        if not self.calculate_average_viscosity():
            return None

        # Отладочная информация
        print(f"Средняя вязкость: {self.average_viscosity:.2f} сП")

        if not (0.5 <= self.average_viscosity <= 100):
            print("Вязкость вне допустимого диапазона (0.5–100 сП)")
            return None  # Вязкость вне допустимого диапазона

        areometer = Areometer(self)
        areometer.temperature_above_15 = temp_above_15
        return areometer.calculate_absolute_error()


class ViscosityCalculatorApp:
    def calculate(self, meter, entries, density_entry):
        measurements = []
        valid = True

        if not self.validate_entry(density_entry) or not meter.set_density(
            density_entry.get()
        ):
            valid = False

        for entry in entries:
            if not self.validate_entry(entry):
                valid = False
            else:
                if not meter.add_measurement(entry.get()):
                    valid = False

        if not valid:
            messagebox.showerror("Ошибка", "Проверьте правильность ввода данных")
            return

        if meter.calculate_average_viscosity():
            if not (0.5 <= meter.average_viscosity <= 100):
                messagebox.showerror(
                    "Ошибка",
                    f"Средняя вязкость ({meter.average_viscosity:.2f} сП) "
                    "вне допустимого диапазона (0.5–100 сП)",
                )
                return

            self.create_areometer_tab(meter)
        else:
            messagebox.showerror("Ошибка", "Недостаточно измерений")
