package com.lalilu.lmusic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import lmusic_kmp.composeapp.generated.resources.NotoColorEmoji
import lmusic_kmp.composeapp.generated.resources.NotoSansSC_Regular
import lmusic_kmp.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.rememberResourceEnvironment
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin { koinSetup() }

    ComposeViewport(document.body!!) {
        platformSetupCoil()
        App()
        ReplaceFont()
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun ReplaceFont() {
    val fontResolver = LocalFontFamilyResolver.current
    val resource = rememberResourceEnvironment()

    LaunchedEffect(Unit) {
        listOf(
            "NotoColorEmoji" to Res.font.NotoColorEmoji,
            "Noto Sans Thin" to Res.font.NotoSansSC_Regular
        ).map {
            async {
                val data = getFontResourceBytes(resource, it.second)
                val font = Font(identity = it.first, data = data)
                fontResolver.preload(FontFamily(listOf(font)))
            }
        }.awaitAll()
    }
}