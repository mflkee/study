#include <iostream>

int global = 1;  // Глобальная инициализация вне функции
int main() {
  int age = 1;  // инициализация переменной
  int Age = 2;  // ригистрозависимый язык

  int x = 42;  // нотация присваивания (assignment notation)
  int y{34};   // инициализация в фигурных скобках (braced initialization)
               // безопаснее, нельзя сузить
  int z(47);   // функциональная инициализация (functional notation)

  int zero{};  // эквивалентно {0}
  std::cout << "zero = " << zero << std::endl;
  zero = 1;  // изменение значения
  std::cout << "zero = " << zero << std::endl;
}
