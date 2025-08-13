package com.lalilu.lplayer.menu

import com.lalilu.lplayer.macos.NSImage
import com.sun.jna.Pointer
import org.rococoa.*
import org.rococoa.cocoa.NSApplication
import org.rococoa.cocoa.foundation.NSObject

abstract class NSMenu : NSObject() {
    companion object {
        fun alloc(): NSMenu = Rococoa.create("NSMenu", NSMenu::class.java)
    }

    abstract fun title(): String?
    abstract fun init(): NSMenu
    abstract fun initWithTitle(title: String?): NSMenu
    abstract fun setTitle(title: String?)
    abstract fun removeAllItems()
    abstract fun removeItem(item: NSMenuItem?)
    abstract fun removeItemAtIndex(index: Int)
    abstract fun addItem(item: NSMenuItem?)
    abstract fun insertItem(item: NSMenuItem?, atIndex: Int)
    abstract fun itemAtIndex(index: Int): NSMenuItem?
    abstract fun numberOfItems(): Long
}

abstract class NSMenuItem : NSObject() {
    companion object {
        public fun alloc(): NSMenuItem = Rococoa.create("NSMenuItem", NSMenuItem::class.java)
    }

    abstract fun title(): String?
    abstract fun hasSubmenu(): Boolean
    abstract fun setSubmenu(menu: NSMenu?)
    abstract fun submenu(): NSMenu?
    abstract fun setTitle(title: String?)
    abstract fun action(): Pointer?
    abstract fun setAction(selector: Pointer?)
    abstract fun target(): ID
    abstract fun setTarget(target: ID)
    abstract fun tag(): String?
    abstract fun setTag(tag: String?)
    abstract fun init(): NSMenuItem
    abstract fun keyEquivalent(): String?
    abstract fun setKeyEquivalent(keyEquivalent: String?)
    abstract fun keyEquivalentModifierMask(): Int
    abstract fun setKeyEquivalentModifierMask(flags: Int)
    abstract fun image(): NSImage?
    abstract fun setImage(image: NSImage?)
    abstract fun initWithTitle(
        @NamedArg("title") string: String,
        @NamedArg("action") selector: Selector?,
        @NamedArg("keyEquivalent") charCode: String
    ): NSMenuItem
}

fun NSApplication.setMainMenu(menu: NSMenu) {
    Foundation.send(
        this.id(),
        Foundation.selector("setMainMenu:"),
        Void::class.java, menu
    )
}