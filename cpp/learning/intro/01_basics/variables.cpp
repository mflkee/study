#include <math.h>
#include <iostream>
using namespace std;

// тип_данных имя_переменной;
int age = 42;  // Инициализация и объявление переменной age

int main() {
  int    a             = 42;       // Целочисленная переменная (4 байт)
  double PI            = 3.14159;  // Число с плавующей точкой (8 байт)
  char   grade         = 'S';      // Символьная переменная (1 байт)
  bool   is_watermelon = true;     // Логическая переменная (1 байт)
  cout << "Число вселенной: " << a << endl;
  cout << "Число Pi: " << PI << endl;
  cout << "Класс cложности босса: " << grade << endl;
  cout << "Наличие арбуза: " << is_watermelon << endl;
  const double P      = 3.14159;  // Объявление константы
  double       radius = 5.0;
  double       area   = P * radius * radius;
  cout << "Площадь круга = " << area << endl;

  // инициализация пустого
  int A;
  // int C = A + PI;
  return 0;
}
