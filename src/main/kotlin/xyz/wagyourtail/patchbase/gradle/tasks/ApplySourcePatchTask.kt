package xyz.wagyourtail.patchbase.gradle.tasks

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import kotlin.io.path.*

abstract class ApplySourcePatchTask : AbstractSourceTask() {

    @get:Input
    abstract val patchDir: Property<File>

    @get:Input
    abstract val outputDir: Property<File>

    @OptIn(ExperimentalPathApi::class)
    @TaskAction
    fun run() {
        val output = outputDir.get().toPath()
        output.deleteRecursively()

        val patch = patchDir.get().toPath()
        for (path in patch.walk()) {
            val relative = path.relativeTo(patch)
            val targetParent = output.resolve(relative.parent)
            targetParent.createDirectories()
            if (path.extension != "patch") {
                // copy directly
                val target = targetParent.resolve(relative.name)
                path.copyTo(target)
            } else {
                findSource(relative.resolveSibling(relative.nameWithoutExtension)) { original ->
                    if (original != null) {
                        targetParent.resolve(relative.nameWithoutExtension)
                            .writeText(applyDiff(original.readBytes().decodeToString(), path.readText()))
                    } else {
                        throw IllegalStateException("Cannot apply patch to non-existent file: $relative")
                    }
                }
            }
        }
    }
}