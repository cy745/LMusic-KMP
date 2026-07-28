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

package com.lalilu

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface Mvi<State, Event> {
    suspend fun reduce(action: suspend (State) -> State)
    suspend fun postEvent(event: Event)
    suspend fun postEvent(action: suspend () -> Event) = postEvent(action())

    @Stable
    fun stateFlow(): StateFlow<State>

    @Stable
    fun eventFlow(): SharedFlow<Event>
}

interface MviWithIntent<State, Event, Intent> : Mvi<State, Event> {
    fun intent(intent: Intent): Any
}

fun <State, Event> mviImpl(
    defaultValue: State
): Mvi<State, Event> {
    return object : Mvi<State, Event> {
        private val stateFlow: MutableStateFlow<State> = MutableStateFlow(defaultValue)
        private val eventFlow: MutableSharedFlow<Event> = MutableSharedFlow()

        override suspend fun reduce(action: suspend (State) -> State) =
            stateFlow.emit(action(stateFlow.value))

        override suspend fun postEvent(event: Event) = eventFlow.emit(event)
        override fun stateFlow(): StateFlow<State> = stateFlow
        override fun eventFlow(): SharedFlow<Event> = eventFlow
    }
}

fun <State, Event, Intent> mviImplWithIntent(
    defaultValue: State
): MviWithIntent<State, Event, Intent> {
    return object : MviWithIntent<State, Event, Intent> {
        private val stateFlow: MutableStateFlow<State> = MutableStateFlow(defaultValue)
        private val eventFlow: MutableSharedFlow<Event> = MutableSharedFlow()

        override suspend fun reduce(action: suspend (State) -> State) =
            stateFlow.emit(action(stateFlow.value))

        override suspend fun postEvent(event: Event) = eventFlow.emit(event)
        override fun stateFlow(): StateFlow<State> = stateFlow
        override fun eventFlow(): SharedFlow<Event> = eventFlow
        override fun intent(intent: Intent): Any = Unit
    }
}