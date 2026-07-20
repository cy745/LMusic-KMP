plugins {
    kotlin("jvm")
}

dependencies {
    api("net.java.dev.jna:jna:5.17.0")
}

// Execute Xcode build for native libraries
tasks.register<Exec>("buildNative") {
    onlyIf {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("mac")) return@onlyIf false

        return@onlyIf try {
            val process = ProcessBuilder("which", "xcode").start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    workingDir = File(projectDir, "./")
    commandLine(
        "xcodebuild",
        "-project",
        "wrapper.xcodeproj",
        "-target",
        "libwrapper",
        "-configuration",
        "Release"
    )

    // Only run this task if the output files don't exist or are older than the sources
    outputs.file(File(workingDir, "build/Release/libwrapper.dylib"))

    doFirst {
        println("Building native libraries...")
    }
}

tasks.register<Exec>("cleanNative") {
    onlyIf {
        val os = System.getProperty("os.name").lowercase()
        if (!os.contains("mac")) return@onlyIf false

        return@onlyIf try {
            val process = ProcessBuilder("which", "xcode").start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    workingDir = File(projectDir, "./")
    commandLine(
        "xcodebuild",
        "-project",
        "wrapper.xcodeproj",
        "-target",
        "libwrapper",
        "-configuration",
        "Release",
        "clean"
    )

    doFirst {
        println("Cleaning native libraries...")
    }
}

tasks.named("clean") {
    dependsOn("cleanNative")
}

// 构建后将 libwrapper.dylib 复制到 appResourcesRootDir 的 macOS 资产目录
tasks.register<Copy>("copyToAssets") {
    dependsOn("buildNative")
    from(File(projectDir, "build/Release/libwrapper.dylib"))
    into(rootDir.resolve("lplayer/src/jvmMain/assets/macos/"))
}

tasks.named("build") {
    dependsOn("buildNative")
    dependsOn("copyToAssets")
}
