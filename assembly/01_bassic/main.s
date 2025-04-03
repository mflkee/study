.section .data
msg:
    .asciz "Hello, world!\n"
len = . - msg

.section .text
.global _start

_start:
    # Вывод сообщения
    mov $1, %rax
    mov $1, %rdi
    lea msg(%rip), %rsi
    mov $len, %rdx
    syscall

    # Завершение
    mov $60, %rax
    xor %rdi, %rdi
    syscall
