-dontusemixedcaseclassnames                 # 基本配置：关闭大小写混合（避免Windows系统问题）
-dontskipnonpubliclibraryclasses            # 不跳过非公共的库类（确保第三方库能被正确处理）
-dontskipnonpubliclibraryclassmembers       # 不跳过非公共的库类成员
-verbose                                    # 输出ProGuard的日志信息
-keepattributes *Annotation*                # 保留注解信息
-keepattributes SourceFile,LineNumberTable  # 保留源文件和行号信息（便于崩溃时回溯）

-dontnote **
-ignorewarnings

# JNI related
-keep class com.lalilu.lmedia.TaglibWrapper { *; }
-keep class com.lalilu.lmedia.entity.* { *; }

-keep class org.koin.** { *; }
-keep class org.koin.core.annotation.** { *; }
-keep @org.koin.core.annotation.* class * { *; }

# kotlin reflect related
-keep class kotlin.reflect.** { *; }

# platform related
-keep class uk.co.caprica.vlcj.** { *; }
-keep class com.sun.jna.** { *; }
-keep class * extends com.sun.jna.** { *; }
-keep class org.rococoa.cocoa.** { *; }
-keep class * extends org.rococoa.cocoa.** { *; }
-keep class * extends coil3.util.FetcherServiceLoaderTarget { *; }
-keep class * extends coil3.util.DecoderServiceLoaderTarget { *; }
-keep class io.netty.util.internal.logging.** { *; }
-keep class okio.** { *; }