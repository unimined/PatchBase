package xyz.wagyourtail.patchbase.gradle.tasks

import io.github.prcraftmc.classdiff.ClassDiffer
import io.github.prcraftmc.classdiff.format.DiffWriter
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.tasks.Jar
import org.objectweb.asm.ClassReader
import org.objectweb.asm.tree.ClassNode
import xyz.wagyourtail.unimined.util.forEachInZip
import xyz.wagyourtail.unimined.util.readZipInputStreamFor
import java.io.InputStream
import kotlin.io.path.*

abstract class CreateClassPatchTask : Jar() {

    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:Input
    abstract val classpath: Property<FileCollection>

    @OptIn(ExperimentalPathApi::class)
    @TaskAction
    fun run() {
        val tempDir = temporaryDir.resolve("diffs").toPath()
        tempDir.deleteRecursively()

        inputFile.get().asFile.toPath().forEachInZip { name, stream ->
            if (name.endsWith(".class")) {
                // find in classpath
                findClass(name) { original ->
                    if (original != null) {
                        val target = tempDir.resolve("$name.cdiff")
                        target.parent.createDirectories()
                        target.writeBytes(diff(original, stream))
                    } else {
                        val target = tempDir.resolve(name)
                        target.parent.createDirectories()
                        target.outputStream().use { stream.copyTo(it) }
                    }
                }
            } else {
                val target = tempDir.resolve(name)
                target.parent.createDirectories()
                target.outputStream().use { stream.copyTo(it) }
            }
        }
        from(tempDir)
        copy()
    }

    fun diff(a: InputStream, b: InputStream): ByteArray {
        val ra = ClassReader(a).let { ClassNode().apply { it.accept(this, ClassReader.SKIP_DEBUG) } }
        val rb = ClassReader(b).let { ClassNode().apply { it.accept(this, ClassReader.SKIP_DEBUG) } }
        val w = DiffWriter()
        ClassDiffer.diff(ra, rb, w)
        return w.toByteArray()
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
                } catch (e: IllegalArgumentException) {
                    action(null)
                }
            }
        }
    }

}