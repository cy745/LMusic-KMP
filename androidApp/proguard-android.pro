-dontnote **
-ignorewarnings

# JNI related
-keep class com.lalilu.lmedia.TaglibWrapper { *; }
# Metadata 由 taglib JNI 反射构造（bindings/jni/taglib_jni.h），类名不能被混淆
-keep class com.lalilu.lmedia.domain.model.Metadata { *; }

-keep class org.koin.** { *; }
-keep class org.koin.core.annotation.** { *; }
-keep @org.koin.core.annotation.* class * { *; }

# kotlin reflect related
-keep class kotlin.reflect.** { *; }

# platform related
