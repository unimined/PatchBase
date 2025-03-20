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
                        val target = tempDir.resolve("$name.binpatch")
                        target.parent.createDirectories()
                        target.writeBytes(makePatch(original, stream, name))
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

    fun makePatch(a: InputStream, b: InputStream, name: String): ByteArray {
        val ra = ClassWriter(ClassReader(a), ClassReader.SKIP_DEBUG).toByteArray()
        val rb = ClassWriter(ClassReader(b), ClassReader.SKIP_DEBUG).toByteArray()
		val x = Patch.from(name, "", ra, rb, false)
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
                } catch (e: IllegalArgumentException) {
                    action(null)
                }
            }
        }
    }

}