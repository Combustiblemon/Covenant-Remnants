plugins {
    kotlin("jvm") version "2.1.0"
    java
}

group = "org.combustiblelemon"
version = "0.1.0"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

sourceSets {
    main {
        kotlin {
            srcDirs("src")
        }
        java {
            srcDirs("src")
        }
        resources {
            srcDirs("data", "graphics")
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    // Starsector API
    compileOnly(files("../../starsector-core/starfarer.api.jar"))
    compileOnly(files("../../starsector-core/fs.common_obf.jar"))
    compileOnly(files("../../starsector-core/fs.sound_obf.jar"))
    compileOnly(files("../../starsector-core/starfarer_obf.jar"))

    // LWJGL for Vector2f and other game library classes
    compileOnly(files("../../starsector-core/lwjgl.jar"))
    compileOnly(files("../../starsector-core/lwjgl_util.jar"))

    // LazyLib dependency
    compileOnly(files("../LazyLib/jars/LazyLib.jar"))
    compileOnly(files("../LazyLib/jars/LazyLib-Kotlin.jar"))

    // MagicLib dependency
    compileOnly(files("../MagicLib/jars/MagicLib.jar"))
    compileOnly(files("../MagicLib/jars/MagicLib-Kotlin.jar"))

    // Log4j for logging (Starsector standard)
    compileOnly("log4j:log4j:1.2.17")

    // LunaLib dependency
    compileOnly(files("../LunaLib/jars/LunaLib.jar"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
        freeCompilerArgs += listOf("-Xjvm-default=all")
    }
}

// Task to build the mod jar
tasks.register<Jar>("buildMod") {
    dependsOn("classes")
    archiveFileName.set("CovenantRemnants.jar")
    destinationDirectory.set(file("jars"))

    from(sourceSets.main.get().output)

    // Include mod resources
    from("data") {
        into("data")
    }
    from("graphics") {
        into("graphics")
    }

    // Include mod_info.json
    from("mod_info.json")

    manifest {
        attributes(
            "Implementation-Title" to "Covenant Remnants",
            "Implementation-Version" to version,
            "Main-Class" to "org.combustiblelemon.covenantremnants.CovenantRemnantsPlugin"
        )
    }
}

// Task to clean build artifacts
tasks.register("cleanMod") {
    dependsOn("clean")
    doLast {
        delete("jars")
    }
}

// Task to package the mod
tasks.register("packageMod") {
    dependsOn("buildMod")
    doLast {
        copy {
            from(".")
            into("build/mod_package")
            include("mod_info.json")
            include("data/**")
            include("graphics/**")
            include("jars/**")
            exclude("build/**")
            exclude("src/**")
            exclude("*.iml")
            exclude("*.ipr")
            exclude("*.iws")
            exclude(".idea/**")
            exclude("gradle/**")
            exclude("*.gradle")
            exclude("*.gradle.kts")
            exclude("gradlew*")
        }
        println("Mod packaged to build/mod_package/")
    }
}
