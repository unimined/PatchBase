package xyz.wagyourtail.patchbase.gradle.tasks

import net.neoforged.binarypatcher.Patch
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import xyz.wagyourtail.unimined.util.forEachInZip
import xyz.wagyourtail.unimined.util.readZipInputStreamFor
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.*

abstract class CreateClassPatchTask : Jar() {
    companion object {
        val EMPTY_DATA: ByteArray = ByteArray(0)
    }

    /**
     * Shrinks the created `.class` patches by remapping constant pool indices.
     */
    @get:Input
    abstract val minimizePatch: Property<Boolean>

    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:Input
    abstract val classpath: Property<FileCollection>

    @OptIn(ExperimentalPathApi::class)
    @TaskAction
    fun run() {
        val tempDir = temporaryDir.resolve("diffs").toPath()
        tempDir.deleteRecursively()

        inputFile.get().asFile.toPath().forEachInZip { name, input ->
            fun create(s: String, bytes: ByteArray? = null): Path = tempDir.resolve(s).apply {
                parent.createDirectories()
                outputStream().use { output ->
                    if (bytes != null) bytes.let { output.write(it) }
                    else input.copyTo(output)
                }
            }

            if (name.endsWith(".deleted")) {
                val target = name.substringBefore(".deleted")
                findClass(target) { original ->
                    if (original != null) project.logger.info("$target will be deleted.")
                    else project.logger.warn("$target does not exist in target.")

                    // Intentionally write empty data, installer will treat this as a deleted file.
                    create("$name.deleted", EMPTY_DATA)
                }
            } else if (name.endsWith(".class")) {
                // find in classpath
                findClass(name) { original ->
                    if (original != null) create("$name.binpatch", makePatch(original, input, name))
                    else create(name)
                }
            } else {
                create(name)
            }
        }
        from(tempDir)
        copy()
    }

    fun makePatch(a: InputStream, b: InputStream, name: String): ByteArray {
        val ra = ClassWriter(ClassReader(a), ClassReader.SKIP_DEBUG).toByteArray()
        val rb = ClassWriter(ClassReader(b), ClassReader.SKIP_DEBUG).toByteArray()
        val x = Patch.from(name, "", ra, rb, minimizePatch.get())
        return x.toBytes()
    }

    fun findClass(name: String, action: (InputStream?) -> Unit) {
        for (source in classpath.get()) {
            val sourcePath = source.toPath()
            if (sourcePath.isDirectory()) {
                val file = sourcePath.resolve(name)
                if (file.exists()) {
                    action(file.inputStream())
                } else {
                    action(null)
                }
            } else {
                try {
                    sourcePath.readZipInputStreamFor(name, true) {
                        action(it)
                    }
                } catch (_: IllegalArgumentException) {
                    action(null)
                }
            }
        }
    }
}
