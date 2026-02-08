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

import coil3.Image
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePainter
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.asPainter
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult

expect fun createErrorBitmap(message: String): Image

expect fun loadNetBitmap(url: String): Image?

@OptIn(ExperimentalCoilApi::class)
internal object CoilImageHandler : AsyncImagePreviewHandler {
    private var isNetworkImageEnabled = false

    fun enableNetworkImage() {
        isNetworkImageEnabled = true
    }

    /**
     * 处理图像请求的主要方法
     *
     * @param imageLoader 图像加载器实例
     * @param request 图像加载请求
     * @return 返回适当的 [AsyncImagePainter.State] 状态
     */
    override suspend fun handle(
        imageLoader: ImageLoader,
        request: ImageRequest
    ): AsyncImagePainter.State {
        return runCatching {
            // 执行图像请求并根据结果进行处理
            when (val result = imageLoader.execute(request)) {
                is SuccessResult -> AsyncImagePainter.State.Success(
                    painter = result.image.asPainter(request.context),
                    result = result
                )

                is ErrorResult -> {
                    // 在错误情况下，尝试直接从 URL 加载图像
                    loadImage(request)
                        ?.let { AsyncImagePainter.State.Loading(it.asPainter(request.context)) }
                        ?: throw result.throwable
                }
            }
        }.getOrElse {
            // 如果所有加载方法都失败，则创建一个显示错误信息的位图
            val image = createErrorBitmap(it.stackTraceToString())
            AsyncImagePainter.State.Loading(image.asPainter(request.context))
        }
    }


    /**
     * 从 URL 直接加载图像的辅助方法
     *
     * 这个方法用作备选方案，在 Coil 的常规加载流程失败时尝试直接获取图像数据
     *
     * @param request 图像加载请求，其中的数据应该是 URL 字符串
     * @return 成功解码的 [Image] 对象，如果失败则返回 null
     */
    private fun loadImage(
        request: ImageRequest
    ): Image? {
        val url = request.data as? String
            ?: return null

        if (!isNetworkImageEnabled) {
            return null
        }

        // 只在必要时暂时禁用渲染安全
        return RenderSecurityHelper.withTemporarilyDisableRenderSecurity {
            loadNetBitmap(url)
        }
    }
}