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

package com.lalilu.preview

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import coil3.Image
import coil3.asImage
import java.net.HttpURLConnection
import java.net.URL
import kotlin.use

@RequiresApi(Build.VERSION_CODES.M)
actual fun createErrorBitmap(message: String): Image {
    val bitmap = createBitmap(768, 768)
    val canvas = Canvas(bitmap)
    val horizontalPadding = 20

    val paint = TextPaint().apply {
        textSize = 24f
    }

    val width = bitmap.width - horizontalPadding * 2
    val staticLayout = StaticLayout.Builder
        .obtain(message, 0, message.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, 1f)
        .setIncludePad(false)
        .build()

    canvas.translate(horizontalPadding.toFloat(), horizontalPadding.toFloat())
    staticLayout.draw(canvas)

    return bitmap.asImage()
}

@RequiresApi(Build.VERSION_CODES.KITKAT)
actual fun loadNetBitmap(url: String): Image? {
    val connection = URL(url)
        .openConnection() as HttpURLConnection

    connection.connectTimeout = 5000
    connection.readTimeout = 5000

    return connection.getInputStream().use {
        BitmapFactory
            .decodeStream(it)
            ?.asImage(false)
    }
}