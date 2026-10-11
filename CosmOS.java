class CosmOS {
    // intrinsecos nativos do Cosm OS: o compilador AOT gera o codigo x86.

    // acesso a memoria fisica (endereco linear)
    static int ler8(int e)   { return 0; }
    static int ler16(int e)  { return 0; }
    static int ler32(int e)  { return 0; }
    static void escrever8(int e, int v)  {}
    static void escrever16(int e, int v) {}
    static void escrever32(int e, int v) {}

    // VBE via BIOS (thunk modo protegido <-> modo real)
    static int vbeInfo(int modo) { return 0; }
    static void vbeSetar(int modo) {}

    // encerra a CPU (cli; hlt)
    static void parar() {}
}
