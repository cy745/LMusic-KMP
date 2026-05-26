/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lalilu.lmusic.jna.windows

import com.lalilu.lmusic.jna.windows.structure.WinUserConst.HTCLIENT
import com.lalilu.lmusic.jna.windows.structure.WinUserConst.HTCLOSE
import com.lalilu.lmusic.jna.windows.structure.WinUserConst.HTMAXBUTTON
import com.lalilu.lmusic.jna.windows.structure.WinUserConst.HTMINBUTTON
import com.lalilu.lmusic.jna.windows.structure.WinUserConst.HTTRANSPANRENT
import com.lalilu.lmusic.jna.windows.structure.WinUserConst.WM_LBUTTONDOWN
import com.lalilu.lmusic.jna.windows.structure.WinUserConst.WM_LBUTTONUP
import com.lalilu.lmusic.jna.windows.structure.WinUserConst.WM_MOUSEMOVE
import com.lalilu.lmusic.jna.windows.structure.WinUserConst.WM_NCHITTEST
import com.lalilu.lmusic.jna.windows.structure.WinUserConst.WM_NCLBUTTONDOWN
import com.lalilu.lmusic.jna.windows.structure.WinUserConst.WM_NCLBUTTONUP
import com.lalilu.lmusic.jna.windows.structure.WinUserConst.WM_NCMOUSEMOVE
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinDef.*
import com.sun.jna.platform.win32.WinUser
import org.jetbrains.skiko.SkiaLayer

class SkiaLayerWindowProcedure(
    skiaLayer: SkiaLayer,
    private val hitTest: (x: Float, y: Float) -> Int
) : WindowProcedure {

    private val windowHandle = HWND(Pointer(skiaLayer.windowHandle))
    internal val contentHandle = HWND(skiaLayer.canvas.let(Native::getComponentPointer))
    private val defaultWindowProcedure =
        User32Extend.instance?.setWindowLong(contentHandle, WinUser.GWL_WNDPROC, this) ?: BaseTSD.LONG_PTR(-1)

    private var hitResult = 1

    override fun callback(
        hwnd: HWND,
        uMsg: Int,
        wParam: WinDef.WPARAM,
        lParam: WinDef.LPARAM
    ): LRESULT {

        return when (uMsg) {

            WM_NCHITTEST -> {
                hitResult = lParam.useMousePoint { x, y -> hitTest(x.toFloat(), y.toFloat()) }
                when (hitResult) {
                    HTCLIENT, HTMAXBUTTON, HTMINBUTTON, HTCLOSE -> LRESULT(hitResult.toLong())
                    else -> LRESULT(HTTRANSPANRENT.toLong())
                }
            }

            WM_NCMOUSEMOVE -> {
                User32Extend.instance?.SendMessage(contentHandle, WM_MOUSEMOVE, wParam, lParam)
                LRESULT(0)
            }

            WM_NCLBUTTONDOWN -> {
                User32Extend.instance?.SendMessage(contentHandle, WM_LBUTTONDOWN, wParam, lParam)
                LRESULT(0)
            }

            WM_NCLBUTTONUP -> {
                User32Extend.instance?.SendMessage(contentHandle, WM_LBUTTONUP, wParam, lParam)
                return LRESULT(0)
            }

            else -> {
                User32Extend.instance?.CallWindowProc(defaultWindowProcedure, hwnd, uMsg, wParam, lParam) ?: LRESULT(0)
            }
        }
    }

    internal inline fun <T> WinDef.LPARAM.useMousePoint(crossinline block: (x: Int, y: Int) -> T): T {
        val lParamValue = toInt()
        val x = lParamValue.lowWord.toShort().toInt()
        val y = lParamValue.highWord.toShort().toInt()
        val point = POINT(x, y)
        User32Extend.instance?.ScreenToClient(windowHandle, point)
        point.read()
        val result = block(point.x, point.y)
        point.clear()
        return result
    }
}
