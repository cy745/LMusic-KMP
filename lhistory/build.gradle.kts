/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import com.lalilu.applyMultiplatform
import com.lalilu.main
import com.lalilu.test

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
}

group = "com.lalilu.lhistory"
version = "1.0.0"

applyMultiplatform {
    main.dependencies {
        implementation(project(":component"))
        implementation(project(":lmedia:lmedia-core"))
        implementation(project(":lmedia:lmedia-data"))
        implementation(project(":lmedia:lmedia-ui"))
        implementation(project(":lplayer"))
        implementation(libs.remixicon.kmp)
        implementation(libs.compose.resources)
        implementation(libs.compose.preview)
        implementation(libs.paging.compose)
    }

    test.dependencies {
        implementation(libs.kotlin.test)
    }
}