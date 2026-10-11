class Menu {
    // menu.grain: retangulo cinza pequeno no centro do lado direito da tela.
    // Carregado em 0x50000 e executado pelo face.grain via CosmOS.executar.
    // Le a struct de tela (0x15000) do video.dr.
    public static void main(String[] args) {
        int lfb   = CosmOS.ler32(0x15000);   // endereco linear do framebuffer
        int pitch = CosmOS.ler32(0x15004);   // bytes por linha
        int larg  = CosmOS.ler32(0x15008);   // largura em pixels
        int alt   = CosmOS.ler32(0x1500C);   // altura em pixels

        int cor      = 0x00808080;           // cinza (BGRX)
        int larg_ret = 70;                   // largura do retangulo (metade)
        int alt_ret  = 120;                  // altura do retangulo

        int x0 = larg - larg_ret;            // encostado na lateral direita
        int y0 = (alt - alt_ret) >>> 1;      // centro vertical

        int y = y0;
        while (y < y0 + alt_ret) {
            int x = x0;
            while (x < x0 + larg_ret) {
                CosmOS.escrever32(lfb + y * pitch + x * 4, cor);
                x = x + 1;
            }
            y = y + 1;
        }
    }
}
