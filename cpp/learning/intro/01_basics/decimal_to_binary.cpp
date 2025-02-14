#include <algorithm>
#include <iostream>
#include <string>

using namespace std;

string decimalToBinary(int number) {
  if (number == 0) return "0";
  bool isNegative = false;
  if (number < 0) {
    isNegative = true;
    number     = -number;
  }

  string binary = "";

  while (number > 0) {
    binary += (number % 2) + '0';
    number /= 2;
    reverse(binary.begin(), binary.end());
  }
  if (isNegative) {
    binary = "-" + binary;
  }
  return binary;
}

int main() {
  int x;
  cout << "input number: " << endl;
  cin >> x;
  cout << "Ваше число: " << x
       << " в бинарной системе исчисления = " << decimalToBinary(x) << endl;
  return 0;
}
