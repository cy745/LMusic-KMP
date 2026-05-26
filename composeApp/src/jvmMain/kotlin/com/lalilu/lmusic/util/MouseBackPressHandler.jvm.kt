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