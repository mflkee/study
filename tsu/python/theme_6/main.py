import json

# Открываем входной файл 'visit_log.csv' для чтения и выходной файл 'funnel.csv' для записи
with open('visit_log.csv', 'r') as infile, open('funnel.csv', 'w') as outfile:
    # Записываем заголовок в выходной файл
    outfile.write('user_id,source,category\n')
    
    # Обрабатываем каждую строку во входном файле
    for line in infile:
        # Преобразуем строку из формата JSON в словарь
        record = json.loads(line)
        
        # Извлекаем user_id и category
        user_id = record.get('user_id')
        category = record.get('category')
        
        # Проверяем, что категория не 'не определена'
        if category and category != 'не определена':
            # Записываем строку в выходной файл
            # Предположим, что source не определен в данных, поэтому указываем 'other'
            outfile.write(f'{user_id},other,{category}\n')