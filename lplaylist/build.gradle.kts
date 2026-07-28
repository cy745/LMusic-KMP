/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.lalilu.gradle.setupKoin
import com.lalilu.gradle.setupMultiplatform

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
}

group = "com.lalilu.lplaylist"
version = "1.0.0"

kotlin {
    setupMultiplatform()
    setupKoin()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":component"))
            implementation(project(":lmedia:lmedia-core"))
            implementation(project(":lmedia:lmedia-data"))
            implementation(project(":lmedia:lmedia-ui"))
            implementation(project(":lplayer"))
            implementation(libs.remixicon.kmp)
            implementation(libs.compose.resources)
            implementation(libs.compose.preview)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
