package com.lalilu.test

import com.lalilu.lplayer.macos.MPMediaItemProperty
import com.lalilu.lplayer.macos.MPNowPlayingInfoCenter
import com.lalilu.lplayer.macos.MPNowPlayingInfoProperty
import com.lalilu.lplayer.menu.NSMenu
import com.lalilu.lplayer.menu.NSMenuItem
import com.lalilu.wrapper.WrapperLibrary
import org.junit.Test
import org.rococoa.Foundation
import org.rococoa.cocoa.foundation.NSArray
import org.rococoa.cocoa.foundation.NSDictionary
import org.rococoa.cocoa.foundation.NSNumber
import org.rococoa.cocoa.foundation.NSString

class RococoaTest {

    init {
        val projectPath = System.getProperty("user.dir")
        val nativeLibPath = "$projectPath/src/jvmMain/resources/osx"
        System.setProperty("jna.library.path", nativeLibPath)
        WrapperLibrary.instance
        println(nativeLibPath)
    }

    @Test
    fun testNSMenu() {
        val menu = NSMenu.alloc().initWithTitle("test")
        val menuItem = NSMenuItem.alloc().initWithTitle("test item", null, "p")

        menu.addItem(menuItem)
        assert(menu.numberOfItems() == 1L) { "menu item not added" }

        menu.removeItem(menuItem)
        assert(menu.numberOfItems() == 0L) { "menu item not removed" }
    }

    @Test
    fun testMPNowPlayingInfoCenter() {
        val defaultCenter = MPNowPlayingInfoCenter.defaultCenter()
        var info = defaultCenter.nowPlayingInfo()

        println("info count before: ${info?.count()}")

        val keys = NSArray.CLASS.arrayWithObjects(
            MPMediaItemProperty.Title.nativeValue,
            MPMediaItemProperty.Artist.nativeValue,
            MPMediaItemProperty.PlaybackDuration.nativeValue,
            MPNowPlayingInfoProperty.PlaybackRate.nativeValue,
            MPNowPlayingInfoProperty.ElapsedPlaybackTime.nativeValue,
            MPNowPlayingInfoProperty.IsLiveStream.nativeValue
        )
        val values = NSArray.CLASS.arrayWithObjects(
            NSString.stringWithString("TEST Title"),
            NSString.stringWithString("TEST Artist"),
            NSNumber.CLASS.numberWithLong(60000L),
            NSNumber.CLASS.numberWithDouble(1.0),
            NSNumber.CLASS.numberWithLong(10000L),
            NSNumber.CLASS.numberWithBool(false)
        )

        val dictionary = NSDictionary.CLASS.dictionaryWithObjects_forKeys(values, keys)
        defaultCenter.setNowPlayingInfo(dictionary)
        info = defaultCenter.nowPlayingInfo()

        println("info count after: ${info?.count()} ${info?.allKeys()}")
        val allKeys = info?.allKeys() ?: return

        for (index in 0 until allKeys.count()) {
            val key = allKeys.objectAtIndex(index)
            val value = info.objectForKey(key)
            println("[$index]: ${key} -> ${value}")
        }
    }
}

fun NSDictionary.allKeys(): NSArray {
    return Foundation.send(
        this.id(),
        Foundation.selector("allKeys"),
        NSArray::class.java
    )
}


