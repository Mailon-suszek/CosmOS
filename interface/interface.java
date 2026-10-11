class Face {
    // O proprio face.grain define a cor da tela. Le a struct de tela (0x15000)
    // preenchida pelo video.dr e pinta o framebuffer inteiro em 32bpp (BGRX).
    public static void main(String[] args) {
        int lfb   = CosmOS.ler32(0x15000);   // endereco linear do framebuffer
        int pitch = CosmOS.ler32(0x15004);   // bytes por linha
        int larg  = CosmOS.ler32(0x15008);   // largura em pixels
        int alt   = CosmOS.ler32(0x1500C);   // altura em pixels

        int cor = 0x0087CFE8;                // azul claro (BGRX)

        int y = 0;
        while (y < alt) {
            int x = 0;
            while (x < larg) {
                CosmOS.escrever32(lfb + y * pitch + x * 4, cor);
                x = x + 1;
            }
            y = y + 1;
        }

        // executa o barsup.grain (0x30000): barra superior branca
        CosmOS.executar(0x30000);

        // executa o barinf.grain (0x40000): barra inferior branca
        CosmOS.executar(0x40000);

        // executa o menu.grain (0x50000): retangulo cinza no lado direito
        CosmOS.executar(0x50000);

        CosmOS.parar();
    }
}
