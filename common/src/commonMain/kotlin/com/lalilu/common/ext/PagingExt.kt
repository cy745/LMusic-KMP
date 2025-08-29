package com.lalilu.common.ext

/**
 * 分页获取所有数据
 *
 * 通过分页请求的方式获取所有数据，避免一次性获取大量数据导致内存问题。
 * 该函数会持续请求数据直到某一页返回的数据为空或少于最大数量为止。
 *
 * @param maxSize 每页请求的最大数据量，默认为500
 * @param request 分页请求函数，接收size(每页数量)和offset(偏移量)作为参数，返回数据列表
 * @return T 类型数据的完整列表
 */
suspend fun <T> retrieveAllPage(
    maxSize: Int = 500,
    request: suspend (size: Int, offset: Int) -> List<T>
): List<T> {
    val result = mutableListOf<T>()
    var offset = 0

    while (true) {
        // 请求数据
        val page = request(maxSize, offset)

        // 添加数据
        offset += maxSize
        result.addAll(page)

        // 如果没有数据或者数据不足500条，则结束循环
        if (page.isEmpty()) break
        if (page.size < maxSize) break
    }

    return result
}