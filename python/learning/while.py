value = 5
while value < 10000000:
    value = value * value 
    print("Счетчик:", value)


while True:
    try:
        age = int(input("Введите ваш возраст:"))
        wait_yaer = 18 - age
        if age < 18:
            print("Увы, пиздюк, тебе", age, "лет, а это значит тебе придется подождать еще", wait_yaer, "лет")
            continue
        break
    except ValueError:
        print("Некоректный ввод, введите число!")

print("Поздравляем ваш возраст:", age, "лет и вы с уверенностью можете попить пивко!!!")

