package xyz.wagyourtail.patchbase.gradle.tasks

import com.github.difflib.DiffUtils
import com.github.difflib.UnifiedDiffUtils
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import xyz.wagyourtail.unimined.util.readZipInputStreamFor
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory

abstract class AbstractSourceTask : ConventionTask() {
    /**
     * Controls how much context (surrounding lines) are provided in the patch files.
     */
    @get:Input
    abstract val diffContextSize: Property<Int>

    /**
     * Trims leading whitespace in the patch files.
     */
    @get:Input
    abstract val trimWhitespace: Property<Boolean>

    @get:Input
    abstract val sources: Property<FileCollection>

    fun findSource(path: Path, action: (InputStream?) -> Unit) {
        for (source in sources.get()) {
            val sourcePath = source.toPath()
            if (sourcePath.isDirectory()) {
                val file = sourcePath.resolve(path)
                if (file.exists()) {
                    action(file.inputStream())
                } else {
                    action(null)
                }
            } else {
                try {
                    sourcePath.readZipInputStreamFor(path.toString(), true) {
                        action(it)
                    }
                } catch (_: IllegalArgumentException) {
                    action(null)
                }
            }
        }
    }

    fun diff(aName: String?, a: String, bName: String?, b: String): String {
        var aLines = a.lines().toMutableList()
        // trim end to posix
        for (i in aLines.indices.reversed()) {
            if (aLines[i].isNotBlank()) {
                break
            } else {
                aLines.removeAt(i)
            }
        }
        aLines.add("")
        var bLines = b.lines().toMutableList()
        // trim end to posix
        for (i in bLines.indices.reversed()) {
            if (bLines[i].isNotBlank()) {
                break
            } else {
                bLines.removeAt(i)
            }
        }
        bLines.add("")
        if (trimWhitespace.get()) {
            aLines = aLines.map(String::trim).toMutableList()
            bLines = bLines.map(String::trim).toMutableList()
        }

        val patch = DiffUtils.diff(aLines, bLines)
        patch.deltas.forEach {
            it.target.position
            it.target.lines = bLines.subList(it.target.position, it.target.position + it.target.size())
        }
        val unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(aName, bName, aLines, patch, diffContextSize.get())
        val sb = StringBuilder()
        for (s in unifiedDiff) {
            sb.append(s).append("\n")
        }
        return sb.toString()
    }

    fun applyDiff(originalText: String, patchText: String): String {
        val patch = UnifiedDiffUtils.parseUnifiedDiff(patchText.lines())
        val patchedText = DiffUtils.patch(originalText.lines(), patch)
        val sb = StringBuilder()
        for (s in patchedText) {
            sb.append(s).append("\n")
        }
        return sb.toString()
    }
}
