# Playback Core

Kotlin Multiplatform 共享播放规则。唯一状态写入者为 `PlaybackCore`，平台只解析资源和执行带票据的音频命令。无 Compose、数据库或平台播放器依赖。

核心 API 见 `PlaybackContract.kt`；模块边界、实施计划与未实现项见 [实施记录](../../docs/architecture/implementation-plan.md)。

## 使用约定

1. 在平台运行时所要求的 dispatcher 上创建核心（Android Media3 为主线程）。
2. `dispatch(ReplaceQueue(...))` 后默认暂停；明确 `Play` 才开始发声。
3. `dispatch` 等待决策接受，不等待网络、解码或淡出。观察 `state` 获取实际阶段。
4. 所有运行时回调必须携带原选曲 `LoadTicket`；不能给旧回调贴上当前票据。定位还需匹配 `seekId`。
5. 来源与运行时的慢操作不能阻塞命令循环。UI 展示旋转和歌词拖动预览不写入实际队列。
6. 宿主结束时在运行时 dispatcher 调用 `close()`，释放该实例的资源。

Android 正式服务已在 P2 接入共享核心，P3 接入私有候选与连续输出。可选能力 `PreloadingAudioRuntime` 使用 `stageNext / cancelNext / confirmNext` 管理带独立序号的 `NextPlan`；候选不是正式队列项，只有实际自然边界经过核心确认后才改变当前项。见 [P3 协议与测量](../../docs/architecture/gapless-preloading.md)。

调试示例位于 `androidApp/src/debug/kotlin/com/lalilu/lmusic/prototype`，复用正式 Android 运行时但使用独立缓存目录。播放/暂停采用约 220 ms 的短音量过渡，手动切歌采用约 60 ms；自然衔接不淡化，系统强制中断或没有实际声音时立即暂停。核心通过 VolumeTransition 指定用途，各运行时共用单调时间增益曲线。

## 构建与模拟器验证

```sh
./gradlew :lplayer:playback-core:jvmTest \
  :lplayer:playback-core:compileKotlinIosSimulatorArm64 \
  :lplayer:playback-core:compileKotlinWasmJs \
  :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest

adb -s emulator-5554 install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb -s emulator-5554 install -r androidApp/build/outputs/apk/androidTest/debug/androidApp-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w \
  -e class com.lalilu.lmusic.prototype.GaplessDeviceTest,com.lalilu.lmusic.prototype.ProductionSessionDeviceTest,com.lalilu.lmusic.prototype.PlaybackCoreDeviceTest,com.lalilu.lmusic.prototype.ContentCacheDeviceTest \
  com.lalilu.lmusic.kmp.test/androidx.test.runner.AndroidJUnitRunner

adb -s emulator-5554 shell am start -W \
  -n com.lalilu.lmusic.kmp/com.lalilu.lmusic.prototype.PlaybackPrototypeActivity
```

示例固定指定模拟器，不能省略 serial 误操作已连接的手机。进入原型暂停旧播放器，退出释放原型，不自动恢复旧播放意图。Activity 自带生成的 A/B/C 音频，也提供读取现有媒体库的入口。

当前 Android 默认体验输出为 48 kHz PCM，单声道/立体声统一双声道；不代表原样输出或 Hi-Res 已验证。P4 已将 iOS、桌面及 Web 正式入口接入共享核心，输出能力和验证边界见 [P4 记录](../../docs/architecture/multiplatform-output.md)。这些后端的单项播放器仍不宣称连续无缝输出。交叉淡化不在当前范围。模拟器 PCM/AudioTrack 测量和正式页面回归不能替代真机/DAC、蓝牙以及主观听感验证。
