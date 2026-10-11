;----------------------------------------------------------------------------
; Cosm OS - stage 2
;   org 0x6000, modo real. Entra com DL = unidade de boot.
;   Le o CD (INT 13h AH=42h), navega a arvore ISO9660 (Rock Ridge 'NM')
;   e carrega arquivos SEPARADOS da ISO:
;     /nucleo/nucleo-1.3.2026  -> 0x10000
;     /interface/face.grain    -> 0x20000
;     /interface/brasup.grain  -> 0x30000
;     /interface/barinf.grain  -> 0x40000
;     /interface/menu.grain    -> 0x50000
;     /drivers/video.dr        -> 0x08000
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

    ; ---- nucleo ----
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

    ; ---- brasup.grain (barra superior, carregada em 0x30000) ----
    mov  si, nb_brasup + 1
    movzx cx, byte [nb_brasup]
    call find_in_dir
    mov  si, ax
    mov  cx, dx
    push es
    mov  ax, 0x3000
    mov  es, ax
    xor  di, di
    call read_file
    pop  es

    ; ---- barinf.grain (barra inferior, carregada em 0x40000) ----
    mov  si, nb_barinf + 1
    movzx cx, byte [nb_barinf]
    call find_in_dir
    mov  si, ax
    mov  cx, dx
    push es
    mov  ax, 0x4000
    mov  es, ax
    xor  di, di
    call read_file
    pop  es

    ; ---- menu.grain (retangulo cinza, carregado em 0x50000) ----
    mov  si, nb_menu + 1
    movzx cx, byte [nb_menu]
    call find_in_dir
    mov  si, ax
    mov  cx, dx
    push es
    mov  ax, 0x5000
    mov  es, ax
    xor  di, di
    call read_file
    pop  es

    ; ---- video.dr (carrega por ultimo: usa 0x8000 como buffer) ----
    call load_cd_root
    mov  si, nb_drivers + 1
    movzx cx, byte [nb_drivers]
    call find_in_dir
    mov  si, ax
    mov  di, 0x8000
    call read_sector
    mov  si, nb_video + 1
    movzx cx, byte [nb_video]
    call find_in_dir
    mov  si, ax
    mov  cx, dx
    push es
    mov  ax, 0x0800
    mov  es, ax
    xor  di, di
    call read_file
    pop  es

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
bootdrv:       db 0
nb_nucleo:     db 6, "nucleo"
nb_interface:  db 9, "interface"
nb_n1:         db 15, "nucleo-1.3.2026"
nb_face:       db 10, "face.grain"
nb_brasup:     db 12, "brasup.grain"
nb_barinf:     db 12, "barinf.grain"
nb_menu:       db 10, "menu.grain"
nb_drivers:    db 7, "drivers"
nb_video:      db 8, "video.dr"
