;----------------------------------------------------------------------------
; Cosm OS - stage 1 (setor de boot, El Torito)
;   org 0x7C00, modo real, deve caber em 512 bytes
;   Le o CD (INT 13h AH=42h), navega a arvore ISO9660 (Rock Ridge 'NM')
;   e localiza /inicio/inicio.cos por nome, carrega em 0x6000 e pula.
;   Os arquivos ficam SEPARADOS na ISO (nada empacotado no boot.img).
;----------------------------------------------------------------------------
[org 0x7C00]
[bits 16]

start:
    cli
    xor  ax, ax
    mov  ds, ax
    mov  es, ax
    mov  ss, ax
    mov  sp, 0x7C00
    sti
    mov  [bootdrv], dl

    call serial_init
    mov  si, m_b1
    call serial_print

    ; PVD (setor 16) -> 0x8000
    mov  si, 16
    mov  di, 0x8000
    call read_sector

    ; dir raiz (extent LE no PVD+158)
    mov  ax, [0x8000 + 158]
    mov  si, ax
    mov  di, 0x8000
    call read_sector

    ; achar o diretorio "inicio"
    mov  si, nb_inicio + 1
    movzx cx, byte [nb_inicio]
    call find_in_dir
    mov  si, ax
    mov  di, 0x8000
    call read_sector

    ; achar o arquivo "inicio.cos"
    mov  si, nb_ic + 1
    movzx cx, byte [nb_ic]
    call find_in_dir
    mov  di, 0x6000
    mov  si, ax
    call read_sector

    mov  si, m_b3
    call serial_print

    mov  dl, [bootdrv]
    jmp  0x0000:0x6000

;---------------------------------------------------------------------------
; read_sector: si=LBA, di=buffer (CD 2048 B/setor, INT13 AH=42)
;---------------------------------------------------------------------------
read_sector:
    mov  bp, 0x500
    mov  byte [bp + 0], 0x10
    mov  byte [bp + 1], 0x00
    mov  word [bp + 2], 0x0001
    mov  word [bp + 4], di
    mov  word [bp + 6], 0x0000
    mov  word [bp + 8], si
    mov  word [bp + 10], 0x0000
    mov  word [bp + 12], 0x0000
    mov  word [bp + 14], 0x0000
    mov  dl, [bootdrv]
    mov  si, bp
    mov  ah, 0x42
    int  0x13
    jnc  .ok
    mov  dl, 0xE0
    mov  [bootdrv], dl
    int  0x13
    jc   .err
.ok:
    ret
.err:
    mov  si, m_err
    call serial_print
    hlt
    jmp  .err

;---------------------------------------------------------------------------
; find_in_dir: si=esperado, cx=len -> ax=extent, dx=size
;   percorre o diretorio em 0x8000, casa o nome Rock Ridge 'NM'
;---------------------------------------------------------------------------
find_in_dir:
    push si
    push cx
    mov  bp, sp
    mov  bx, 0x8000
.rec:
    movzx ax, byte [bx]
    test al, al
    jz   .notfound
    movzx si, byte [bx + 32]
    cmp  si, 1
    jbe  .nextrec
    ; sysarea = 33 + nid + (nid&1)
    movzx cx, byte [bx + 32]
    mov  ax, cx
    and  ax, 1
    xor  ax, 1
    add  ax, cx
    add  ax, 33
    mov  si, bx
    add  si, ax
    movzx cx, byte [bx]
    mov  di, bx
    add  di, cx
.nm:
    mov  al, [si]
    cmp  al, 'N'
    jne  .nmx
    cmp  byte [si + 1], 'M'
    je   .found
.nmx:
    inc  si
    cmp  si, di
    jb   .nm
.nextrec:
    movzx ax, byte [bx]
    add  bx, ax
    jmp  .rec
.found:
    mov  cx, [bp]
    mov  di, [bp + 2]
    lea  si, [si + 5]
.cmp:
    dec  cx
    js   .matched
    mov  al, [di]
    inc  di
    cmp  al, [si]
    jne  .nextrec
    inc  si
    jmp  .cmp
.matched:
    mov  ax, [bx + 2]
    mov  dx, [bx + 10]
    add  sp, 4
    ret
.notfound:
    xor  ax, ax
    add  sp, 4
    ret

;---------------------------------------------------------------------------
; serial COM1 16-bit (debug)
;---------------------------------------------------------------------------
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
    xor  al, al
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

;---------------------------------------------------------------------------
bootdrv:   db 0
m_b1:      db "COSM[B1] lendo PVD/raiz", 13, 10, 0
m_b3:      db "COSM[B1] inicio.cos em 0x6000", 13, 10, 0
m_err:     db "COSM[B1] ERRO de leitura", 13, 10, 0
nb_inicio: db 6, "inicio"
nb_ic:     db 10, "inicio.cos"

times 510 - ($ - $$) db 0
dw 0xAA55