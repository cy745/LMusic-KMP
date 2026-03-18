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