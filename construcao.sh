#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD="/tmp/cosmbuild"
ISO="$BUILD/iso"

rm -rf "$BUILD"
mkdir -p "$BUILD/classes"

echo "== [1/5] NASM (saida direto nas pastas do projeto) =="
nasm -f bin -o "$ROOT/inicio/inimin.cos"      "$ROOT/inicio/inicio_minimo.asm"
nasm -f bin -o "$ROOT/inicio/inicio.cos"      "$ROOT/inicio/inicio.asm"
nasm -f bin -o "$ROOT/nucleo/nucleo-1.2.2026" "$ROOT/nucleo/nucleo.asm"

echo "== [2/5] javac + AOT (.class fica no /tmp) =="
javac -d "$BUILD/classes" \
    "$ROOT/AOT.java" \
    "$ROOT/CosmOS.java" \
    "$ROOT/interface/interface.java" \
    "$ROOT/drivers/video.java"
mkdir -p "$ROOT/drivers"
java -cp "$BUILD/classes" AOT "$BUILD/classes/Face.class"  "$ROOT/interface/face.grain"
java -cp "$BUILD/classes" AOT "$BUILD/classes/Video.class" "$ROOT/drivers/video.dr"

echo "== [3/5] estrutura da ISO (arquivos separados) =="
mkdir -p "$ISO/inicio" "$ISO/nucleo" "$ISO/interface" "$ISO/drivers"
cp "$ROOT/inicio/inimin.cos"      "$ISO/inicio/inimin.cos"
cp "$ROOT/inicio/inicio.cos"      "$ISO/inicio/inicio.cos"
cp "$ROOT/nucleo/nucleo-1.2.2026" "$ISO/nucleo/nucleo-1.2.2026"
cp "$ROOT/interface/face.grain"   "$ISO/interface/face.grain"
cp "$ROOT/drivers/video.dr"       "$ISO/drivers/video.dr"

echo "== [4/5] genisoimage (boot = /inicio/inimin.cos, sem boot.img) =="
genisoimage -quiet -R -J -b inicio/inimin.cos -c boot.catalog \
    -no-emul-boot -boot-load-size 4 -input-charset ISO8859-1 \
    -hide boot.catalog -hide-joliet boot.catalog \
    -o "$ROOT/CosmOS.iso" "$ISO" 2>/dev/null

echo "== [5/5] CosmOS.iso pronto em $ROOT/CosmOS.iso =="

if [ "${1:-}" = "--run" ]; then
    echo "== iniciando QEMU =="
    qemu-system-i386 -cdrom "$ROOT/CosmOS.iso" -boot d -m 32
fi
