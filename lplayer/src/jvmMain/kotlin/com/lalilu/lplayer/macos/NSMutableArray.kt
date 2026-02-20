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

package com.lalilu.lplayer.macos

import org.rococoa.Foundation
import org.rococoa.NamedArg
import org.rococoa.cocoa.foundation.NSDictionary
import org.rococoa.cocoa.foundation.NSObject
import org.rococoa.cocoa.foundation.NSString

abstract class NSMutableDictionary : NSDictionary() {

    /**
     * - (void) setValue:(ObjectType) value
     *            forKey:(NSString *) key;
     */
    abstract fun setValue(
        @NamedArg("value") value: NSObject,
        @NamedArg("forKey") key: String
    )

    abstract fun setValue(
        @NamedArg("value") value: NSObject,
        @NamedArg("forKey") key: NSString
    )

    /**
     * - (void) setObject:(ObjectType) anObject
     *             forKey:(id<NSCopying>) aKey;
     */
    abstract fun setObject(
        @NamedArg("object") anObject: NSObject,
        @NamedArg("forKey") aKey: String
    )

    abstract fun setObject(
        @NamedArg("object") anObject: NSObject,
        @NamedArg("forKey") aKey: NSString
    )
}

fun NSDictionary.mutableCopy(): NSMutableDictionary {
    return Foundation.send(
        this.id(),
        Foundation.selector("mutableCopy"),
        NSMutableDictionary::class.java
    )
}