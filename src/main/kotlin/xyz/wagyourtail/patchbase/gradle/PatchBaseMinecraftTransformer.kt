package xyz.wagyourtail.patchbase.gradle

import org.gradle.api.Project
import xyz.wagyourtail.patchbase.installer.PatchbaseInstaller
import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig
import xyz.wagyourtail.unimined.internal.minecraft.MinecraftProvider
import xyz.wagyourtail.unimined.internal.minecraft.patch.MinecraftJar
import xyz.wagyourtail.unimined.internal.minecraft.patch.jarmod.JarModAgentMinecraftTransformer
import xyz.wagyourtail.unimined.internal.minecraft.patch.jarmod.JarModMinecraftTransformer
import xyz.wagyourtail.unimined.util.withSourceSet

fun MinecraftConfig.patchBase(action: PatchBaseMinecraftTransformer.() -> Unit = {}) {
    customPatcher(PatchBaseMinecraftTransformer(this.project, this as MinecraftProvider), action)
}

class PatchBaseMinecraftTransformer(project: Project, provider: MinecraftProvider) : JarModAgentMinecraftTransformer(project, provider) {
    val patchBase = project.configurations.maybeCreate("patchBase".withSourceSet(provider.sourceSet))

    override fun transform(minecraft: MinecraftJar): MinecraftJar {
        val patchDep = patchBase.dependencies.last()
        val patchJar = patchBase.files(patchDep).first { it.extension == "jar" || it.extension == "zip" }
        val outputFolder = minecraft.path.parent.resolve(patchDep.name).resolve(patchDep.version)

        val patchedMC = MinecraftJar(
            minecraft,
            parentPath = outputFolder,
            patches = minecraft.patches + "patchbase"
        )

        PatchbaseInstaller().patch(patchJar.toPath(), minecraft.path, patchedMC.path)

        return super.transform(patchedMC)
    }

}