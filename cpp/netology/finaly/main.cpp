#include <algorithm>
#include <fstream>
#include <iostream>
#include <map>
#include <memory>
#include <sstream>
#include <string>
#include <vector>

// Базовый класс товара
class Product {
public:
  std::string sku;                          // артикул
  double price;                             // цена
  unsigned int stock;                       // количество на складе
  virtual std::string category() const = 0; // категория
  virtual ~Product() {}
};

// Одежда
class Clothing : public Product {
public:
  std::string name;  // наименование
  std::string size;  // размер
  std::string color; // цвет
  std::string category() const override { return "Одежда"; }
};

// Электроника
class Electronics : public Product {
public:
  std::string name;         // наименование
  std::string model;        // модель
  std::string manufacturer; // производитель
  std::string category() const override { return "Электроника"; }
};

// Книга
class Book : public Product {
public:
  std::string author;    // автор
  std::string title;     // название
  std::string publisher; // издательство
  std::string category() const override { return "Книги"; }
};

// Покупатель
struct Customer {
  std::string name;      // ФИО
  std::string card;      // номер карты
  double balance;        // баланс
  bool notified = false; // уведомлён
};

// Позиция заказа
struct OrderItem {
  std::string sku;  // артикул
  unsigned int qty; // количество
};

// Заказ
struct Order {
  unsigned int id;              // номер заказа
  std::string card;             // номер карты покупателя
  std::vector<OrderItem> items; // список позиций
};

// Торговая площадка
class Marketplace {
  std::map<std::string, std::unique_ptr<Product>> products; // товары
  std::map<std::string, Customer> customers;                // покупатели
  std::vector<Order> orders;                                // заказы
  double S; // пороговая сумма для скидки
  double p; // процент скидки

  // Результаты
  std::map<std::string, double> revenue; // выручка по категориям
  std::vector<std::string> queue;        // очередь покупателей
  std::vector<std::pair<std::string, unsigned int>>
      reserved; // зарезервированные товары

public:
  // Загрузка товаров
  void loadProducts(const std::string &filename) {
    std::ifstream f(filename);
    if (!f) {
      std::cerr << "Не удалось открыть файл с товарами\n";
      return;
    }
    std::string line;
    while (std::getline(f, line)) {
      if (line.empty())
        continue;
      std::stringstream ss(line);
      std::string type;
      std::getline(ss, type, ',');
      if (type == "Clothing") {
        auto item = std::make_unique<Clothing>();
        std::getline(ss, item->sku, ',');
        std::getline(ss, item->name, ',');
        std::getline(ss, item->size, ',');
        std::getline(ss, item->color, ',');
        ss >> item->price;
        ss.ignore();
        ss >> item->stock;
        products[item->sku] = std::move(item);
      } else if (type == "Electronics") {
        auto item = std::make_unique<Electronics>();
        std::getline(ss, item->sku, ',');
        std::getline(ss, item->name, ',');
        std::getline(ss, item->model, ',');
        std::getline(ss, item->manufacturer, ',');
        ss >> item->price;
        ss.ignore();
        ss >> item->stock;
        products[item->sku] = std::move(item);
      } else if (type == "Book") {
        auto item = std::make_unique<Book>();
        std::getline(ss, item->sku, ',');
        std::getline(ss, item->author, ',');
        std::getline(ss, item->title, ',');
        ss >> item->price;
        ss.ignore();
        std::getline(ss, item->publisher, ',');
        ss >> item->stock;
        products[item->sku] = std::move(item);
      }
    }
  }

  // Загрузка покупателей
  void loadCustomers(const std::string &filename) {
    std::ifstream f(filename);
    if (!f) {
      std::cerr << "Не удалось открыть файл с покупателями\n";
      return;
    }
    std::string line;
    while (std::getline(f, line)) {
      if (line.empty())
        continue;
      std::stringstream ss(line);
      Customer c;
      std::getline(ss, c.name, ',');
      std::getline(ss, c.card, ',');
      ss >> c.balance;
      customers[c.card] = c;
    }
  }

  // Загрузка заказов (группировка по id)
  void loadOrders(const std::string &filename) {
    std::ifstream f(filename);
    if (!f) {
      std::cerr << "Не удалось открыть файл с заказами\n";
      return;
    }
    std::string line;
    std::map<unsigned int, Order> temp;
    while (std::getline(f, line)) {
      if (line.empty())
        continue;
      std::stringstream ss(line);
      unsigned int id;
      ss >> id;
      ss.ignore();
      std::string card;
      std::getline(ss, card, ',');
      std::string sku;
      unsigned int qty;
      std::getline(ss, sku, ',');
      ss >> qty;
      if (temp.find(id) == temp.end()) {
        Order o;
        o.id = id;
        o.card = card;
        o.items.push_back({sku, qty});
        temp[id] = o;
      } else {
        temp[id].items.push_back({sku, qty});
      }
    }
    for (auto &kv : temp) {
      orders.push_back(kv.second);
    }
  }

  // Ввод параметров скидки
  void initParams() {
    std::cout << "Введите пороговую сумму для скидки (S): ";
    std::cin >> S;
    std::cout << "Введите процент скидки (p): ";
    std::cin >> p;
  }

  // Обработка заказов
  void process() {
    for (auto &order : orders) {
      auto &cust = customers[order.card];
      std::vector<OrderItem> ok;
      // Проверка наличия
      for (auto &it : order.items) {
        auto &prod = products[it.sku];
        if (prod && prod->stock >= it.qty) {
          prod->stock -= it.qty;
          ok.push_back(it);
        } else {
          cust.notified = true;
        }
      }
      if (ok.empty())
        continue;
      // Расчёт суммы
      double total = 0;
      for (auto &it : ok)
        total += products[it.sku]->price * it.qty;
      if (total > S)
        total *= (100 - p) / 100.0;
      // Оплата
      if (cust.balance >= total) {
        cust.balance -= total;
        for (auto &it : ok)
          revenue[products[it.sku]->category()] +=
              products[it.sku]->price * it.qty;
      } else {
        // Оптимизация корзины
        std::sort(ok.begin(), ok.end(), [&](auto &a, auto &b) {
          return products[a.sku]->price * a.qty >
                 products[b.sku]->price * b.qty;
        });
        // Возврат на склад
        for (auto &it : ok)
          products[it.sku]->stock += it.qty;
        double sum = 0;
        std::vector<OrderItem> opt;
        for (auto &it : ok) {
          double cost = products[it.sku]->price * it.qty;
          if (sum + cost <= cust.balance) {
            sum += cost;
            opt.push_back(it);
          }
        }
        if (!opt.empty()) {
          cust.notified = true;
          queue.push_back(cust.card);
          for (auto &it : opt) {
            products[it.sku]->stock -= it.qty;
            reserved.emplace_back(it.sku, it.qty);
          }
        }
      }
    }
  }

  // Отчёт и сохранение
  void report() {
    std::cout << "\n== Отчёт торговой площадки ==\n";
    std::cout << "Выручка по категориям:\n";
    for (auto &r : revenue)
      std::cout << "  " << r.first << ": " << r.second << "\n";
    std::cout << "Всего в очереди: " << queue.size() << "\n";
    if (!queue.empty()) {
      std::cout << "Очередь:\n";
      for (auto &c : queue)
        std::cout << "  " << c << "\n";
    }
    std::cout << "Зарезервировано позиций: " << reserved.size() << "\n";
    if (!reserved.empty()) {
      std::cout << "Резерв:\n";
      for (auto &pr : reserved)
        std::cout << "  " << pr.first << ", qty=" << pr.second << "\n";
    }
    std::cout << "Повторные напоминания:\n";
    for (auto &kv : customers) {
      if (kv.second.notified)
        std::cout << "  " << kv.second.card << ": Повторное напоминание\n";
    }
    // В файл
    std::ofstream out("statistics.txt");
    out << "== Отчёт торговой площадки ==\n";
    out << "Выручка по категориям:\n";
    for (auto &r : revenue)
      out << r.first << ": " << r.second << "\n";
    out << "Всего в очереди: " << queue.size() << "\n";
    if (!queue.empty()) {
      out << "Очередь:\n";
      for (auto &c : queue)
        out << c << "\n";
    }
    out << "Зарезервировано позиций: " << reserved.size() << "\n";
    if (!reserved.empty()) {
      out << "Резерв:\n";
      for (auto &pr : reserved)
        out << pr.first << ", qty=" << pr.second << "\n";
    }
    out << "Повторные напоминания:\n";
    for (auto &kv : customers) {
      if (kv.second.notified)
        out << kv.second.card << ": Повторное напоминание\n";
    }
  }
};

int main() {
  Marketplace mp;
  mp.loadProducts("products.csv");
  mp.loadCustomers("customers.csv");
  mp.loadOrders("orders.csv");
  mp.initParams();
  mp.process();
  mp.report();
  std::cout << "Статистика сохранена в statistics.txt\n";
  return 0;
}
