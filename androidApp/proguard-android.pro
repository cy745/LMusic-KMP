-dontnote **
-ignorewarnings

# JNI related
-keep class com.lalilu.lmedia.TaglibWrapper { *; }
# Metadata 由 taglib JNI 反射构造（bindings/jni/taglib_jni.h），类名不能被混淆
-keep class com.lalilu.lmedia.domain.model.Metadata { *; }

# Subsonic API 响应实体（ktorfit + ContentNegotiation 运行时反序列化）：
# R8 混淆会破坏序列化器查找，release 包报
# NoTransformationFoundException「Expected response body of the type 'hd.m' ...」
# （hd.m 即被混淆的 SubsonicResponseWrapper / 各响应实体）
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,Signature
-keep class com.lalilu.lmedia.source.subsonic.** { *; }
# Remote 源配置同为 @Serializable + 运行时反序列化，与 Subsonic 相同的混淆风险
-keep class com.lalilu.lmedia.source.RemoteSourceConfig { *; }

-keep class org.koin.** { *; }
-keep class org.koin.core.annotation.** { *; }
-keep @org.koin.core.annotation.* class * { *; }

# kotlin reflect related
-keep class kotlin.reflect.** { *; }

# platform related
