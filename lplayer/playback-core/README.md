# Playback Core 原型

Kotlin Multiplatform 共享播放规则。唯一状态写入者为 `PlaybackCore`，平台只解析资源和执行带票据的音频命令。无 Compose、数据库或平台播放器依赖。

核心 API 见 `PlaybackContract.kt`；模块边界、实施计划与未实现项见 [实施记录](../../docs/architecture/implementation-plan.md)。

## 使用约定

1. 在平台运行时所要求的 dispatcher 上创建核心（Android Media3 原型为主线程）。
2. `dispatch(ReplaceQueue(...))` 后默认暂停；明确 `Play` 才开始发声。
3. `dispatch` 等待决策接受，不等待网络、解码或淡出。观察 `state` 获取实际阶段。
4. 所有运行时回调必须携带原选曲 `LoadTicket`；不能给旧回调贴上当前票据。定位还需匹配 `seekId`。
5. 来源与运行时的慢操作不能阻塞命令循环。UI 展示旋转和歌词拖动预览不写入实际队列。
6. 宿主结束时在运行时 dispatcher 调用 `close()`，释放该实例的资源。

Android 示例位于 `androidApp/src/debug/kotlin/com/lalilu/lmusic/prototype`。它只在前台运行，不取代正式媒体服务。音频固定较低测试增益，手动切歌/播放/暂停采用约 60 ms 的渐变；这些是原型参数，还未进行主观听感调校。

## 构建与模拟器验证

```sh
./gradlew :lplayer:playback-core:jvmTest \
  :lplayer:playback-core:compileKotlinIosSimulatorArm64 \
  :lplayer:playback-core:compileKotlinWasmJs \
  :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest

adb -s emulator-5554 install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb -s emulator-5554 install -r androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w \
  -e class com.lalilu.lmusic.prototype.PlaybackCoreDeviceTest \
  com.lalilu.lmusic.kmp.test/androidx.test.runner.AndroidJUnitRunner

adb -s emulator-5554 shell am start -W \
  -n com.lalilu.lmusic.kmp/com.lalilu.lmusic.prototype.PlaybackPrototypeActivity
```

示例固定指定模拟器，不能省略 serial 误操作已连接的手机。进入原型暂停旧播放器，退出释放原型，不自动恢复旧播放意图。Activity 自带生成的 A/B/C 音频，也提供读取现有媒体库的入口。

原型不支持后台媒体服务、候选预加载、无缝衔接、分段缓存、Hi-Res 输出验证或交叉淡化。iOS/Web 目前仅验证共享核心可编译，没有接入对应音频后端。正式播放页没有迁移，不能用此原型测试结果替代其 UI 回归。
