# Секция данных
.section .data
msg:
    .asciz "Hello, world!\n"  # Сообщение + символ новой строки
len = . - msg                # Длина сообщения

# Секция текста
.section .text
.global _start               # Глобальная точка входа

_start:
    # Системный вызов write (sys_write)
    mov $1, %rax              # syscall number for sys_write
    mov $1, %rdi              # file descriptor 1 (stdout)
    lea msg(%rip), %rsi       # адрес сообщения (RIP-относительная адресация)
    mov $len, %rdx            # длина сообщения
    syscall                   # вызов системной функции

    # Системный вызов exit (sys_exit)
    mov $60, %rax             # syscall number for sys_exit
    xor %rdi, %rdi            # код возврата 0
    syscall                   # вызов системной функции
