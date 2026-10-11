class Video {
    public static void main(String[] args) {
        int off = CosmOS.ler16(0x1400E);
        int seg = CosmOS.ler16(0x14010);
        int lista = (seg << 4) + off;
        int melhor = 0xFFFF;
        int p = 0;
        while (true) {
            int modo = CosmOS.ler16(lista + p);
            if (modo == 0xFFFF) {
                break;
            }
            p = p + 2;
            if (CosmOS.vbeInfo(modo) == 0) {
                continue;
            }
            int attr = CosmOS.ler8(0x14200);
            if ((attr & 0x80) == 0) {
                continue;
            }
            int mm = CosmOS.ler8(0x1421B);
            if (mm != 4 && mm != 6) {
                continue;
            }
            if (CosmOS.ler8(0x14219) != 32) {
                continue;
            }
            int xres = CosmOS.ler16(0x14212);
            int yres = CosmOS.ler16(0x14214);
            if (xres != 800 || yres != 600) {
                continue;
            }
            int pitch = CosmOS.ler16(0x14232);
            if (pitch == 0) {
                pitch = CosmOS.ler16(0x14210);
            }
            int total = CosmOS.ler16(0x14012) << 16;
            if (pitch * yres > total) {
                continue;
            }
            melhor = modo;
            break;
        }
        if (melhor != 0xFFFF) {
            CosmOS.vbeSetar(melhor);
            CosmOS.vbeInfo(melhor);
            CosmOS.escrever32(0x15000, CosmOS.ler32(0x14228));
            int pt = CosmOS.ler16(0x14232);
            if (pt == 0) {
                pt = CosmOS.ler16(0x14210);
            }
            CosmOS.escrever32(0x15004, pt);
            CosmOS.escrever32(0x15008, CosmOS.ler16(0x14212));
            CosmOS.escrever32(0x1500C, CosmOS.ler16(0x14214));
            CosmOS.escrever32(0x15010, CosmOS.ler8(0x14219));
        }
    }
}
