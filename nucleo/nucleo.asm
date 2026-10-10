;==============================================================================
; Cosm OS - Nucleo (nucleo-1.1.2026)
; org 0x0000 dentro do segmento 0x1000 (fisico 0x10000)
; prepara o hardware: modo de video via BIOS, A20, GDT,
; salta para o modo protegido 32-bit e entrega face.grain (0x20000)
;==============================================================================
[bits 16]
[org 0x0000]

BASE     equ 0x10000          ; endereco linear onde o nucleo vive
PAY_FACE equ 0x20000

start:
    cli
    mov  ax, 0x1000           ; segmento base = fisico 0x10000
    mov  ds, ax
    mov  es, ax
    mov  ss, ax
    mov  sp, 0x3C00
    cld
    sti

    call serial_init
    mov  si, msg_s3
    call serial_print

    ; --- modo de video da BIOS: 80x25 texto colorido ---
    mov  ax, 0x0003
    int  0x10
    mov  si, msg_n1
    call serial_print
    ; esconde o cursor
    mov  ax, 0x0100
    mov  cx, 0x2000
    int  0x10

    ; --- paleta: DAC 0x39 = azul claro (sky blue) ---
    mov  dx, 0x3C8
    mov  al, 0x39
    out  dx, al
    inc  dx
    mov  al, 33               ; R (0-63)
    out  dx, al
    mov  al, 51               ; G (0-63)
    out  dx, al
    mov  al, 58               ; B (0-63)
    out  dx, al

    ; --- habilita A20 (via fast gate) ---
    in   al, 0x92
    or   al, 0x02
    out  0x92, al
    mov  si, msg_a20
    call serial_print

    ; --- carrega a GDT ---
    lgdt [gdt_desc]
    mov  si, msg_n4
    call serial_print

    ; --- pula para o modo protegido ---
    cli
    mov  eax, cr0
    or   al, 1
    mov  cr0, eax

    jmp  dword 0x08:(modo32 + BASE)

;----------------------------------------------------------------------------
[bits 32]
modo32:
    mov  ax, 0x10
    mov  ds, ax
    mov  es, ax
    mov  fs, ax
    mov  gs, ax
    mov  ss, ax
    mov  esp, 0x90000

    ; liga o serial 32-bit e anuncia
    call serial_init32
    mov  esi, msg_pm + BASE
    call serial_print32

    ; entrega o controle ao face.grain
    mov  eax, PAY_FACE
    jmp  eax

;----------------------------------------------------------------------------
; serial 32-bit (COM1)
;----------------------------------------------------------------------------
serial_init32:
    mov  dx, 0x3FB
    mov  al, 0x80
    out  dx, al
    mov  dx, 0x3F8
    mov  al, 0x01
    out  dx, al
    mov  dx, 0x3F9
    mov  al, 0x00
    out  dx, al
    mov  dx, 0x3FB
    mov  al, 0x03
    out  dx, al
    ret

serial_print32:
    lodsb
    or   al, al
    jz   .done
    mov  dx, 0x3FD
.waits:
    in   al, dx
    test al, 0x20
    jz   .waits
    mov  dx, 0x3F8
    mov  al, [esi-1]
    out  dx, al
    jmp  serial_print32
.done:
    ret

;----------------------------------------------------------------------------
; serial 16-bit (COM1)
;----------------------------------------------------------------------------
[bits 16]
serial_init:
    push ax
    push dx
    mov  dx, 0x3FB
    mov  al, 0x80
    out  dx, al
    mov  dx, 0x3F8
    mov  al, 0x01
    out  dx, al
    mov  dx, 0x3F9
    mov  al, 0x00
    out  dx, al
    mov  dx, 0x3FB
    mov  al, 0x03
    out  dx, al
    pop  dx
    pop  ax
    ret

serial_print:
    lodsb
    or   al, al
    jz   .done
    call serial_char
    jmp  serial_print
.done:
    ret

serial_char:
    push ax
    push dx
    mov  ah, al
.sc_wait:
    mov  dx, 0x3FD
    in   al, dx
    test al, 0x20
    jz   .sc_wait
    mov  al, ah
    mov  dx, 0x3F8
    out  dx, al
    pop  dx
    pop  ax
    ret

;----------------------------------------------------------------------------
align 16
GDT:
    dq 0x0000000000000000
    ; code: base 0, limit 0xFFFFF, present, DPL0, exec/read, gran4K, 32bit
    dw 0xFFFF, 0x0000
    db 0x00, 0x9A, 0xCF, 0x00
    ; data: base 0, limit 0xFFFFF, present, DPL0, read/write, gran4K, 32bit
    dw 0xFFFF, 0x0000
    db 0x00, 0x92, 0xCF, 0x00

gdt_desc:
    dw GDT_end - GDT - 1
    dd GDT + BASE

GDT_end:

msg_s3:  db "COSM[S3] nucleo iniciou, preparando hardware", 13, 10, 0
msg_a20: db "COSM[S3] A20 habilitado", 13, 10, 0
msg_n1:  db "COSM[S3] video mode 80x25 via BIOS", 13, 10, 0
msg_n4:  db "COSM[S3] GDT carregada, indo para modo protegido", 13, 10, 0

[bits 32]
msg_pm:  db "COSM[S3] modo protegido, entregando ao face.grain", 13, 10, 0