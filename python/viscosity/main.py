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

    def calculate_absolute_error(self):
        if self.average_viscosity is None:
            return None

        v = self.average_viscosity
        if 0.5 <= v < 10:
            return 0.05 * v + 0.30
        elif 10 <= v <= 100:
            return 0.04 * v + 1.33
        return None


class Areometer:
    def __init__(self, viscosity_meter):
        self.viscosity = viscosity_meter.average_viscosity
        self.temperature_above_15 = False

    def calculate_absolute_error(self):
        if self.viscosity is None:
            return None
        if self.temperature_above_15:
            return 0.0178 * self.viscosity
        return 0.0283 * self.viscosity


class ViscosityCalculatorApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Калькулятор вязкости и погрешностей")
        self.areometer_tabs = {}

        self.vt531 = ViscosityMeter("VT531")
        self.vt532 = ViscosityMeter("VT532")

        self.create_widgets()
        self.setup_style()

    def setup_style(self):
        style = ttk.Style()
        style.configure("Error.TEntry", foreground="red")

    def create_widgets(self):
        self.notebook = ttk.Notebook(self.root)
        self.notebook.pack(pady=10, padx=10, fill=tk.BOTH, expand=True)

        self.create_viscosity_tab(self.vt531)
        self.create_viscosity_tab(self.vt532)

    def create_viscosity_tab(self, meter):
        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text=meter.name)

        entries = []
        for i in range(3):
            frame = ttk.Frame(tab)
            frame.pack(pady=5, fill=tk.X)

            label = ttk.Label(frame, text=f"Замер {i+1} (сСт):", width=15)
            label.pack(side=tk.LEFT, padx=5)

            entry = ttk.Entry(frame)
            entry.pack(side=tk.LEFT, fill=tk.X, expand=True)
            entries.append(entry)

        density_frame = ttk.Frame(tab)
        density_frame.pack(pady=5, fill=tk.X)

        ttk.Label(density_frame, text="Плотность (г/см³):", width=15).pack(
            side=tk.LEFT, padx=5
        )
        density_entry = ttk.Entry(density_frame)
        density_entry.pack(side=tk.LEFT, fill=tk.X, expand=True)

        btn_frame = ttk.Frame(tab)
        btn_frame.pack(pady=10)

        ttk.Button(
            btn_frame,
            text="Рассчитать",
            command=lambda: self.calculate(meter, entries, density_entry),
        ).pack(side=tk.LEFT, padx=5)

        ttk.Button(
            btn_frame,
            text="Очистить",
            command=lambda: self.clear_fields(entries, density_entry),
        ).pack(side=tk.LEFT)

    def clear_fields(self, entries, density_entry):
        for entry in entries:
            entry.delete(0, tk.END)
            entry.configure(style="TEntry")
        density_entry.delete(0, tk.END)
        density_entry.configure(style="TEntry")

    def validate_entry(self, entry):
        value = entry.get()
        if not value.replace(".", "", 1).isdigit() or float(value) <= 0:
            entry.configure(style="Error.TEntry")
            return False
        entry.configure(style="TEntry")
        return True

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
            abs_error = meter.calculate_absolute_error()
            msg = (
                f"Средняя вязкость: {meter.average_viscosity:.2f} сП\n"
                f"Абс. погрешность: {abs_error:.2f} сП"
            )
            messagebox.showinfo("Результат", msg)
            self.create_areometer_tab(meter)
        else:
            messagebox.showerror("Ошибка", "Недостаточно измерений")

    def create_areometer_tab(self, meter):
        if meter.name in self.areometer_tabs:
            self.notebook.select(self.areometer_tabs[meter.name])
            return

        tab = ttk.Frame(self.notebook)
        self.notebook.add(tab, text=f"{meter.name} Ареометр")
        self.areometer_tabs[meter.name] = tab

        temp_var = tk.BooleanVar()

        ttk.Label(tab, text="Температурные условия:").pack(pady=5)
        ttk.Radiobutton(tab, text=">15°C", variable=temp_var, value=True).pack()
        ttk.Radiobutton(tab, text="≤15°C", variable=temp_var, value=False).pack()

        ttk.Button(
            tab,
            text="Рассчитать погрешность",
            command=lambda: self.show_areometer_error(meter, temp_var.get()),
        ).pack(pady=10)

    def show_areometer_error(self, meter, temp_above_15):
        areometer = Areometer(meter)
        areometer.temperature_above_15 = temp_above_15
        error = areometer.calculate_absolute_error()

        if error:
            temp_str = ">15°C" if temp_above_15 else "≤15°C"
            msg = f"Погрешность ареометра ({temp_str}): {error:.2f} сП"
            messagebox.showinfo("Результат", msg)
        else:
            messagebox.showerror("Ошибка", "Невозможно рассчитать погрешность")


if __name__ == "__main__":
    root = tk.Tk()
    app = ViscosityCalculatorApp(root)
    root.mainloop()
