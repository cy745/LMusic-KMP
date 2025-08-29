package com.lalilu.common.ext

import io.ktor.utils.io.core.*
import org.kotlincrypto.hash.md.MD5

/**
 * 计算字符串的MD5哈希值
 *
 * 将当前字符串转换为字节数组，然后使用MD5算法计算其哈希值，
 * 最后将哈希值转换为十六进制字符串表示。
 *
 * @return 表示MD5哈希值的十六进制字符串
 */
fun String.md5(): String {
    return MD5().digest(this.toByteArray())
        .toHexString()
}