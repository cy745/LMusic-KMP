# lib-decoder-flac

FLAC 软解码扩展模块，基于 [AndroidX Media3](https://github.com/androidx/media) 的 `decoder_flac` 库，为不支持 FLAC 硬件解码的设备提供软件解码能力。

## 背景

该 AAR 不是从 Maven 仓库获取的（Media3 的 decoder 扩展不公开发布），而是从 Media3 源码编译。由于 AAR 是预编译二进制，当 Media3 版本升级引入新的接口方法时，预编译的类会导致 `AbstractMethodError`。

## 常见问题

### Media3 版本升级后播放 FLAC 崩溃

**症状**：点击播放 FLAC 文件时 App 闪退，日志中出现：

```
java.lang.AbstractMethodError: abstract method "boolean androidx.media3.extractor.SeekMap.isEstimated()"
    on receiver java.lang.Class<androidx.media3.decoder.flac.FlacExtractor$FlacSeekMap>

java.lang.AbstractMethodError: abstract method "java.util.List androidx.media3.extractor.Extractor.getSniffFailureDetails()"
    on receiver java.lang.Class<androidx.media3.decoder.flac.FlacExtractor>
```

**原因**：Media3 升级后在 `SeekMap`、`Extractor` 等接口中新增了默认方法，但由于 ART/D8 的类链接机制，预编译 AAR 中的类即使面对 default 方法也会抛出 `AbstractMethodError`。

## 重新编译 AAR

### 1. 克隆 Media3 仓库

```shell
cd ~/AndroidStudioProjects
git clone --depth 1 --branch <media3-version> https://github.com/androidx/media.git media3
```

> 将 `<media3-version>` 替换为当前项目使用的版本（见 `gradle/libs.versions.toml` 中的 `media3` 版本）。

### 2. 获取 libflac native 源码

```shell
cd media3/libraries/decoder_flac/src/main/jni
git clone https://github.com/xiph/flac.git --depth=1 libflac
```

### 3. 设置 ANDROID_HOME

```shell
export ANDROID_HOME=~/Library/Android/sdk
```

### 4. 修改源码以显式实现新增的 default 方法

打开 `libraries/decoder_flac/src/main/java/androidx/media3/decoder/flac/FlacExtractor.java`，确保以下方法有显式的 `@Override` 实现：

```java
// FlacSeekMap 内部类中
@Override
public boolean isEstimated() {
    return false;
}

// FlacExtractor 类中
@Override
public List<SniffFailure> getSniffFailureDetails() {
    return ImmutableList.of();
}

@Override
public Extractor getUnderlyingImplementation() {
    return this;
}
```

> **提示**：Media3 升级后可能新增更多 default 方法。编译前对比 `libraries/extractor/src/main/java/androidx/media3/extractor/Extractor.java` 和 `SeekMap.java` 接口，确保 `FlacExtractor` / `FlacSeekMap` 显式 override 了所有新增方法。

同时需要添加相应的 import：

```java
import androidx.media3.extractor.SniffFailure;
import com.google.common.collect.ImmutableList;
import java.util.List;
```

### 5. 编译 AAR

```shell
cd ~/AndroidStudioProjects/media3
./gradlew :lib-decoder-flac:assembleRelease
```

### 6. 替换到项目

```shell
cp libraries/decoder_flac/buildout/outputs/aar/lib-decoder-flac-release.aar \
   ~/AndroidStudioProjects/LMusic-KMP/lplayer/lib-decoder-flac/lib-decoder-flac-release.aar
```

### 7. 验证

```shell
cd /tmp && unzip -o <aar-path> classes.jar
javap -p -cp classes.jar 'androidx.media3.decoder.flac.FlacExtractor$FlacSeekMap' | grep isEstimated
javap -p -cp classes.jar 'androidx.media3.decoder.flac.FlacExtractor' | grep getSniffFailureDetails
```

两个方法都应出现在输出中。

## 依赖关系

```
lplayer:lib-decoder-flac (AAR, 手动编译)
├── libflac (native .so: arm64-v8a, armeabi-v7a, x86, x86_64)
└── Media3 lib-decoder / lib-extractor (编译时依赖)
```

## 相关链接

- [AndroidX Media 仓库](https://github.com/androidx/media)
- [Media3 发布说明](https://github.com/androidx/media/releases)
- [FLAC 解码扩展文档](https://github.com/androidx/media/tree/main/libraries/decoder_flac)
