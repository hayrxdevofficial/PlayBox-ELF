.section .text
.global _start
.type _start, @function
_start:
    movabs $0x504c4159424f58, %rax
    ret
.size _start, .-_start
