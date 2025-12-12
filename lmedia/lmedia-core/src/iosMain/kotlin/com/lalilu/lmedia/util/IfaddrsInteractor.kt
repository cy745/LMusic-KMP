package com.lalilu.lmedia.util

import kotlinx.cinterop.*
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.posix.*

/**
 * 网络接口
 */
data class IfAddresses(
    val ifName: String,
    val afInet: String?,
    val afInet6: String?,
    val ifFlag: Int = 0
) {
    val isUp get() = ifFlag and IFF_UP != 0
    val isRunning get() = ifFlag and IFF_RUNNING != 0
    val isLoopback get() = ifFlag and IFF_LOOPBACK != 0
    val isMulticast get() = ifFlag and IFF_MULTICAST != 0
    val isBroadcast get() = ifFlag and IFF_BROADCAST != 0
    val isPointToPoint get() = ifFlag and IFF_POINTOPOINT != 0
    val hasIpv4 get() = afInet != null
    val hasIpv6 get() = afInet6 != null
    val isValid get() = afInet != null || afInet6 != null
}

/**
 * 获取所有网络接口
 */
@OptIn(ExperimentalForeignApi::class)
object IfaddrsInteractor {
    private const val TAG = "IfaddrsInteractor"

    fun get(netInterfaces: Set<String> = emptySet()): Collection<IfAddresses> {
        if (netInterfaces.isEmpty()) return emptyList()
        return getAll().filter { it.ifName in netInterfaces }
    }

    fun getAll(): Collection<IfAddresses> = memScoped {
        val output = mutableSetOf<IfAddresses>()
        val ifaddrPtr = allocPointerTo<ifaddrs>()

        if (getifaddrs(ifaddrPtr.ptr) == 0) {
            val ifaddr = ifaddrPtr.pointed ?: return@memScoped emptySet()
            var addr = ifaddr.ifa_next?.reinterpret<ifaddrs>()
            var afInet: String? = null
            var afInet6: String? = null

            while (addr != null) {
                val ifaName = addr.pointed.ifa_name?.reinterpret<ByteVar>()?.toKString()

                if (ifaName != null) {
                    val socketAddr = addr.pointed.ifa_addr?.reinterpret<sockaddr>()
                    val currentSaFamily = socketAddr?.pointed?.sa_family?.toInt()

                    if (currentSaFamily == AF_INET || currentSaFamily == AF_INET6) {
                        val saLen = socketAddr.pointed.sa_len
                        val hostname = allocArray<ByteVar>(length = NI_MAXHOST)

                        getnameinfo(socketAddr, saLen.toUInt(), hostname, NI_MAXHOST.toUInt(), null, 0u, NI_NUMERICHOST)

                        when (currentSaFamily) {
                            AF_INET -> afInet = hostname.toKString()
                            AF_INET6 -> afInet6 = hostname.toKString()
                            else -> Unit
                        }

                        output += IfAddresses(
                            ifName = ifaName,
                            afInet = afInet,
                            afInet6 = afInet6,
                            ifFlag = addr.pointed.ifa_flags.toInt(),
                        )
                    }
                }

                addr = addr.pointed.ifa_next?.reinterpret()
            }

            freeifaddrs(ifaddr.ptr)
        }

        return output
    }
}