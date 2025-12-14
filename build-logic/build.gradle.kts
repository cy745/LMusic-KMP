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
}
