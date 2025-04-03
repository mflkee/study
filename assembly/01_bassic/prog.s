.section .data
numbers:
    .int 10, 20, 30, 40, 50, -1
buffer:
    .ascii "                    \n"  # 20 пробелов + \n

.section .text
.global _start

_start:
    # Вычисление суммы
    lea numbers(%rip), %rsi
    xor %eax, %eax

sum_loop:
    mov (%rsi), %ebx
    cmp $-1, %ebx
    je convert
    add %ebx, %eax
    add $4, %rsi
    jmp sum_loop

convert:
    # Подготовка к преобразованию
    lea buffer+19(%rip), %rdi  # Указатель на предпоследний байт буфера
    mov $10, %ecx
    xor %edx, %edx             # Флаг отрицательности

    # Проверка на отрицательное число
    test %eax, %eax
    jns .convert_loop
    neg %eax
    mov $1, %edx

.convert_loop:
    # Преобразование в строку
    xor %ebx, %ebx
    div %ecx
    add $'0', %bl
    mov %bl, (%rdi)
    dec %rdi
    test %eax, %eax
    jnz .convert_loop

    # Добавление знака минус
    test %edx, %edx
    jz .print
    movb $'-', (%rdi)
    dec %rdi

.print:
    # Корректный расчет длины
    lea buffer+20(%rip), %rsi  # Конец буфера
    sub %rdi, %rsi             # Длина = rsi - rdi
    inc %rsi                   # Компенсируем декремент

    # Системный вызов write
    mov $1, %rax               # sys_write
    mov $1, %rdi               # stdout
    lea 1(%rdi), %rsi          # Адрес начала строки (rdi + 1)
    mov %rsi, %rdx             # Длина строки
    syscall

    # Завершение программы
    mov $60, %rax              # sys_exit
    xor %rdi, %rdi
    syscall
