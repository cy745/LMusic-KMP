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

package com.lalilu.lmedia.data.database.converter

import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.parameter.ParametersDefinition
import org.koin.core.qualifier.Qualifier
import org.koin.mp.KoinPlatformTools

private inline fun <reified T : Any> KoinComponent.injectOrTest(
    qualifier: Qualifier? = null,
    mode: LazyThreadSafetyMode = KoinPlatformTools.defaultLazyMode(),
    noinline parameters: ParametersDefinition? = null,
    crossinline block: () -> T
): Lazy<T> = lazy(mode) { runCatching { get<T>(qualifier, parameters) }.getOrElse { block() } }


interface SerializableConverter<T> {
    companion object : KoinComponent {
        private val json: Json by injectOrTest { Json { ignoreUnknownKeys = true } }
    }

    fun json(): Json = json
    fun from(string: String): T
    fun to(item: T): String
}