import tkinter as tk
from tkinter import messagebox
from tkinter import ttk


class ViscosityMeter:
    def __init__(self, name):
        self.name = name
        self.measurements = []
        self.average_viscosity = None

    def add_measurement(self, measurement):
        try:
            measurement = float(measurement)
            self.measurements.append(measurement)
        except ValueError:
            return False
        return True

    def calculate_average_viscosity(self):
        if self.measurements:
            self.average_viscosity = sum(self.measurements) / len(self.measurements)
        else:
            self.average_viscosity = None

    def calculate_absolute_error(self):
        if self.average_viscosity is None:
            return None

        v = self.average_viscosity
        if 0.5 <= v < 10:
            return 0.05 * v + 0.30
        elif 10 <= v <= 100:
            return 0.04 * v + 1.33
        else:
            return None


class Areometer:
    def __init__(self, viscosity_meter):
        self.viscosity = viscosity_meter.average_viscosity
        self.temperature_above_15 = False

    def set_temperature_above_15(self, above_15):
        self.temperature_above_15 = above_15

    def calculate_absolute_error(self):
        if self.viscosity is None:
            return None

        if self.temperature_above_15:
            return 0.0178 * self.viscosity
        else:
            return 0.0283 * self.viscosity


class ViscosityCalculatorApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Калькулятор вязкости и погрешностей")

        self.vt531 = ViscosityMeter("VT531")
        self.vt532 = ViscosityMeter("VT532")

        self.create_widgets()

    def create_widgets(self):
        # Создаем вкладки
        self.notebook = ttk.Notebook(self.root)
        self.notebook.pack(pady=10, padx=10, fill=tk.BOTH, expand=True)

        self.tab1 = ttk.Frame(self.notebook)
        self.tab2 = ttk.Frame(self.notebook)
        self.notebook.add(self.tab1, text="VT531")
        self.notebook.add(self.tab2, text="VT532")

        self.create_tab(self.tab1, self.vt531)
        self.create_tab(self.tab2, self.vt532)

    def create_tab(self, tab, viscosity_meter):
        self.entries = []
        self.labels = []

        for i in range(3):
            label = ttk.Label(tab, text=f"Замер {i+1}:")
            label.grid(row=i, column=0, padx=5, pady=5, sticky=tk.W)
            self.labels.append(label)

            entry = ttk.Entry(tab)
            entry.grid(row=i, column=1, padx=5, pady=5)
            self.entries.append(entry)

        calculate_button = ttk.Button(
            tab, text="Рассчитать", command=lambda: self.calculate(viscosity_meter)
        )
        calculate_button.grid(row=3, column=0, columnspan=2, pady=10)

    def calculate(self, viscosity_meter):
        measurements = []
        for entry in self.entries:
            measurement = entry.get().strip()
            if not viscosity_meter.add_measurement(measurement):
                messagebox.showerror("Ошибка", "Пожалуйста, введите число.")
                return
            measurements.append(float(measurement))

        viscosity_meter.calculate_average_viscosity()
        avg_viscosity = viscosity_meter.average_viscosity
        abs_error = viscosity_meter.calculate_absolute_error()

        if avg_viscosity is not None:
            messagebox.showinfo(
                "Результат",
                f"Средняя вязкость: {avg_viscosity:.2f}\nАбсолютная погрешность: {abs_error:.2f}",
            )
        else:
            messagebox.showerror("Ошибка", "Не удалось рассчитать среднюю вязкость.")

        self.create_areometer_tab(viscosity_meter)

    def create_areometer_tab(self, viscosity_meter):
        tab_name = viscosity_meter.name + " Ареометр"
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text=tab_name)

        label = ttk.Label(tab, text="Температура в лаборатории больше 15°C?")
        label.grid(row=0, column=0, padx=5, pady=5, sticky=tk.W)

        self.temp_var = tk.BooleanVar()
        temp_radio1 = ttk.Radiobutton(
            tab, text="Да", variable=self.temp_var, value=True
        )
        temp_radio1.grid(row=1, column=0, padx=5, pady=5, sticky=tk.W)
        temp_radio2 = ttk.Radiobutton(
            tab, text="Нет", variable=self.temp_var, value=False
        )
        temp_radio2.grid(row=2, column=0, padx=5, pady=5, sticky=tk.W)

        calculate_button = ttk.Button(
            tab,
            text="Рассчитать погрешность ареометра",
            command=lambda: self.calculate_areometer(viscosity_meter),
        )
        calculate_button.grid(row=3, column=0, pady=10)

    def calculate_areometer(self, viscosity_meter):
        areometer = Areometer(viscosity_meter)
        areometer.set_temperature_above_15(self.temp_var.get())
        abs_error = areometer.calculate_absolute_error()

        if abs_error is not None:
            temp_str = "больше 15°C" if self.temp_var.get() else "не больше 15°C"
            messagebox.showinfo(
                "Результат",
                f"Абсолютная погрешность ареометра при температуре {temp_str}: {abs_error:.2f}",
            )
        else:
            messagebox.showerror(
                "Ошибка", "Не удалось рассчитать абсолютную погрешность ареометра."
            )


if __name__ == "__main__":
    root = tk.Tk()
    app = ViscosityCalculatorApp(root)
    root.mainloop()
