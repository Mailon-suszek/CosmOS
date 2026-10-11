class BarraSuperior {
    // barsup.grain: barra superior branca. Carregado em 0x30000 e executado
    // pelo face.grain via CosmOS.executar. Le a struct de tela (0x15000) do
    // video.dr e pinta as primeiras linhas do framebuffer em branco (BGRX).
    public static void main(String[] args) {
        int lfb   = CosmOS.ler32(0x15000);   // endereco linear do framebuffer
        int pitch = CosmOS.ler32(0x15004);   // bytes por linha
        int larg  = CosmOS.ler32(0x15008);   // largura em pixels

        int cor    = 0x00FFFFFF;             // branco (BGRX)
        int altura = 40;                     // altura da barra em pixels

        int y = 0;
        while (y < altura) {
            int x = 0;
            while (x < larg) {
                CosmOS.escrever32(lfb + y * pitch + x * 4, cor);
                x = x + 1;
            }
            y = y + 1;
        }
    }
}
