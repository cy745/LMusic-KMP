plugins {
    kotlin("jvm")
    application
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.shadowJar)
}

group = "com.lalilu.lmedia"
version = "0.0.1"

dependencies {
    implementation(project(":common"))
    implementation(project(":lmedia:lmedia-core"))
    implementation(project(":lmedia:lmedia-server"))

    implementation("com.github.ajalt.clikt:clikt:5.0.3")
    implementation("com.github.ajalt.clikt:clikt-markdown:5.0.3")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application.apply {
    mainClass.set("com.lalilu.lmedia.MainKt")
}

tasks.shadowJar {
    mainClass.set("com.lalilu.lmedia.MainKt")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}