#include <iostream>  // Подключаем заголовочный файл

int a{};
int main() {
  std::cout << "Hello World!" << std::endl;
  int age;
  age = 27;
  std::cout << "Age = " << age << std::endl;
  int value = {42};
  std::cout << "Value = " << value << std::endl;
  float x = 4.13, y{5.18}, z(10.3);  // Три способа обьявления переменной
  std::cout << "x = " << x << std::endl;
  x = 3;  // Можно переназначать переменную
  std::cout << "x = " << x << std::endl;
  std::cout << "y = " << y << std::endl;
  std::cout << "z = " << z << std::endl;
  std::cout << "a = " << a << std::endl;
  return 0;
}
