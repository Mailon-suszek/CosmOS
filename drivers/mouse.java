class Mouse {
    // mouse.dr: cursor do mouse (bolinha vermelha) com save/restore do fundo.
    // A posicao (0x15100/0x15104) e atualizada pelo nucleo (mouse_poll), que
    // le o hardware PS/2. O face.grain chama o poll e depois este driver a
    // cada quadro. O fundo sob o cursor e salvo em 0x15400 e restaurado no
    // quadro seguinte, deixando o cursor "flutuar" sem deixar rastro.
    public static void main(String[] args) {
        int lfb   = CosmOS.ler32(0x15000);   // endereco linear do framebuffer
        int pitch = CosmOS.ler32(0x15004);   // bytes por linha

        int cor  = 0x00FF0000;               // vermelho (BGRX)
        int raio = 6;                        // raio da bolinha em pixels
        int tam  = raio + raio + 1;          // lado da caixa do cursor (13)
        int r2   = raio * raio;              // raio ao quadrado

        int cx = CosmOS.ler32(0x15100);      // posicao atual (nucleo)
        int cy = CosmOS.ler32(0x15104);
        int px = CosmOS.ler32(0x15108);      // posicao anterior
        int py = CosmOS.ler32(0x1510C);
        int iniciado = CosmOS.ler32(0x15110);

        // restaura o fundo onde o cursor estava no quadro anterior
        if (iniciado != 0) {
            int ry = 0;
            while (ry < tam) {
                int rx = 0;
                while (rx < tam) {
                    int off = (py - raio + ry) * pitch + (px - raio + rx) * 4;
                    int idx = (ry * tam + rx) * 4;
                    CosmOS.escrever32(lfb + off, CosmOS.ler32(0x15400 + idx));
                    rx = rx + 1;
                }
                ry = ry + 1;
            }
        }

        // salva o fundo da nova posicao e desenha a bolinha por cima
        int ly = 0;
        while (ly < tam) {
            int lx = 0;
            while (lx < tam) {
                int dx   = lx - raio;
                int dy   = ly - raio;
                int off  = (cy + dy) * pitch + (cx + dx) * 4;
                int idx  = (ly * tam + lx) * 4;
                CosmOS.escrever32(0x15400 + idx, CosmOS.ler32(lfb + off));
                if (dx * dx + dy * dy <= r2) {
                    CosmOS.escrever32(lfb + off, cor);
                }
                lx = lx + 1;
            }
            ly = ly + 1;
        }

        CosmOS.escrever32(0x15108, cx);      // guarda a posicao desenhada
        CosmOS.escrever32(0x1510C, cy);
        CosmOS.escrever32(0x15110, 1);       // cursor ja desenhado uma vez
    }
}
