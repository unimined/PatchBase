package xyz.wagyourtail.patchbase.installer;

import com.nothome.delta.Delta;
import com.nothome.delta.GDiffPatcher;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.*;
import java.util.zip.Adler32;

/**
 * Minimal reader/writer for the standard {@code .binpatch} record —
 * the same wire format Forge/NeoForge userdev ships and unimined's
 * forge binpatch pipeline parses. Kept in-repo (instead of depending
 * on {@code net.neoforged.installertools:binarypatcher}) because the
 * record is ~60 lines over {@code javaxdelta}+asm — the same
 * tradeoff unimined makes for forge binpatches.
 *
 * <p>Wire format: {@code obf-marker byte}, {@code obfName UTF},
 * {@code name UTF}, {@code exists bool}, {@code adler32(clean) int}
 * (only when exists), {@code patchLen int}, gdiff bytes. The pair of
 * names lets a patch <em>rename</em> its entry (obf name vs output
 * name); for PatchBase-created patches they are equal.
 */
public class BinPatch {

    private static final byte[] EMPTY_DATA = new byte[0];

    public final String obfName;
    public final String name;
    public final boolean exists;
    public final int checksum;
    public final byte[] data;

    private BinPatch(String obfName, String name, boolean exists, int checksum, byte[] data) {
        this.obfName = obfName;
        this.name = name;
        this.exists = exists;
        this.checksum = checksum;
        this.data = data;
    }

    /** Build a patch taking class {@code name} from {@code clean} to
     *  {@code dirty}. {@code dirty} is always re-serialized against
     *  {@code clean}'s constant pool first — the InstallerTools#7
     *  minimization trick; unminimized output is never worth it. */
    public static BinPatch create(String name, byte[] clean, byte[] dirty) throws IOException {
        ClassWriter writer = new ClassWriter(new ClassReader(clean), 0);
        new ClassReader(dirty).accept(writer, 0);
        dirty = writer.toByteArray();
        Delta delta = new Delta();
        return new BinPatch(name, name, true, adler32(clean), delta.compute(clean, dirty));
    }

    public static BinPatch read(InputStream in) throws IOException {
        DataInputStream input = new DataInputStream(in);
        input.readByte(); // obf-name marker
        String obfName = input.readUTF();
        String name = input.readUTF();
        boolean exists = input.readBoolean();
        int checksum = exists ? input.readInt() : 0;
        byte[] data = new byte[input.readInt()];
        input.readFully(data);
        return new BinPatch(obfName, name, exists, checksum, data);
    }

    public byte[] write() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(out);
        data.writeByte(0); // obf-name marker — PatchBase patches are never obf-mapped
        data.writeUTF(obfName);
        data.writeUTF(name);
        data.writeBoolean(exists);
        if (exists) data.writeInt(checksum);
        data.writeInt(this.data.length);
        data.write(this.data);
        data.flush();
        return out.toByteArray();
    }

    /** Apply against {@code input} (empty array = class absent).
     *  @return patched bytes, or {@link #EMPTY_DATA}-sized array for
     *          removal records */
    public byte[] apply(byte[] input) throws IOException {
        if (exists && input.length == 0) {
            throw new IOException("Patch expected " + name + " to exist, but received empty data");
        }
        if (!exists && input.length > 0) {
            throw new IOException("Patch expected " + name + " to not exist, but received " + input.length + " bytes");
        }
        if (exists && checksum != adler32(input)) {
            throw new IOException("Patch expected " + name + " to have the checksum " +
                Integer.toHexString(checksum) + " but it was " + Integer.toHexString(adler32(input)));
        }
        if (data.length == 0) return EMPTY_DATA;
        return new GDiffPatcher().patch(input, data);
    }

    static int adler32(byte[] data) {
        Adler32 adler = new Adler32();
        adler.update(data);
        return (int) adler.getValue();
    }

}
