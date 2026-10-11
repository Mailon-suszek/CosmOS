;==============================================================================
; Cosm OS - Nucleo (nucleo-1.4.2026)
; org 0x0000 dentro do segmento 0x1000 (fisico 0x10000)
; prepara o hardware: modo de video via BIOS, A20, GDT,
; entra em modo protegido 32-bit, chama video.dr (0x8000) que configura a
; maior resolucao via VBE e retorna, inicializa o mouse PS/2 (hardware) e
; publica a rotina mouse_poll em 0x15020, e entrega o controle ao face.grain
; (0x20000) que por sua vez executa o barsup.grain (0x30000), o barinf.grain
; (0x40000), o menu.grain (0x50000) e, no loop de quadros, o mouse_poll e o
; mouse.dr (0x60000)
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

    ; inicializa o mouse PS/2 (hardware) e a posicao inicial do cursor
    call mouse_init
    mov  eax, [0x15008]        ; largura da tela (struct do video.dr)
    shr  eax, 1
    mov  [0x15100], eax
    mov  eax, [0x1500C]        ; altura da tela
    shr  eax, 1
    mov  [0x15104], eax
    mov  dword [0x15110], 0    ; cursor ainda nao desenhado (1o quadro)

    ; publica o endereco da rotina de polling (mouse_poll) para o face.grain
    mov  eax, mouse_poll + BASE
    mov  [0x15020], eax

    mov  eax, PAY_FACE
    jmp  eax

;----------------------------------------------------------------------------
; mouse_init: habilita a porta auxiliar do controlador PS/2 (mouse)
;----------------------------------------------------------------------------
mouse_init:
    mov  al, 0xA8              ; habilita o dispositivo auxiliar
    call ps2_cmd
    mov  al, 0xF6              ; seta defaults
    call mouse_cmd
    mov  al, 0xF3              ; seta taxa de amostragem...
    call mouse_cmd
    mov  al, 0x28              ; ...40 amostras/s (menos perda no polling)
    call mouse_cmd
    mov  al, 0xF4              ; habilita o report de dados
    call mouse_cmd
    ret

; ps2_cmd: espera o input buffer e escreve al na porta de comando (0x64)
ps2_cmd:
    push eax
    call ps2_wait_in
    mov  dx, 0x64
    pop  eax
    out  dx, al
    ret

; mouse_cmd: envia al ao mouse (prefixo 0xD4) e descarta o ACK (0xFA)
mouse_cmd:
    push eax
    call ps2_wait_in
    mov  dx, 0x64
    mov  al, 0xD4
    out  dx, al
    call ps2_wait_in
    mov  dx, 0x60
    pop  eax
    out  dx, al
    call ps2_wait_out
    mov  dx, 0x60
    in   al, dx
    ret

; ps2_wait_in: aguarda o input buffer ficar vazio (bit1 de 0x64 == 0)
ps2_wait_in:
    mov  dx, 0x64
.w: in   al, dx
    test al, 0x02
    jnz  .w
    ret

; ps2_wait_out: aguarda o output buffer encher (bit0 de 0x64 == 1)
ps2_wait_out:
    mov  dx, 0x64
.w: in   al, dx
    test al, 0x01
    jz   .w
    ret

;----------------------------------------------------------------------------
; mouse_poll: le os bytes pendentes do mouse PS/2 e atualiza a posicao do
; cursor em 0x15100/0x15104. Chamada pelo face.grain a cada quadro.
;----------------------------------------------------------------------------
mouse_poll:
    push eax
    push ebx
    push ecx
    push edx
.mloop:
    mov  dx, 0x64
    in   al, dx
    test al, 0x01
    jz   .done                 ; output buffer vazio: acabou
    test al, 0x20
    jz   .kbd                  ; byte do teclado: apenas descarta
    mov  dx, 0x60
    in   al, dx                ; byte do mouse em al
    mov  ebx, [0x15120]        ; indice do pacote (0..2)
    test ebx, ebx
    jnz  .have0
    test al, 0x08              ; 1o byte valido? (bit3 sempre 1)
    jz   .mloop
.have0:
    mov  ecx, 0x15124
    add  ecx, ebx
    mov  [ecx], al
    inc  ebx
    mov  [0x15120], ebx
    cmp  ebx, 3
    jne  .mloop
    ; pacote completo
    mov  dword [0x15120], 0
    mov  al, [0x15124]         ; flags
    test al, 0xC0              ; overflow em X ou Y: descarta
    jnz  .mloop
    movzx ebx, byte [0x15125]  ; dx
    test al, 0x10
    jz   .xpos
    or   ebx, 0xFFFFFF00
.xpos:
    movzx ecx, byte [0x15126]  ; dy
    test al, 0x20
    jz   .ypos
    or   ecx, 0xFFFFFF00
.ypos:
    mov  edx, [0x15100]
    add  edx, ebx
    cmp  edx, 6
    jge  .xok1
    mov  edx, 6
.xok1:
    cmp  edx, 793
    jle  .xok2
    mov  edx, 793
.xok2:
    mov  [0x15100], edx
    mov  edx, [0x15104]
    sub  edx, ecx              ; PS/2: Y cresce para cima
    cmp  edx, 6
    jge  .yok1
    mov  edx, 6
.yok1:
    cmp  edx, 593
    jle  .yok2
    mov  edx, 593
.yok2:
    mov  [0x15104], edx
    jmp  .mloop
.kbd:
    mov  dx, 0x60
    in   al, dx                ; descarta o byte do teclado
    jmp  .mloop
.done:
    pop  edx
    pop  ecx
    pop  ebx
    pop  eax
    ret

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