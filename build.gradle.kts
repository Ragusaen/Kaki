import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion = "1.5.31"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    application
}

group = "me.ragusa"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "16"
}

java {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
}

kotlin {
    sourceSets {
        val main by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib:1.5.31")

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
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        archiveFileName.set("Kaki.jar")
        manifest {
            attributes["Main-Class"] = "MainKt"
        }

        // To add all the dependencies
        from(sourceSets.main.get().output)
        dependsOn(configurations.runtimeClasspath)
        from({
            configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
        })
    }
}

dependencies {
    implementation(kotlin("script-runtime"))
}