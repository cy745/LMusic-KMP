package com.lalilu.navigation

import androidx.compose.runtime.compositionLocalOf
import com.lalilu.component.ModalBottomSheetState

val LocalModalBottomSheetState =
    compositionLocalOf<ModalBottomSheetState> { error("AnchoredDraggableState Not provided") }