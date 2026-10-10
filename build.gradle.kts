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
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    // Starsector API
    compileOnly(files("../../starsector-core/starfarer.api.jar"))
    compileOnly(files("../../starsector-core/fs.common_obf.jar"))
    compileOnly(files("../../starsector-core/fs.sound_obf.jar"))
    compileOnly(files("../../starsector-core/starfarer_obf.jar"))
    compileOnly(files("../../starsector-core/json.jar")) // loadCSV() returns org.json.JSONArray

    // LWJGL for Vector2f and other game library classes
    compileOnly(files("../../starsector-core/lwjgl.jar"))
    compileOnly(files("../../starsector-core/lwjgl_util.jar"))

    // LemonLib dependency — resolved via the composite build (settings.gradle.kts
    // includeBuild) to LemonLib's compiled classes, NOT to jars/LemonLib.jar.
    // Compiling against the jar would make the IDE's language server lock that
    // file and block jar replacement on rebuilds; class directories don't lock.
    compileOnly("org.combustiblelemon:LemonLib")

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

    // Console Commands dependency
    compileOnly(files("../Console Commands/jars/lw_Console.jar"))
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

// Build LemonLib first — this mod compiles against ../LemonLib/jars/LemonLib.jar
tasks.named("compileKotlin") {
    dependsOn(gradle.includedBuild("LemonLib").task(":buildMod"))
}

// Task to build the mod jar
tasks.register<Jar>("buildMod") {
    dependsOn("classes")
    archiveFileName.set("CovenantRemnants.jar")
    destinationDirectory.set(file("jars"))

    // Windows: the previous jar may be locked by a JVM (Gradle/Kotlin daemon,
    // IDE language server, the game). A locked file cannot be overwritten, but
    // it can usually be renamed — so move it aside and let the new jar take its
    // place. Stale copies are cleaned up on later builds once their holders exit.
    doFirst {
        val out = destinationDirectory.get().file(archiveFileName.get()).asFile
        out.parentFile?.listFiles()?.filter { it.name.contains(".stale-") }?.forEach { it.delete() }
        if (out.exists()) {
            // Rename first: it frees the file NAME immediately even when a
            // share-delete handle would leave a deleted file's name occupied.
            val stale = File(out.parentFile, "${out.name}.stale-${System.currentTimeMillis()}")
            if (!out.renameTo(stale) && !out.delete()) {
                throw GradleException(
                    "${out.name} is locked and could not be replaced or renamed. " +
                        "Close the game/IDE holding it or run 'gradlew --stop', then rebuild."
                )
            }
        }
    }

    // Code only: Starsector reads data/, graphics/, sounds/ and mod_info.json from the mod folder
    // (the classpath is only its last fallback), so copies in the jar were dead weight.
    from(sourceSets.main.get().output)

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
            // Version Checker: data/config/version/version_files.csv points at this root file;
            // shipping the CSV without it makes LunaLib's loadJSON throw at startup.
            include("*.version")
            include("data/**")
            include("graphics/**")
            include("sounds/**")
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
