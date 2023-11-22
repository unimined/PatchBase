package xyz.wagyourtail.patchbase.installer;

import io.github.prcraftmc.classdiff.ClassPatcher;
import io.github.prcraftmc.classdiff.format.DiffReader;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.*;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class PatchbaseInstaller {

    /*
     * create your installer around this method.
     */
    public void patch(Path patchJar, Path baseJar, Path outputJar) throws IOException {
        Files.createDirectories(outputJar.getParent());
        Files.copy(baseJar, outputJar, StandardCopyOption.REPLACE_EXISTING);
        try (FileSystem fs = openZipFileSystem(outputJar)) {
            forEachInZip(patchJar, (entry, is) -> {
                if (entry.endsWith(".cdiff")) {
                    try {
                        readZipInputStreamFor(baseJar, entry.substring(0, entry.length() - 6), true, original -> {
                            try {
                                ClassWriter cw = new ClassWriter(0);
                                ClassNode node = new ClassNode();
                                new ClassReader(original).accept(node, ClassReader.SKIP_DEBUG);
                                ClassPatcher.patch(node, new DiffReader(is.readAllBytes()));
                                node.accept(cw);
                                Path p = fs.getPath(entry.substring(0, entry.length() - 6));
                                if (p.getParent() != null) Files.createDirectories(p.getParent());
                                Files.write(p, cw.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
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
            var zipEntry = zis.getNextEntry();
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
            var entry = zis.getNextEntry();
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
        return openZipFileSystem(path, Map.of());
    }

    public static FileSystem openZipFileSystem(Path path, Map<String, Object> args) throws IOException {
        if (!Files.exists(path) && args.getOrDefault("create", false).equals(true)) {
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(path))) {
                zos.closeEntry();
            }
        }
        return FileSystems.newFileSystem(URI.create("jar:" + path.toUri()), args, null);
    }

}
