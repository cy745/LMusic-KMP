plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.lalilu"
version = "1.0-SNAPSHOT"

dependencies {
    implementation(libs.gradlePlugin.kotlin)
    implementation(libs.gradlePlugin.android)
    implementation(libs.gradlePlugin.vanniktech.publish)
}

gradlePlugin {
    plugins.register("build-logic") {
        id = "build-logic"
        implementationClass = "com.lalilu.BuildLogicPlugin"
    }
    plugins.register("com.lalilu.resignore") {
        id = "com.lalilu.resignore"
        implementationClass = "com.lalilu.resignore.ResIgnorePlugin"
    }
    plugins.register("com.lalilu.cmpshrink") {
        id = "com.lalilu.cmpshrink"
        implementationClass = "com.lalilu.cmpshrink.CmpShrinkPlugin"
    }
}
