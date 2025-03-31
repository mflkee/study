import turtle

# Создаем экран и черепашку
screen = turtle.Screen()
screen.bgcolor("black")
screen.title("Черепашка ходит по кругу")

# Создаем черепашку
t = turtle.Turtle()
t.color("white")
t.shape("turtle")
t.speed(10)  # Устанавливаем скорость черепашки

# Движение по кругу
radius = 300  # Радиус круга
t.penup()
t.goto(0, -radius)  # Ставим черепашку в начальную точку
t.pendown()
t.circle(radius)  # Черепашка обходит круг

# Завершаем программу по клику на экран
screen.mainloop()
