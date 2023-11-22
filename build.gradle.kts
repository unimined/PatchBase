import java.net.URI

plugins {
    kotlin("jvm") version "1.8.21"
    `java-gradle-plugin`
    `maven-publish`
}

version = if (project.hasProperty("version_snapshot")) project.properties["version"] as String + "-SNAPSHOT" else project.properties["version"] as String
group = project.properties["maven_group"] as String

base {
    archivesName.set(project.properties["archives_base_name"] as String)
}

val installer by sourceSets.creating

sourceSets {
    main {
        compileClasspath += installer.output
        runtimeClasspath += installer.output
    }
}

repositories {
    maven("https://maven.wagyourtail.xyz/releases")
    maven("https://maven.wagyourtail.xyz/snapshots")
    maven("https://maven.jemnetworks.com/snapshots")
    mavenCentral()
}

configurations {
    implementation {
        extendsFrom(configurations.named("installerImplementation").get())
    }
}

dependencies {
    testImplementation(kotlin("test"))

    runtimeOnly(gradleApi())

    implementation("xyz.wagyourtail.unimined:unimined:1.1.0-SNAPSHOT")
    "installerImplementation"("io.github.java-diff-utils:java-diff-utils:4.12")
    "installerImplementation"("io.github.prcraftmc:class-diff:1.0-SNAPSHOT")
    "installerImplementation"("org.jetbrains:annotations:24.0.1")
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
    from(sourceSets.main.get().output, installer.output)

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
            groupId = project.group as String
            artifactId = project.properties["archives_base_name"] as String? ?: project.name
            version = project.version as String

            artifact(installerJar.get()) {
                classifier = "installer"
            }
        }
    }
}