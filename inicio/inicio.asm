;----------------------------------------------------------------------------
; Cosm OS - stage 2
;   org 0x6000, modo real. Entra com DL = unidade de boot.
;   Le o CD (INT 13h AH=42h), navega a arvore ISO9660 (Rock Ridge 'NM')
;   e carrega arquivos SEPARADOS da ISO:
;     /nucleo/nucleo-1.1.2026  -> 0x10000
;     /interface/face.grain    -> 0x20000
;   Depois pula para o nucleo em 0x1000:0x0000.
;----------------------------------------------------------------------------
[org 0x6000]
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
    mov  si, m_s1
    call serial_print

    ; ---- nucleo ----
    mov  si, m_s2
    call serial_print
    call load_cd_root
    mov  si, nb_nucleo + 1
    movzx cx, byte [nb_nucleo]
    call find_in_dir
    mov  si, ax
    mov  di, 0x8000
    call read_sector
    mov  si, nb_n1 + 1
    movzx cx, byte [nb_n1]
    call find_in_dir
    mov  si, ax
    mov  cx, dx
    push es
    mov  ax, 0x1000
    mov  es, ax
    xor  di, di
    call read_file
    pop  es

    ; ---- face.grain ----
    mov  si, m_s3
    call serial_print
    call load_cd_root
    mov  si, nb_interface + 1
    movzx cx, byte [nb_interface]
    call find_in_dir
    mov  si, ax
    mov  di, 0x8000
    call read_sector
    mov  si, nb_face + 1
    movzx cx, byte [nb_face]
    call find_in_dir
    mov  si, ax
    mov  cx, dx
    push es
    mov  ax, 0x2000
    mov  es, ax
    xor  di, di
    call read_file
    pop  es

    mov  si, m_sj
    call serial_print

    mov  dl, [bootdrv]
    jmp  0x1000:0x0000

;---------------------------------------------------------------------------
; load_cd_root: le PVD (setor 16) e o diretorio raiz em 0x8000
;---------------------------------------------------------------------------
load_cd_root:
    mov  si, 16
    mov  di, 0x8000
    call read_sector
    mov  ax, [0x8000 + 158]
    mov  si, ax
    mov  di, 0x8000
    call read_sector
    ret

;---------------------------------------------------------------------------
; read_file: si=LBA, cx=size, di=buffer. Le ceil(size/2048) setores do CD
;---------------------------------------------------------------------------
read_file:
    push ax
    add  cx, 2047
    shr  cx, 11
.rf:
    push si
    call read_sector
    pop  si
    inc  si
    add  di, 2048
    dec  cx
    jnz  .rf
    pop  ax
    ret

;---------------------------------------------------------------------------
; read_sector: si=LBA, di=buffer (CD 2048 B/setor, INT13 AH=42)
;---------------------------------------------------------------------------
read_sector:
    mov  bp, 0x500
    mov  byte [bp + 0], 0x10
    mov  byte [bp + 1], 0x00
    mov  word [bp + 2], 0x0001
    mov  word [bp + 4], di
    mov  word [bp + 6], es
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
    mov  si, m_errd
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
bootdrv:       db 0
m_s1:          db "COSM[S2] stage2 ok, lendo CD", 13, 10, 0
m_s2:          db "COSM[S2] carregando nucleo-1.1.2026", 13, 10, 0
m_s3:          db "COSM[S2] carregando face.grain", 13, 10, 0
m_sj:          db "COSM[S2] ->J para 0x10000", 13, 10, 0
m_errd:        db "COSM[S2] ERRO de leitura", 13, 10, 0
nb_nucleo:     db 6, "nucleo"
nb_interface:  db 9, "interface"
nb_n1:         db 15, "nucleo-1.1.2026"
nb_face:       db 10, "face.grain"