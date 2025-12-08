package com.lalilu

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.lalilu.component.component.generated.resources.Res
import com.lalilu.component.component.generated.resources.noto_sans_sc_vf
import com.materialkolor.DynamicMaterialTheme
import org.jetbrains.compose.resources.Font

val LocalFontFamily = staticCompositionLocalOf<FontFamily> { error("No font family provided") }
val LocalSeedColor = staticCompositionLocalOf<MutableState<Color>> { error("No seed color state provided") }

@Composable
fun LMusicTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val seedColorState = remember { mutableStateOf(Color.Red) }
    val fontWeight = remember { (100..900 step 100).map { FontWeight(it) } }
    val fonts = fontWeight.map { Font(resource = Res.font.noto_sans_sc_vf, weight = it) }
    val fontFamily = remember { FontFamily(fonts) }

    CompositionLocalProvider(
        LocalSeedColor provides seedColorState,
        LocalFontFamily provides fontFamily
    ) {
        DynamicMaterialTheme(
            seedColor = seedColorState.value,
            isDark = isDarkTheme,
            animate = true,
            content = content,
        )
    }
}