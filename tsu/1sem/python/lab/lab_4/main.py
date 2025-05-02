import json
import xml.etree.ElementTree as ET
from xml.dom import minidom


# Чтение данных из JSON файла
def read_json(file_path):
    with open(file_path, "r", encoding="utf-8") as file:
        return json.load(file)


# Конвертация данных из JSON в XML
def json_to_xml(data):
    root = ET.Element("Данные")
    for entry in data:
        person = ET.SubElement(root, "Человек")
        for key, value in entry.items():
            element = ET.SubElement(person, key)
            element.text = str(value)
    return root


# Функция для форматирования XML с отступами
def pretty_print(element):
    xml_string = ET.tostring(element, encoding="utf-8")
    parsed = minidom.parseString(xml_string)
    return parsed.toprettyxml(indent="    ")


# Запись отформатированного XML в файл
def write_xml(element, file_path):
    formatted_xml = pretty_print(element)
    with open(file_path, "w", encoding="utf-8") as file:
        file.write(formatted_xml)


# Основная программа
json_data = read_json("data.json")
xml_root = json_to_xml(json_data)
write_xml(xml_root, "data.xml")

print(
    "Конвертация завершена. Данные сохранены в 'data.xml' с отступами для читаемости."
)
