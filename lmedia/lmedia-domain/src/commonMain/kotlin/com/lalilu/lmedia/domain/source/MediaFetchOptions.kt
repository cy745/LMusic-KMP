package com.lalilu.lmedia.domain.source

/**
 * 媒体获取选项，传递额外参数给 [MediaDataSource] 方法。
 *
 * 当前用于传递图片尺寸（由 Coil 的目标尺寸驱动），
 * 未来可扩展以支持码率、格式、起始位置等参数而不破坏接口兼容性。
 *
 * @param width  请求宽度（像素），0 表示不指定
 * @param height 请求高度（像素），0 表示不指定
 */
data class MediaFetchOptions(
    val width: Int = 0,
    val height: Int = 0,
) {
    companion object {
        /** 无额外参数的默认值 */
        val EMPTY = MediaFetchOptions()
    }
}
