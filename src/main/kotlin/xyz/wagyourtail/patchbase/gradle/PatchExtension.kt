package xyz.wagyourtail.patchbase.gradle

import xyz.wagyourtail.unimined.api.minecraft.MinecraftConfig

abstract class PatchExtension {

    fun patchBaseCreator(minecraftConfig: MinecraftConfig) {
        minecraftConfig.patchBaseCreator()
    }

    fun patchBase(minecraftConfig: MinecraftConfig) {
        minecraftConfig.patchBase()
    }

}