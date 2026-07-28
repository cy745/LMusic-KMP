/*
 * Copyright (c) 2026 lalilu. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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