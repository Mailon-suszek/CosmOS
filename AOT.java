import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

public class AOT {

    private static Object[] cp;

    // onde o driver de video e carregado e onde vive o runtime dos intrinsecos
    private static final int BASE     = 0x8000;
    private static final int RUNTIME_AT = 0x9000;
    private static final int VBE_INFO = 0x9000;
    private static final int VBE_SET  = 0x9100;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("uso: AOT <Classe.class> <saida>");
            System.exit(1);
        }
        byte[] cls = Files.readAllBytes(Paths.get(args[0]));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        translate(cls, out);
        Files.write(Paths.get(args[1]), out.toByteArray());
        System.err.println(args[1] + " gerado (" + out.size() + " bytes)");
    }

    private static void translate(byte[] cls, ByteArrayOutputStream out) throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(cls));
        if (in.readInt() != 0xCAFEBABE) throw new RuntimeException("nao e um classfile");
        in.readUnsignedShort();
        in.readUnsignedShort();
        readConstantPool(in);
        in.readUnsignedShort();
        className(in.readUnsignedShort()); // this_class
        in.readUnsignedShort();
        int ic = in.readUnsignedShort();
        for (int i = 0; i < ic; i++) in.readUnsignedShort();
        int fc = in.readUnsignedShort();
        for (int i = 0; i < fc; i++) {
            in.readUnsignedShort();
            in.readUnsignedShort();
            in.readUnsignedShort();
            int ac = in.readUnsignedShort();
            for (int j = 0; j < ac; j++) skipAttr(in);
        }
        int mc = in.readUnsignedShort();
        for (int i = 0; i < mc; i++) {
            in.readUnsignedShort();
            String name = utf8(in.readUnsignedShort());
            String desc = utf8(in.readUnsignedShort());
            int ac = in.readUnsignedShort();
            byte[] code = null;
            for (int j = 0; j < ac; j++) {
                String an = utf8(in.readUnsignedShort());
                int len = in.readInt();
                byte[] buf = new byte[len];
                in.readFully(buf);
                if (an.equals("Code")) code = buf;
            }
            if (name.equals("main") && desc.equals("([Ljava/lang/String;)V")) {
                byte[] bc = extractCode(code);
                emitFull(bc, out);
            }
        }
    }

    private static byte[] extractCode(byte[] attr) throws Exception {
        DataInputStream cin = new DataInputStream(new ByteArrayInputStream(attr));
        cin.readUnsignedShort();
        cin.readUnsignedShort();
        int len = cin.readInt();
        byte[] code = new byte[len];
        cin.readFully(code);
        return code;
    }

    //--------------------------------------------------------------- driver (completo)
    private static void emitFull(byte[] code, ByteArrayOutputStream out) {
        int len = code.length;

        // pass A: maior slot de variavel local
        int maxLocal = -1;
        for (int pc = 0; pc < len; ) {
            int op = code[pc] & 0xFF;
            int idx = -1;
            if (op == 0x15 || op == 0x36) idx = code[pc + 1] & 0xFF;
            else if (op >= 0x1A && op <= 0x1D) idx = op - 0x1A;
            else if (op >= 0x3B && op <= 0x3E) idx = op - 0x3B;
            if (idx > maxLocal) maxLocal = idx;
            pc += bcLen(code, pc);
        }
        int nlocals = maxLocal + 1;
        if (nlocals < 1) nlocals = 1;

        // pass B: mapeia pc (bytecode) -> offset x86
        int[] x86At = new int[len + 1];
        Arrays.fill(x86At, -1);
        int xo = prologueLen();
        for (int pc = 0; pc < len; ) {
            x86At[pc] = xo;
            xo += instrLen(code, pc);
            pc += bcLen(code, pc);
        }

        // prologo: push ebp; mov ebp,esp; sub esp, nlocals*4
        out.write(0x55);
        out.write(0x89); out.write(0xE5);
        out.write(0x81); out.write(0xEC); writeLE32(out, nlocals * 4);

        // pass C: emite
        for (int pc = 0; pc < len; ) {
            emitInstr(code, pc, out, x86At);
            pc += bcLen(code, pc);
        }

        // padding ate o runtime e anexa o runtime dos intrinsecos
        while (out.size() < RUNTIME_AT - BASE) out.write(0);
        if (out.size() != RUNTIME_AT - BASE)
            throw new RuntimeException("codigo do driver passou dos " + (RUNTIME_AT - BASE) + " bytes reservados");
        for (int b : RUNTIME) out.write(b);
    }

    private static int prologueLen() { return 1 + 2 + 6; }

    private static int instrLen(byte[] code, int pc) {
        ByteArrayOutputStream tmp = new ByteArrayOutputStream();
        emitInstr(code, pc, tmp, null);   // x86At=null -> sem alvos reais
        return tmp.size();
    }

    private static int bcLen(byte[] code, int pc) {
        int op = code[pc] & 0xFF;
        switch (op) {
            case 0x10: case 0x12: case 0x15: case 0x36: return 2;
            case 0x11: return 3;
            case 0x99: case 0x9A: case 0x9B: case 0x9C: case 0x9D: case 0x9E:
            case 0x9F: case 0xA0: case 0xA1: case 0xA2: case 0xA3: case 0xA4:
            case 0xA7: case 0xB8: return 3;
            default: return 1;
        }
    }

    private static void emitInstr(byte[] code, int pc, ByteArrayOutputStream out, int[] x86At) {
        int op = code[pc] & 0xFF;
        switch (op) {
            case 0x00: break;
            case 0x02: pushImm(out, -1); break;
            case 0x03: pushImm(out, 0); break;
            case 0x04: pushImm(out, 1); break;
            case 0x05: pushImm(out, 2); break;
            case 0x06: pushImm(out, 3); break;
            case 0x07: pushImm(out, 4); break;
            case 0x08: pushImm(out, 5); break;
            case 0x10: pushImm(out, (byte) code[pc + 1]); break;                       // bipush
            case 0x11: pushImm(out, (short)(((code[pc+1]&0xFF)<<8)|(code[pc+2]&0xFF))); break; // sipush
            case 0x12: pushImm(out, ((CpInt) cp[code[pc + 1] & 0xFF]).v); break;       // ldc int

            case 0x15: pushLocal(out, code[pc + 1] & 0xFF); break;
            case 0x1A: pushLocal(out, 0); break;
            case 0x1B: pushLocal(out, 1); break;
            case 0x1C: pushLocal(out, 2); break;
            case 0x1D: pushLocal(out, 3); break;
            case 0x36: popLocal(out, code[pc + 1] & 0xFF); break;
            case 0x3B: popLocal(out, 0); break;
            case 0x3C: popLocal(out, 1); break;
            case 0x3D: popLocal(out, 2); break;
            case 0x3E: popLocal(out, 3); break;

            case 0x57: out.write(0x83); out.write(0xC4); out.write(0x04); break;        // pop
            case 0x60: binOp(out, 0x01, 0xD8); break;   // iadd  (add eax,ebx)
            case 0x64: binOp(out, 0x29, 0xD8); break;   // isub
            case 0x68: out.write(0x5B); out.write(0x58);
                       out.write(0x0F); out.write(0xAF); out.write(0xC3);
                       out.write(0x50); break;          // imul
            case 0x78: shiftOp(out, 0xE0); break;        // ishl
            case 0x7C: shiftOp(out, 0xE8); break;        // iushr
            case 0x7E: binOp(out, 0x21, 0xD8); break;    // iand
            case 0x80: binOp(out, 0x09, 0xD8); break;    // ior
            case 0x82: binOp(out, 0x31, 0xD8); break;    // ixor

            case 0x99: cmpZero(out, code, pc, x86At, 0x84); break; // ifeq
            case 0x9A: cmpZero(out, code, pc, x86At, 0x85); break; // ifne
            case 0x9B: cmpZero(out, code, pc, x86At, 0x8C); break; // iflt
            case 0x9C: cmpZero(out, code, pc, x86At, 0x8D); break; // ifge
            case 0x9D: cmpZero(out, code, pc, x86At, 0x8F); break; // ifgt
            case 0x9E: cmpZero(out, code, pc, x86At, 0x8E); break; // ifle

            case 0x9F: cmpTwo(out, code, pc, x86At, 0x84); break;  // if_icmpeq
            case 0xA0: cmpTwo(out, code, pc, x86At, 0x85); break;  // if_icmpne
            case 0xA1: cmpTwo(out, code, pc, x86At, 0x8C); break;  // if_icmplt
            case 0xA2: cmpTwo(out, code, pc, x86At, 0x8D); break;  // if_icmpge
            case 0xA3: cmpTwo(out, code, pc, x86At, 0x8F); break;  // if_icmpgt
            case 0xA4: cmpTwo(out, code, pc, x86At, 0x8E); break;  // if_icmple

            case 0xA7: out.write(0xE9); writeJump(out, code, pc, x86At); break; // goto
            case 0xAC: out.write(0x58); out.write(0xC9); out.write(0xC3); break; // ireturn
            case 0xB1: out.write(0xC9); out.write(0xC3); break;                  // return
            case 0xB8: emitFullInvoke(code, pc, out); break;

            default:
                throw new RuntimeException("bytecode nao suportado: 0x" + Integer.toHexString(op) + " pc=" + pc);
        }
    }

    private static void pushImm(ByteArrayOutputStream out, int v) {
        out.write(0x68); writeLE32(out, v);
    }

    private static void pushLocal(ByteArrayOutputStream out, int i) {
        out.write(0xFF); out.write(0x75); out.write((byte) (-(i + 1) * 4));
    }

    private static void popLocal(ByteArrayOutputStream out, int i) {
        out.write(0x8F); out.write(0x45); out.write((byte) (-(i + 1) * 4));
    }

    private static void binOp(ByteArrayOutputStream out, int b0, int b1) {
        out.write(0x5B); out.write(0x58); out.write(b0); out.write(b1); out.write(0x50);
    }

    private static void shiftOp(ByteArrayOutputStream out, int modrm) {
        out.write(0x59); out.write(0x58); out.write(0xD3); out.write(modrm); out.write(0x50);
    }

    private static void cmpZero(ByteArrayOutputStream out, byte[] code, int pc, int[] x86At, int cond) {
        out.write(0x58);                                   // pop eax
        out.write(0x83); out.write(0xF8); out.write(0x00); // cmp eax,0
        out.write(0x0F); out.write(cond);
        writeJump(out, code, pc, x86At);
    }

    private static void cmpTwo(ByteArrayOutputStream out, byte[] code, int pc, int[] x86At, int cond) {
        out.write(0x5B); out.write(0x58);                  // pop ebx; pop eax
        out.write(0x39); out.write(0xD8);                  // cmp eax,ebx
        out.write(0x0F); out.write(cond);
        writeJump(out, code, pc, x86At);
    }

    private static void writeJump(ByteArrayOutputStream out, byte[] code, int pc, int[] x86At) {
        int off = (short)(((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF));
        int tpc = pc + off;
        int relPos = out.size();
        int val = (x86At == null) ? 0 : (x86At[tpc] - (relPos + 4));
        writeLE32(out, val);
    }

    private static void writeCallRel(ByteArrayOutputStream out, int absTarget) {
        int relPos = out.size();
        writeLE32(out, absTarget - (BASE + relPos + 4));
    }

    private static void emitFullInvoke(byte[] code, int pc, ByteArrayOutputStream out) {
        int idx = ((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF);
        CpRef ref = (CpRef) cp[idx];
        String klass = className(ref.classIdx);
        CpNameAndType nat = (CpNameAndType) cp[ref.natIdx];
        String name = utf8(nat.nameIdx);
        if (name.equals("ler8"))             { out.write(0x58); out.write(0x0F); out.write(0xB6); out.write(0x00); out.write(0x50); }
        else if (name.equals("ler16"))       { out.write(0x58); out.write(0x0F); out.write(0xB7); out.write(0x00); out.write(0x50); }
        else if (name.equals("ler32"))       { out.write(0x58); out.write(0x8B); out.write(0x00); out.write(0x50); }
        else if (name.equals("escrever8"))   { out.write(0x59); out.write(0x58); out.write(0x88); out.write(0x08); }
        else if (name.equals("escrever16"))  { out.write(0x59); out.write(0x58); out.write(0x66); out.write(0x89); out.write(0x08); }
        else if (name.equals("escrever32"))  { out.write(0x59); out.write(0x58); out.write(0x89); out.write(0x08); }
        else if (name.equals("vbeInfo"))     { out.write(0x58); out.write(0xE8); writeCallRel(out, VBE_INFO); out.write(0x50); }
        else if (name.equals("vbeSetar"))    { out.write(0x58); out.write(0xE8); writeCallRel(out, VBE_SET); }
        else if (name.equals("parar"))       { out.write(0xFA); out.write(0xF4); }
        else if (name.equals("executar"))    { out.write(0x58); out.write(0xFF); out.write(0xD0); }
        else throw new RuntimeException("intrinseco nao suportado: " + klass + "." + name);
    }

    private static void writeLE32(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >> 8) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >> 24) & 0xFF);
    }

    //--------------------------------------------------------------- constant pool
    private static void readConstantPool(DataInputStream in) throws Exception {
        int count = in.readUnsignedShort();
        cp = new Object[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1:  { CpUtf8 e = new CpUtf8(); e.idx = i; cp[i] = e; e.s = readUtf8(in); break; }
                case 3:  cp[i] = new CpInt(in.readInt()); break;
                case 4:  in.readFloat(); break;
                case 5:  in.readLong(); i++; break;
                case 6:  in.readDouble(); i++; break;
                case 7:  cp[i] = new CpClass(in.readUnsignedShort()); break;
                case 8:  cp[i] = new CpString(in.readUnsignedShort()); break;
                case 9:
                case 10:
                case 11: cp[i] = new CpRef(in.readUnsignedShort(), in.readUnsignedShort()); break;
                case 12: cp[i] = new CpNameAndType(in.readUnsignedShort(), in.readUnsignedShort()); break;
                case 15: in.readUnsignedByte(); in.readUnsignedShort(); break;
                case 16: in.readUnsignedShort(); break;
                case 17:
                case 18: in.readUnsignedShort(); in.readUnsignedShort(); break;
                case 19:
                case 20: in.readUnsignedShort(); break;
                default: throw new RuntimeException("tag do constant pool: " + tag);
            }
        }
    }

    private static String readUtf8(DataInputStream in) throws Exception {
        int len = in.readUnsignedShort();
        byte[] b = new byte[len];
        in.readFully(b);
        return new String(b, "UTF-8");
    }

    private static void skipAttr(DataInputStream in) throws Exception {
        in.readUnsignedShort();
        in.skipBytes(in.readInt());
    }

    private static String utf8(int idx) {
        return ((CpUtf8) cp[idx]).s;
    }

    private static String className(int classIdx) {
        return utf8(((CpClass) cp[classIdx]).nameIdx);
    }

    // runtime dos intrinsecos do driver, carregado em 0x9000:
    //   +0x000 vbeInfo(modo): int 0x10 4F01 via thunk pm<->rm, retorna 1 se ok
    //   +0x100 vbeSetar(modo): int 0x10 4F02 (LFB) via thunk pm<->rm
    private static final int[] RUNTIME = {
            0xFA, 0x89, 0x25, 0xBC, 0x91, 0x00, 0x00, 0xA3, 0xC0, 0x91, 0x00, 0x00,
            0x0F, 0x01, 0x15, 0x92, 0x91, 0x00, 0x00, 0xEA, 0x1A, 0x90, 0x00, 0x00,
            0x08, 0x00, 0xB8, 0x10, 0x00, 0x8E, 0xD8, 0x8E, 0xC0, 0x8E, 0xE0, 0x8E,
            0xE8, 0x8E, 0xD0, 0x0F, 0x20, 0xC0, 0x66, 0x83, 0xE0, 0xFE, 0x0F, 0x22,
            0xC0, 0xEA, 0x36, 0x90, 0x00, 0x00, 0x31, 0xC0, 0x8E, 0xD8, 0x8E, 0xD0,
            0xBC, 0x00, 0x7C, 0x0F, 0x01, 0x1E, 0xB6, 0x91, 0xB8, 0x00, 0x10, 0x8E,
            0xC0, 0xBF, 0x00, 0x42, 0x8B, 0x0E, 0xC0, 0x91, 0xB8, 0x01, 0x4F, 0xCD,
            0x10, 0xA3, 0xC4, 0x91, 0x0F, 0x01, 0x16, 0xB0, 0x91, 0x0F, 0x20, 0xC0,
            0x66, 0x83, 0xC8, 0x01, 0x0F, 0x22, 0xC0, 0xEA, 0x6C, 0x90, 0x08, 0x00,
            0x66, 0xB8, 0x10, 0x00, 0x8E, 0xD8, 0x8E, 0xC0, 0x8E, 0xE0, 0x8E, 0xE8,
            0x8E, 0xD0, 0x8B, 0x25, 0xBC, 0x91, 0x00, 0x00, 0x0F, 0xB7, 0x05, 0xC4,
            0x91, 0x00, 0x00, 0x66, 0x83, 0xF8, 0x4F, 0x0F, 0x94, 0xC0, 0x0F, 0xB6,
            0xC0, 0xC3, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0xFA, 0x89, 0x25, 0xBC, 0x91, 0x00, 0x00, 0xA3,
            0xC0, 0x91, 0x00, 0x00, 0x0F, 0x01, 0x15, 0x92, 0x91, 0x00, 0x00, 0xEA,
            0x1A, 0x91, 0x00, 0x00, 0x08, 0x00, 0xB8, 0x10, 0x00, 0x8E, 0xD8, 0x8E,
            0xC0, 0x8E, 0xE0, 0x8E, 0xE8, 0x8E, 0xD0, 0x0F, 0x20, 0xC0, 0x66, 0x83,
            0xE0, 0xFE, 0x0F, 0x22, 0xC0, 0xEA, 0x36, 0x91, 0x00, 0x00, 0x31, 0xC0,
            0x8E, 0xD8, 0x8E, 0xD0, 0xBC, 0x00, 0x7C, 0x0F, 0x01, 0x1E, 0xB6, 0x91,
            0x8B, 0x1E, 0xC0, 0x91, 0x81, 0xCB, 0x00, 0x40, 0xB8, 0x02, 0x4F, 0xCD,
            0x10, 0x0F, 0x01, 0x16, 0xB0, 0x91, 0x0F, 0x20, 0xC0, 0x66, 0x83, 0xC8,
            0x01, 0x0F, 0x22, 0xC0, 0xEA, 0x65, 0x91, 0x08, 0x00, 0x66, 0xB8, 0x10,
            0x00, 0x8E, 0xD8, 0x8E, 0xC0, 0x8E, 0xE0, 0x8E, 0xE8, 0x8E, 0xD0, 0x8B,
            0x25, 0xBC, 0x91, 0x00, 0x00, 0xC3, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x9A, 0x00, 0x00, 0xFF, 0xFF,
            0x00, 0x00, 0x00, 0x92, 0x00, 0x00, 0x17, 0x00, 0x7A, 0x91, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF, 0x00, 0x00,
            0x00, 0x9A, 0xCF, 0x00, 0xFF, 0xFF, 0x00, 0x00, 0x00, 0x92, 0xCF, 0x00,
            0x17, 0x00, 0x98, 0x91, 0x00, 0x00, 0xFF, 0x03, 0x00, 0x00, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    };

    //--------------------------------------------------------------- constant pool
    private static final class CpUtf8        { int idx; String s; }
    private static final class CpInt         { int v; CpInt(int x) { v = x; } }
    private static final class CpClass       { int nameIdx; CpClass(int i) { nameIdx = i; } }
    private static final class CpString      { int utfIdx; CpString(int i) { utfIdx = i; } }
    private static final class CpRef         { int classIdx, natIdx; CpRef(int a, int b) { classIdx = a; natIdx = b; } }
    private static final class CpNameAndType { int nameIdx, descIdx; CpNameAndType(int a, int b) { nameIdx = a; descIdx = b; } }
}
