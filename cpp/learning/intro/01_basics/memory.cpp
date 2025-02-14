#include <fstream>
#include <iostream>
using namespace std;

int main() {
  // Знаковые числа
  char  var_char  = 120;
  short var_short = 120;
  int   var_int   = 120;
  long  var_long  = 120;

  // Определяем размер памяти для каждого типа данных
  cout << sizeof(int) << " " << sizeof(short) << " " << sizeof(long) << " "
       << sizeof(long long) << " " << sizeof(char) << endl;
  // Создаем файл, который являеться образом оперативной памяти во внешней
  ofstream output("memory.dat");
  // Записываем в файл числа так как они храняться в пямяти
  output.write(reinterpret_cast<const char*>(&var_char), sizeof(char));
  output.write(reinterpret_cast<const char*>(&var_short), sizeof(short));
  output.write(reinterpret_cast<const char*>(&var_int), sizeof(int));
  output.write(reinterpret_cast<const char*>(&var_long), sizeof(long));
}
