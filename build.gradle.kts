import java.net.URI

plugins {
    kotlin("jvm") version "2.1.20"
    `java-gradle-plugin`
    `maven-publish`
}

version = "${project.properties["version"]}" + if (project.hasProperty("version_snapshot")) "-SNAPSHOT" else ""
group = "${project.properties["maven_group"]}"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(8)
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    jvmToolchain(8)
}

base.archivesName = "${project.properties["archives_base_name"]}"

val main: SourceSet by sourceSets.getting
val installer: SourceSet by sourceSets.creating
val installerImplementation: Configuration by configurations.getting

sourceSets {
    main {
        compileClasspath += installer.output
        runtimeClasspath += installer.output
    }
}

repositories {
    maven("https://maven.wagyourtail.xyz/releases")
    maven("https://maven.wagyourtail.xyz/snapshots")
    mavenCentral()
}

configurations {
    implementation {
        extendsFrom(installerImplementation)
    }
}

dependencies {
    testImplementation(kotlin("test"))

    runtimeOnly(gradleApi())

    implementation("xyz.wagyourtail.unimined:unimined:1.4.2")
    implementation("xyz.wagyourtail.unimined.mapping:unimined-mapping-library-jvm:1.2.1")
    installerImplementation("io.github.java-diff-utils:java-diff-utils:4.16")
    installerImplementation("net.neoforged.installertools:binarypatcher:3.0.13")
    installerImplementation("org.ow2.asm:asm:9.8")
    installerImplementation("org.ow2.asm:asm-tree:9.8")
    installerImplementation("org.jetbrains:annotations:26.0.2-1")
    installerImplementation("com.nothome:javaxdelta:2.0.1")
}

gradlePlugin {
    plugins {
        create("xyz.wagyourtail.patchbase") {
            id = "xyz.wagyourtail.patchbase"
            implementationClass = "xyz.wagyourtail.patchbase.gradle.PatchPlugin"
        }
    }
}

gradlePlugin {
    plugins {
        create("xyz.wagyourtail.patchbase-creator") {
            id = "xyz.wagyourtail.patchbase-creator"
            implementationClass = "xyz.wagyourtail.patchbase.gradle.PatchCreatorPlugin"
        }
    }
}

tasks.jar {
    from(main.output, installer.output)

    manifest {
        attributes(
            "Implementation-Version" to project.version
        )
    }
}

project.evaluationDependsOnChildren()

val installerJar by tasks.registering(Jar::class) {
    group = "build"
    archiveClassifier.set("installer")
    from(installer.output)
}

tasks.test {
    useJUnitPlatform()
}

tasks.build {
    dependsOn(installerJar)
}

publishing {
    repositories {
        maven {
            name = "WagYourMaven"
            url = if (project.hasProperty("version_snapshot")) {
                URI.create("https://maven.wagyourtail.xyz/snapshots/")
            } else {
                URI.create("https://maven.wagyourtail.xyz/releases/")
            }
            credentials {
                username = project.findProperty("mvn.user") as String? ?: System.getenv("USERNAME")
                password = project.findProperty("mvn.key") as String? ?: System.getenv("TOKEN")
            }
        }
    }
    publications {
        create<MavenPublication>("maven") {
            groupId = "${project.group}"
            artifactId = "${project.properties["archives_base_name"]}"
            version = "${project.version}"

            artifact(installerJar.get()) {
                classifier = "installer"
            }
        }
    }
}
