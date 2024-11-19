# Открываем входной файл 'visit_log.csv' для чтения и выходной файл 'funnel.csv' для записи
with open('visit_log.csv', 'r') as infile, open('funnel.csv', 'w') as outfile:
    # Записываем заголовок в выходной файл
    outfile.write('user_id,source,category\n')
    
    # Обрабатываем каждую строку во входном файле
    for line in infile:
        # Разделяем строку на компоненты по запятой
        user_id, source, category = line.strip().split(',')
        
        # Проверяем, была ли совершена покупка (предполагаем, что наличие категории указывает на покупку)
        if category != '':  # Здесь можно заменить условие на более точное, если известно
            # Записываем строку в выходной файл
            outfile.write(f'{user_id},{source},{category}\n')