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

@file:Suppress("UNCHECKED_CAST", "INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "DEPRECATED")

package com.lalilu.lmusic.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.HitTestResult
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.RootNodeOwner
import androidx.compose.ui.scene.ComposeScene
import androidx.compose.ui.scene.CopiedList
import androidx.compose.ui.scene.LocalComposeScene
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.util.fastForEachReversed
import java.lang.reflect.Method

@OptIn(InternalComposeUiApi::class)
@Composable
fun rememberLayoutHitTestOwner(): LayoutHitTestOwner {
    // TODO Remove LocalComposeScene
    val scene = LocalComposeScene.current ?: error("no compose scene")
    return remember(scene) {
        when (scene::class.qualifiedName) {
            "androidx.compose.ui.scene.CanvasLayersComposeSceneImpl" -> {
                CanvasLayersLayoutHitTestOwner(scene)
            }

            "androidx.compose.ui.scene.PlatformLayersComposeSceneImpl" -> {
                PlatformLayersLayoutHitTestOwner(scene)
            }

            else -> error("unsupported compose scene")
        }
    }
}

interface LayoutHitTestOwner {

    fun hitTest(x: Float, y: Float): Boolean {
        return false
    }
}

/*
* reflect implementation for compose 1.8
 */
internal abstract class ReflectLayoutHitTestOwner : LayoutHitTestOwner {

    @OptIn(InternalComposeUiApi::class)
    protected val classLoader = ComposeScene::class.java.classLoader!!

    protected fun getLayoutNode(rootNodeOwner: RootNodeOwner): LayoutNode {
        return rootNodeOwner.owner.root
    }

    protected fun LayoutNode.layoutNodeHitTest(x: Float, y: Float): Boolean {
        try {
            val result = HitTestResult()
            this.hitTest(pointerPosition = Offset(x, y), hitTestResult = result)
            val lastNode = result.lastOrNull()
            return lastNode is PointerInputModifierNode
        } catch (e: Exception) {
            // If anything goes wrong, return false to be safe
            return false
        }
    }
}

@OptIn(InternalComposeUiApi::class)
internal class PlatformLayersLayoutHitTestOwner(scene: ComposeScene) : ReflectLayoutHitTestOwner() {
    private val sceneClass = classLoader.loadClass("androidx.compose.ui.scene.PlatformLayersComposeSceneImpl")

    private val mainOwnerRef = sceneClass.getDeclaredMethod("getMainOwner").let {
        it.trySetAccessible()
        it.invoke(scene) as RootNodeOwner
    }

    override fun hitTest(x: Float, y: Float): Boolean {
        return mainOwnerRef.owner.root.layoutNodeHitTest(x, y)
    }
}

@OptIn(InternalComposeUiApi::class)
internal class CanvasLayersLayoutHitTestOwner(private val scene: ComposeScene) : ReflectLayoutHitTestOwner() {
    private val sceneClass = classLoader.loadClass("androidx.compose.ui.scene.CanvasLayersComposeSceneImpl")
    private val layerClass = sceneClass.declaredClasses
        .first { it.name == "androidx.compose.ui.scene.CanvasLayersComposeSceneImpl\$AttachedComposeSceneLayer" }

    private val mainOwnerRef = sceneClass.getDeclaredField("mainOwner").let {
        it.trySetAccessible()
        it.get(scene) as RootNodeOwner
    }

    private val _layers = sceneClass.getDeclaredField("_layersCopyCache").let {
        it.trySetAccessible()
        it.get(scene)
    } as CopiedList<*>

    private val focusedLayerField = sceneClass.getDeclaredField("focusedLayer").apply {
        trySetAccessible()
    }

    private val layerOwnerField = layerClass
        .getDeclaredField("owner").apply {
            trySetAccessible()
        }

    private var layerBoundsInWindowMethod: Method? = layerClass
        .declaredMethods.first {
            it.name.startsWith("getBoundsInWindow")
        }.apply {
            trySetAccessible()
        }

    fun isBoundsInWindow(layer: Any?, x: Float, y: Float): Boolean {
        val rect = layerBoundsInWindowMethod?.invoke(layer) as IntRect

        return x.toInt() in rect.left..rect.right &&
                y.toInt() in rect.top..rect.bottom
    }

    override fun hitTest(x: Float, y: Float): Boolean {
        _layers.withCopy {
            it.fastForEachReversed { layer ->
                if (isBoundsInWindow(layer, x, y)) {
                    return getLayoutNode(layerOwnerField.get(layer) as RootNodeOwner).layoutNodeHitTest(x, y)
                } else if (layer == focusedLayerField.get(scene)) {
                    return false
                }
            }
        }
        return getLayoutNode(mainOwnerRef).layoutNodeHitTest(x, y)
    }

}