# Gradle Build System for Covenant Remnants Mod

This project has been converted from IntelliJ IDEA to use Gradle as the build system.

## Prerequisites

- Java 8 or higher
- Gradle (will be automatically downloaded via wrapper)

## Setup

1. **Download Gradle Wrapper JAR** (Windows):

   ```powershell
   .\download-gradle-wrapper.ps1
   ```

2. **Or manually download** the `gradle-wrapper.jar` file from:
   ```
   https://github.com/gradle/gradle/raw/v8.4.0/gradle/wrapper/gradle-wrapper.jar
   ```
   Place it in the `gradle/wrapper/` directory.

## Building the Mod

### Basic Build

```bash
# Windows
.\gradlew.bat build

# Linux/Mac
./gradlew build
```

### Build Mod JAR

```bash
# Windows
.\gradlew.bat buildMod

# Linux/Mac
./gradlew buildMod
```

### Package Mod for Distribution

```bash
# Windows
.\gradlew.bat packageMod      # or double-click package_mod.bat

# Linux/Mac
./gradlew packageMod          # or ./package_mod.sh
```

This builds a fresh jar and writes `Covenant Remnants.zip`. The zip holds one
`Covenant Remnants/` folder that players unzip into `mods/`. It contains only the files the game
needs at runtime:

- `mod_info.json` and `covenant-remnants.version`;
- `jars/CovenantRemnants.jar`;
- `data/`, `graphics/` and `sounds/`.

Two unused repo assets are excluded: `graphics/covenant_remnants/fx/shield/noise.png` and
`sounds/plasma_lance/fire_fp_*.ogg`. The file list lives only in `packageMod`. The two scripts just
call it.

### Clean Build

```bash
# Windows
.\gradlew.bat cleanMod

# Linux/Mac
./gradlew cleanMod
```

## Available Tasks

- `build` - Compiles and tests the project
- `buildMod` - Creates the mod JAR file in the `jars/` directory
- `stageMod` - Copies the runtime files into `build/mod_package/Covenant Remnants/`
- `packageMod` - Zips the staged folder into `Covenant Remnants.zip` (runs `stageMod` and `buildMod` first)
- `cleanMod` - Removes build artifacts and the `jars/` directory
- `jar` - Creates the basic JAR file
- `test` - Runs tests
- `clean` - Removes build directory

## Project Structure

The Gradle build system is configured to:

- Compile Kotlin source code from `src/`
- Include mod resources (`data/`, `graphics/`)
- Generate the mod JAR with proper manifest
- Support both Windows and Unix-like systems

## Dependencies

The project currently has these dependencies configured:

- Kotlin standard library
- Kotlin reflect
- JUnit for testing

**Note**: Starsector API and MagicLib dependencies are commented out in `build.gradle.kts`. You'll need to:

1. Add the actual JAR files to the `lib/` directory
2. Uncomment the corresponding dependency lines in `build.gradle.kts`

## IDE Integration

### IntelliJ IDEA

1. Open the project in IntelliJ IDEA
2. When prompted, choose "Open as Gradle Project"
3. IntelliJ will automatically import the Gradle configuration

### Eclipse

1. Install the Gradle plugin for Eclipse
2. Import the project as a Gradle project

### VS Code

1. Install the Gradle extension
2. Open the project folder
3. Use the Gradle sidebar to run tasks

## Troubleshooting

### Gradle Wrapper Issues

If you get errors about missing Gradle wrapper:

1. Ensure `gradle-wrapper.jar` exists in `gradle/wrapper/`
2. Run the download script: `.\download-gradle-wrapper.ps1`

### Build Issues

- Check that Java 8+ is installed and `JAVA_HOME` is set
- Ensure all required dependencies are available
- Check the Gradle build output for specific error messages

### Permission Issues (Linux/Mac)

If you get permission denied errors:

```bash
chmod +x gradlew
```

## Migration Notes

- The old IntelliJ project files (`.iml`, `.ipr`, `.iws`) can be safely deleted
- The `.idea/` directory can be removed if you're not using IntelliJ
- All build artifacts will now go to the `build/` directory
- The mod JAR will be created in the `jars/` directory
