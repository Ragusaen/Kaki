import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm
import org.gradle.jvm.tasks.Jar

plugins {
    val kotlinVersion = "1.5.31"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
}

group = "me.ragusa"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "16"
}

kotlin {
    sourceSets {
        val main by getting {

            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.4")

                // https://mvnrepository.com/artifact/guru.nidi/graphviz-kotlin
                implementation("guru.nidi:graphviz-kotlin:0.18.1")

                // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-serialization-json
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.1")

                // https://mvnrepository.com/artifact/org.redundent/kotlin-xml-builder
                implementation("org.redundent:kotlin-xml-builder:1.7.4")
            }
        }
    }
}

tasks {
    jar {
        archiveFileName.set("Kaki.jar")
        manifest {
            attributes["Main-Class"] = "MainKt"
        }
        // To add all of the dependencies
        from(sourceSets.main.get().output)
        dependsOn(configurations.runtimeClasspath)
    }
}
