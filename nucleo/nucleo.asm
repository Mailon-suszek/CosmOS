;==============================================================================
; Cosm OS - Nucleo (nucleo-1.3.2026)
; org 0x0000 dentro do segmento 0x1000 (fisico 0x10000)
; prepara o hardware: modo de video via BIOS, A20, GDT,
; entra em modo protegido 32-bit, chama video.dr (0x8000) que configura a
; maior resolucao via VBE e retorna, e entrega o controle ao face.grain (0x20000)
; que por sua vez executa o barsup.grain (0x30000), o barinf.grain (0x40000)
; e o menu.grain (0x50000)
;==============================================================================
[bits 16]
[org 0x0000]

BASE      equ 0x10000         ; endereco linear onde o nucleo vive
PAY_VIDEO equ 0x8000          ; driver de video (video.dr)
PAY_FACE  equ 0x20000         ; interface (face.grain)

start:
    cli
    mov  ax, 0x1000           ; segmento base = fisico 0x10000
    mov  ds, ax
    mov  es, ax
    mov  ss, ax
    mov  sp, 0x3C00
    cld
    sti

    ; --- modo de video da BIOS: 80x25 texto colorido ---
    mov  ax, 0x0003
    int  0x10
    ; esconde o cursor
    mov  ax, 0x0100
    mov  cx, 0x2000
    int  0x10

    ; --- VBE: coleta o controller info (4F00) em 0x14000 p/ o video.dr ---
    push es
    mov  ax, 0x1400
    mov  es, ax
    xor  di, di
    mov  dword [es:0], 0x32454256   ; assinatura "VBE2"
    mov  ax, 0x4F00
    int  0x10
    pop  es

    ; --- habilita A20 (via fast gate) ---
    in   al, 0x92
    or   al, 0x02
    out  0x92, al

    ; --- carrega a GDT ---
    lgdt [gdt_desc]

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

    ; chama o driver de video (0x8000): ele configura a maior resolucao via
    ; VBE e RETORNA. Depois o nucleo entrega o controle ao face.grain (0x20000).
    mov  eax, PAY_VIDEO
    call eax

    mov  eax, PAY_FACE
    jmp  eax

;----------------------------------------------------------------------------
[bits 16]
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
