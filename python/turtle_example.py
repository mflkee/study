import turtle

# Создаем экран и черепашку
screen = turtle.Screen()
screen.bgcolor("white")
screen.title("Черепашка ходит по кругу")

# Создаем черепашку
t = turtle.Turtle()
t.shape("turtle")
t.speed(1)  # Устанавливаем скорость черепашки

# Движение по кругу
radius = 100  # Радиус круга
t.penup()
t.goto(0, -radius)  # Ставим черепашку в начальную точку
t.pendown()

t.circle(radius)  # Черепашка обходит круг

# Завершаем программу по клику на экран
screen.mainloop()
