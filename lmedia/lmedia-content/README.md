# lmedia-content

方案 B 的内容访问与通用缓存模块。接口、UML、接入规则和限制见 [P1 说明](../../docs/architecture/content-access-cache.md)。

- `CatalogCache`：目录/展示元数据原子提交，显式 Complete/Partial 与刷新代次。
- `RangeContentCache`：稳定身份与内容版本隔离，有界范围读取、磁盘校验、并发去重和活动句柄保护。
- `HttpRangeReader`：缓存缺失时才解析 URL/凭据，严格验证 HTTP Range 和内容版本。
- `SourceContentLibrary`：现有来源兼容桥；未声明缓存身份的来源不猜测缓存键。

每个磁盘根目录必须只有一个宿主实例；磁盘操作注入 IO dispatcher。持有 ContentHandle 的调用方必须关闭句柄。来源回调必须提供自己的超时、取消支持和启用/账户状态校验。

```sh
./gradlew :lmedia:lmedia-content:jvmTest \
  :lmedia:lmedia-content:compileKotlinIosSimulatorArm64 \
  :lmedia:lmedia-content:compileKotlinWasmJs
./gradlew :lmedia:lmedia-client:jvmTest --tests '*WebDavSourceTest*'
./gradlew :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest
adb -s emulator-5554 install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb -s emulator-5554 install -r androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w \
  -e class com.lalilu.lmusic.prototype.ContentCacheDeviceTest,com.lalilu.lmusic.prototype.PlaybackCoreDeviceTest \
  com.lalilu.lmusic.kmp.test/androidx.test.runner.AndroidJUnitRunner
```

模拟器测试使用自身创建的 HTTP 服务、音频和临时目录，不需要用户媒体库或外部账号。正式播放器继续使用旧路径，P2 才处理统一系统会话和生产 UI 接入。
