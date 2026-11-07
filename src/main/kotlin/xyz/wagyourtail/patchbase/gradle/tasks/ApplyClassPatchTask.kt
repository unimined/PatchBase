package xyz.wagyourtail.patchbase.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import xyz.wagyourtail.patchbase.installer.PatchbaseInstaller
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Applies one or more patch jars to a base jar — the apply-side
 * counterpart of [CreateClassPatchTask], mirroring how Forge
 * binpatches turn a stock MC jar into the dev-time loader jar: no
 * source, no recompile, `.bdiff` byte-deltas over the base.
 *
 * Patches apply in [patchJars] iteration order; each patch's
 * `.bdiff` entries carry the base checksum, so ordering against the
 * same base is verified, not assumed.
 */
abstract class ApplyClassPatchTask : DefaultTask() {

    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @get:Classpath
    abstract val patchJars: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val base = inputFile.get().asFile.toPath()
        val out = outputFile.get().asFile.toPath()
        Files.createDirectories(out.parent)
        var current = base
        patchJars.files.forEachIndexed { i, patch ->
            val next = if (i == patchJars.files.size - 1) out
                else temporaryDir.toPath().resolve("patched-$i.jar")
            PatchbaseInstaller().patch(patch.toPath(), current, next)
            current = next
        }
        if (patchJars.files.isEmpty()) {
            Files.copy(base, out, StandardCopyOption.REPLACE_EXISTING)
        }
    }

}
