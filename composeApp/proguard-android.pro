-dontnote **
-ignorewarnings

# JNI related
-keep class com.lalilu.lmedia.TaglibWrapper { *; }
-keep class com.lalilu.lmedia.entity.* { *; }

-keep class org.koin.** { *; }
-keep class org.koin.core.annotation.** { *; }
-keep @org.koin.core.annotation.* class * { *; }

# platform related
