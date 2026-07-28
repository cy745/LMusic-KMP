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

package com.lalilu.lmusic.util

import androidx.annotation.MainThread
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner

@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.handleMouseBackPress(): Modifier = composed {
    val dispatcherOwner = LocalNavigationEventDispatcherOwner.current
    val onBackPress = remember {
        val input = object : NavigationEventInput() {
            @MainThread
            fun onBackPress() {
                dispatchOnBackStarted(NavigationEvent())
                dispatchOnBackCompleted()
            }
        }.also { dispatcherOwner?.navigationEventDispatcher?.addInput(it) }

        input::onBackPress
    }

    onPointerEvent(eventType = PointerEventType.Press) {
        if (it.buttons.isBackPressed) {
            onBackPress()
        }
    }
}