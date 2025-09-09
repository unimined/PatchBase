package xyz.wagyourtail.patchbase.gradle.tasks

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File
import kotlin.io.path.*

abstract class CreateSourcePatchTask : AbstractSourceTask() {
    @get:Input
    abstract val sourceDir: Property<File>

    @get:Input
    abstract val outputDir: Property<File>
    @OptIn(ExperimentalPathApi::class)
    @TaskAction
    fun run() {
        val output = outputDir.get().toPath()
        output.deleteRecursively()

        val source = sourceDir.get().toPath()
        for (path in source.walk()) {
            val relative = path.relativeTo(source)
            val targetParent = relative.parent?.let {
                output.resolve(it).createDirectories()
            } ?: output
            findSource(relative) { original ->
                if (original != null) {
                    project.logger.info("Creating a patch for $path")
                    val target = targetParent.resolve(relative.nameWithoutExtension + ".${relative.extension}.patch")
                    val diff = diff(relative.name, original.readBytes().decodeToString(), relative.name, path.readText())
                    if (!diff.trim().isEmpty()) {
                        target.writeText(diff)
                    }
                } else {
                    val target = targetParent.resolve(relative.name)
                    project.logger.info("Copying $path to $target")
                    path.copyTo(target, overwrite = true)
                }
            }
        }
    }
}
