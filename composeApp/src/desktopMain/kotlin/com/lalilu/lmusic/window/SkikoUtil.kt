/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lalilu.lmusic.window


import androidx.compose.ui.awt.ComposeWindow
import org.jetbrains.skiko.SkiaLayer
import java.awt.Container
import javax.swing.JComponent

internal fun <T : JComponent> findComponent(
    container: Container,
    klass: Class<T>,
): T? {
    val componentSequence = container.components.asSequence()
    return componentSequence.filter { klass.isInstance(it) }.ifEmpty {
        componentSequence.filterIsInstance<Container>()
            .mapNotNull { findComponent(it, klass) }
    }.map { klass.cast(it) }.firstOrNull()
}

internal inline fun <reified T : JComponent> Container.findComponent() =
    findComponent(this, T::class.java)

internal fun ComposeWindow.findSkiaLayer(): SkiaLayer? = findComponent<SkiaLayer>()