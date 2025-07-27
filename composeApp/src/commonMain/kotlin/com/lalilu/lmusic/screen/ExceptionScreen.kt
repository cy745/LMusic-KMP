package com.lalilu.lmusic.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lalilu.krouter.annotation.Destination
import io.github.hristogochev.vortex.screen.Screen


@Destination("/error")
class ExceptionScreen(private val exception: Exception) : Screen {

    @Composable
    override fun Content() {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Exception: ${exception.message}")
        }
    }

    companion object Companion {
        val SCREEN_NOT_FOUND = ExceptionScreen(Exception("Screen not found"))
    }
}