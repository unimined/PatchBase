package xyz.wagyourtail.patchbase.installer;

import com.nothome.delta.Delta;
import com.nothome.delta.GDiffPatcher;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.Adler32;

/**
 * Byte-level class-patch unit — the PatchBase 2.0 replacement for
 * class-diff's {@code .cdiff} format.
 *
 * <p>Format ({@code .bdiff}): {@code [adler32(clean): 4 bytes]
 * [javaxdelta gdiff: rest]}.
 *
 * <p>Diffs run over constant-pool-remapped dirty bytes — the same
 * technique as neoforged/InstallerTools#7: re-serializing the dirty
 * class with the clean class's constant pool
 * ({@link ClassWriter#ClassWriter(ClassReader)}) gives shared
 * constants identical indices, so the delta only captures real
 * changes (~60% smaller on large classes). Applying the patch yields
 * the pool-remapped dirty bytes — semantically the dirty class.
 *
 * <p>The checksum makes base drift fail loudly instead of silently
 * producing wrong classes — appropriate because patch jars always
 * target a pinned base artifact.
 */
public final class ClassDiff {

    private ClassDiff() {}

    /** Create a {@code .bdiff} patch unit transforming {@code clean}
     *  into {@code dirty}. Both are class-file bytes. */
    public static byte[] create(byte[] clean, byte[] dirty) throws IOException {
        byte[] shrunk = shrinkDirtyForPatch(clean, dirty);
        byte[] delta = new Delta().compute(clean, shrunk);
        ByteArrayOutputStream bos = new ByteArrayOutputStream(4 + delta.length);
        DataOutputStream out = new DataOutputStream(bos);
        out.writeInt(adler(clean));
        out.write(delta);
        out.flush();
        return bos.toByteArray();
    }

    /** Apply a {@code .bdiff} patch unit to {@code clean}, returning
     *  the patched class bytes.
     *  @throws IllegalStateException if {@code clean} isn't the base
     *    this patch was built for */
    public static byte[] apply(byte[] clean, byte[] diff) throws IOException {
        if (diff.length < 4) {
            throw new IllegalArgumentException("truncated .bdiff entry");
        }
        int expected = ByteBuffer.wrap(diff, 0, 4).getInt();
        int actual = adler(clean);
        if (expected != actual) {
            throw new IllegalStateException(String.format(
                "patch base mismatch: expected adler32 %08x, got %08x " +
                "(patch jar was built against a different base artifact)",
                expected, actual));
        }
        byte[] delta = new byte[diff.length - 4];
        System.arraycopy(diff, 4, delta, 0, delta.length);
        return new GDiffPatcher().patch(clean, delta);
    }

    private static byte[] shrinkDirtyForPatch(byte[] clean, byte[] dirty) {
        if (clean.length == 0) return dirty;
        ClassWriter writer = new ClassWriter(new ClassReader(clean), 0);
        new ClassReader(dirty).accept(writer, 0);
        return writer.toByteArray();
    }

    private static int adler(byte[] bytes) {
        Adler32 a = new Adler32();
        a.update(bytes);
        return (int) a.getValue();
    }

}
