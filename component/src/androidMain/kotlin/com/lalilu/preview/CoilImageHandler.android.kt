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