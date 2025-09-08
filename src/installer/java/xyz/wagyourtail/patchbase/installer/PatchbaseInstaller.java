package xyz.wagyourtail.patchbase.installer;

import com.nothome.delta.GDiffPatcher;
import net.neoforged.binarypatcher.Patch;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.*;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class PatchbaseInstaller {
    private static final GDiffPatcher PATCHER = new GDiffPatcher();
    private static final byte[] EMPTY_DATA = new byte[0];

    /*
     * create your installer around this method.
     */
    public void patch(Path patchJar, Path baseJar, Path outputJar) throws IOException {
        Files.createDirectories(outputJar.getParent());
        Files.copy(baseJar, outputJar, StandardCopyOption.REPLACE_EXISTING);
        try (FileSystem fs = openZipFileSystem(outputJar)) {
            forEachInZip(patchJar, (entry, is) -> {
                if (entry.endsWith(".binpatch")) {
                    try {
                        readZipInputStreamFor(baseJar, entry.substring(0, entry.length() - 6), true, originalStream -> {
                            try {
                                byte[] original = new ClassWriter(new ClassReader(originalStream), ClassReader.SKIP_DEBUG).toByteArray();
                                byte[] result = patch(original, Patch.from(is));

                                // if we removed the file, don't write on disk
                                if ( result != EMPTY_DATA ) {
                                    Path p = fs.getPath(entry.substring(0, entry.length() - 6));
                                    if (p.getParent() != null) {
                                        Files.createDirectories(p.getParent());
                                    }
                                    Files.write(p, result, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            return null;
                        });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    try {
                        Path p = fs.getPath(entry);
                        if (p.getParent() != null) Files.createDirectories(p.getParent());
                        Files.copy(is, p, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }

    public static <T> T readZipInputStreamFor(Path path, String entry, boolean throwIfMissing, Function<InputStream, T> action) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(path))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                if (zipEntry.isDirectory()) {
                    zipEntry = zis.getNextEntry();
                    continue;
                }
                if (zipEntry.getName().equals(entry)) {
                    return action.apply(zis);
                }
                zipEntry = zis.getNextEntry();
            }
        }
        if (throwIfMissing) {
            throw new IllegalArgumentException("Missing file " + entry + " in " + path);
        }
        return null;
    }

    public static void forEachInZip(Path path, BiConsumer<String, InputStream> action) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(path))) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                if (entry.isDirectory()) {
                    entry = zis.getNextEntry();
                    continue;
                }
                action.accept(entry.getName(), zis);
                entry = zis.getNextEntry();
            }
        }
    }

    public static FileSystem openZipFileSystem(Path path) throws IOException {
        return openZipFileSystem(path, Collections.emptyMap());
    }

    public static FileSystem openZipFileSystem(Path path, Map<String, Object> args) throws IOException {
        if (!Files.exists(path) && args.getOrDefault("create", false).equals(true)) {
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(path))) {
                zos.closeEntry();
            }
        }
        return FileSystems.newFileSystem(URI.create("jar:" + path.toUri()), args, null);
    }

    private static byte[] patch( byte[] data, Patch patch ) throws IOException {
        if (patch.exists && data.length == 0) {
            throw new IOException( "Patch expected " + patch.getName() + " to exist, but received empty data" );
        }
        if (!patch.exists && data.length > 0) {
            throw new IOException( "Patch expected " + patch.getName() + " to not exist, but received " + data.length + " bytes" );
        }

        int checksum = patch.checksum(data);
        if (checksum != patch.checksum) {
            throw new IOException( "Patch expected " + patch.getName() + " to have the checksum " + Integer.toHexString( patch.checksum ) + " but it was " + Integer.toHexString( checksum ) );
        }

        if (patch.data.length == 0) {  // File removed
            return EMPTY_DATA;
        } else {
            return PATCHER.patch( data, patch.data );
        }
    }

}
