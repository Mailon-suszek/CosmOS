import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AOT {

    private static Object[] cp;

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("uso: AOT <Face.class> <face.grain>");
            System.exit(1);
        }
        byte[] cls = Files.readAllBytes(Paths.get(args[0]));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        translate(cls, out);
        Files.write(Paths.get(args[1]), out.toByteArray());
        System.err.println("face.grain gerado (" + out.size() + " bytes)");
    }

    private static void translate(byte[] cls, ByteArrayOutputStream out) throws Exception {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(cls));
        if (in.readInt() != 0xCAFEBABE) throw new RuntimeException("nao e um classfile");
        in.readUnsignedShort();
        in.readUnsignedShort();
        readConstantPool(in);
        in.readUnsignedShort();
        in.readUnsignedShort();
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
                emitMethod(new DataInputStream(new ByteArrayInputStream(code)), out);
            }
        }
    }

    private static void readConstantPool(DataInputStream in) throws Exception {
        int count = in.readUnsignedShort();
        cp = new Object[count];
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1:  { CpUtf8 e = new CpUtf8(); e.idx = i; cp[i] = e; e.s = readUtf8(in); break; }
                case 3:  cp[i] = new CpInt(in.readInt()); break;
                case 4:  cp[i] = new CpFloat(in.readFloat()); break;
                case 5:  in.readLong(); i++; break;
                case 6:  in.readDouble(); i++; break;
                case 7:  cp[i] = new CpClass(in.readUnsignedShort()); break;
                case 8:  cp[i] = new CpString(in.readUnsignedShort()); break;
                case 9:
                case 10:
                case 11: cp[i] = new CpRef(in.readUnsignedShort(), in.readUnsignedShort()); break;
                case 12: cp[i] = new CpNameAndType(in.readUnsignedShort(), in.readUnsignedShort()); break;
                case 15: cp[i] = new CpMethodHandle(in.readUnsignedByte(), in.readUnsignedShort()); break;
                case 16: cp[i] = new CpMethodType(in.readUnsignedShort()); break;
                case 17:
                case 18: cp[i] = new CpDynamic(in.readUnsignedShort(), in.readUnsignedShort()); break;
                case 19:
                case 20: cp[i] = new CpModule(in.readUnsignedShort()); break;
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

    private static void emitMethod(DataInputStream cin, ByteArrayOutputStream out) throws Exception {
        cin.readUnsignedShort();
        cin.readUnsignedShort();
        int len = cin.readInt();
        byte[] code = new byte[len];
        cin.readFully(code);
        int pc = 0;
        while (pc < len) {
            int op = code[pc] & 0xFF;
            switch (op) {
                case 0x00: pc++; break;
                case 0x2A: pc++; break;
                case 0xB1: out.write(new byte[]{(byte) 0xFA, (byte) 0xF4}); pc++; break;
                case 0xB8: emitInvokeStatic(code, pc, out); pc += 3; break;
                default:
                    throw new RuntimeException("bytecode nao suportado: 0x" + Integer.toHexString(op) + " pc=" + pc);
            }
        }
    }

    private static void emitInvokeStatic(byte[] code, int pc, ByteArrayOutputStream out) {
        int idx = ((code[pc + 1] & 0xFF) << 8) | (code[pc + 2] & 0xFF);
        CpRef ref = (CpRef) cp[idx];
        String klass = className(ref.classIdx);
        CpNameAndType nat = (CpNameAndType) cp[ref.natIdx];
        String name = utf8(nat.nameIdx);
        String desc = utf8(nat.descIdx);
        if (!desc.equals("()V")) throw new RuntimeException("intrinseco nao-void: " + desc);
        emitIntrinsic(klass, name, out);
    }

    private static void emitIntrinsic(String klass, String name, ByteArrayOutputStream out) {
        if (klass.equals("CosmOS") && name.equals("telaBranca")) {
            emitFillWhite(out);
        } else {
            throw new RuntimeException("intrinseco desconhecido: " + klass + "." + name);
        }
    }

    // mov edx,0xB8000 ; mov ecx,2000 ; loop: mov word [edx],0x9020 ; add edx,2 ; dec ecx ; jnz loop
    private static void emitFillWhite(ByteArrayOutputStream out) {
        int[] m = {
                0xBA, 0x00, 0x80, 0x0B, 0x00,
                0xB9, 0xD0, 0x07, 0x00, 0x00,
                0x66, 0xC7, 0x02, 0x20, 0x90,
                0x83, 0xC2, 0x02,
                0x49,
                0x75, 0xF5
        };
        for (int b : m) out.write(b);
    }

    private static final class CpUtf8          { int idx; String s; }
    private static final class CpInt           { CpInt(int v) {} }
    private static final class CpFloat         { CpFloat(float v) {} }
    private static final class CpClass         { int nameIdx; CpClass(int i) { nameIdx = i; } }
    private static final class CpString        { int utfIdx; CpString(int i) { utfIdx = i; } }
    private static final class CpRef           { int classIdx, natIdx; CpRef(int a, int b) { classIdx = a; natIdx = b; } }
    private static final class CpNameAndType   { int nameIdx, descIdx; CpNameAndType(int a, int b) { nameIdx = a; descIdx = b; } }
    private static final class CpMethodHandle  { CpMethodHandle(int k, int i) {} }
    private static final class CpMethodType    { CpMethodType(int i) {} }
    private static final class CpDynamic       { CpDynamic(int a, int b) {} }
    private static final class CpModule        { CpModule(int i) {} }
}