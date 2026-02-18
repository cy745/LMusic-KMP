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

/**
 * 创建一个表示错误状态的位图图像
 *
 * 当图像加载完全失败且没有其他备选方案时，此函数会被调用以生成一个
 * 显示错误信息的图像。这个图像通常会包含错误的堆栈跟踪或其他诊断信息。
 *
 * @param message 错误信息字符串，通常是异常的堆栈跟踪
 * @return 表示错误状态的 [Image] 对象
 */
expect fun createErrorBitmap(message: String): Image

/**
 * 从网络URL直接加载位图图像
 *
 * 此函数作为 Coil 图像加载器的备选方案，在主加载流程失败时使用。
 * 它绕过 Coil 的缓存和处理机制，直接从网络获取图像数据。
 *
 * @param url 要加载的图像的网络地址
 * @return 成功加载的 [Image] 对象，如果加载失败则返回 null
 */
expect fun loadNetBitmap(url: String): Image?

/**
 * Coil 图像处理器，用于处理图像加载请求和错误情况
 *
 * 这个对象实现了 [AsyncImagePreviewHandler] 接口，为 Coil 图像加载库提供
 * 自定义的图像处理逻辑。它支持网络图像加载、备用 URL 和错误处理功能。
 *
 * 主要特性：
 * - 支持启用/禁用网络图像加载
 * - 提供备用 URL 机制
 * - 实现多层错误处理和恢复策略
 * - 集成自定义的错误图像生成功能
 */
@OptIn(ExperimentalCoilApi::class)
internal object CoilImageHandler : AsyncImagePreviewHandler {
    /**
     * 控制是否允许从网络加载图像的标志
     *
     * 当设置为 true 时，[CoilImageHandler.loadImage] 方法会尝试从网络加载图像。
     * 默认值为 false，需要显式启用。
     */
    private var isNetworkImageEnabled = false

    /**
     * 备用图像 URL
     *
     * 当主图像加载失败时，系统会尝试使用此 URL 加载备用图像。
     * 如果为空或与主 URL 相同，则不使用备用机制。
     */
    private var fallbackUrl: String? = null

    /**
     * 启用网络图像加载功能
     *
     * 调用此方法后，[CoilImageHandler.loadImage] 方法将能够从网络加载图像。
     * 注意：这可能会产生网络请求，应谨慎使用。
     */
    fun enableNetworkImage() {
        isNetworkImageEnabled = true
    }

    /**
     * 设置备用图像 URL
     *
     * @param url 备用图像的网络地址
     */
    fun setFallbackUrl(url: String) {
        fallbackUrl = url
    }

    /**
     * 处理图像请求的核心方法
     *
     * 此方法是图像加载流程的入口点，负责协调各种加载策略：
     * 1. 首先尝试使用 Coil 的标准加载流程
     * 2. 如果失败，尝试直接从 URL 加载
     * 3. 如果仍然失败且设置了备用 URL，则尝试加载备用图像
     * 4. 最后，如果所有方法都失败，生成错误图像
     *
     * @param imageLoader Coil 图像加载器实例
     * @param request 图像加载请求对象
     * @return 表示加载状态的 [AsyncImagePainter.State]
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
                    var temp = loadImage(request)
                        ?.let { AsyncImagePainter.State.Loading(it.asPainter(request.context)) }

                    if (temp == null && !fallbackUrl.isNullOrBlank() && request.data != fallbackUrl) {
                        val fallbackRequest = ImageRequest.Builder(request)
                            .data(fallbackUrl)
                            .build()
                        temp = loadImage(fallbackRequest)
                            ?.let { AsyncImagePainter.State.Loading(it.asPainter(request.context)) }
                    }

                    temp ?: throw result.throwable
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
     * 这个方法用作备选方案，在 Coil 的常规加载流程失败时尝试直接获取图像数据。
     * 它会检查网络加载是否启用，并使用 [RenderSecurityHelper] 来处理可能的安全限制。
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