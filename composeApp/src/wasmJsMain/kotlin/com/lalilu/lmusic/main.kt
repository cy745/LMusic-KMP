package com.lalilu.lmusic

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
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
    platformSetupCoil()

    ComposeViewport(document.body!!) {
        ReplaceFont {
            App()
        }
    }
}

@OptIn(ExperimentalResourceApi::class)
@Composable
fun ReplaceFont(content: @Composable () -> Unit = {}) {
    val fontResolver = LocalFontFamilyResolver.current
    val resource = rememberResourceEnvironment()
    val fontReady = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch {
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

            fontReady.value = true
        }
    }

    AnimatedVisibility(
        modifier = Modifier.fillMaxSize(),
        visible = fontReady.value,
        exit = fadeOut(),
        enter = fadeIn()
    ) {
        content()
    }
}