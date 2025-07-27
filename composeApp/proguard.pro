-keep class com.sun.jna.** { *; }
-keep class org.freedesktop.dbus.** { *; }
-keep class org.koin.** { *; }
-keep class org.slf4j.** { *; }

-dontnote com.sun.jna.**
-dontnote org.freedesktop.dbus.**
-dontnote org.freedesktop.dbus.**
-dontnote org.slf4j.**

-dontwarn org.koin.**

# Keep annotation definitions
-keep class org.koin.core.annotation.** { *; }
# Keep classes annotated with Koin annotations
-keep @org.koin.core.annotation.* class * { *; }