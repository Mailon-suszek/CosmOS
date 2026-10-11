class BarraInferior {
    // barinf.grain: barra inferior branca. Carregado em 0x40000 e executado
    // pelo face.grain via CosmOS.executar. Le a struct de tela (0x15000) do
    // video.dr e pinta as ultimas linhas do framebuffer em branco (BGRX).
    public static void main(String[] args) {
        int lfb   = CosmOS.ler32(0x15000);   // endereco linear do framebuffer
        int pitch = CosmOS.ler32(0x15004);   // bytes por linha
        int larg  = CosmOS.ler32(0x15008);   // largura em pixels
        int alt   = CosmOS.ler32(0x1500C);   // altura em pixels

        int cor    = 0x00FFFFFF;             // branco (BGRX)
        int altura = 40;                     // altura da barra em pixels

        int y = alt - altura;
        while (y < alt) {
            int x = 0;
            while (x < larg) {
                CosmOS.escrever32(lfb + y * pitch + x * 4, cor);
                x = x + 1;
            }
            y = y + 1;
        }
    }
}
