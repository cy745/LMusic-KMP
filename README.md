# LMusic

一个简洁优雅的跨平台音乐播放器，基于 Kotlin + Compose Multiplatform 构建，一套代码同时运行于 Android、iOS、Web (WASM) 和 Desktop 平台。

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-blue?logo=kotlin)](https://kotlinlang.org)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-1.11.0--alpha01-purple)](https://www.jetbrains.com/lp/compose-multiplatform)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue)](LICENSE)
[![Gradle](https://img.shields.io/badge/Gradle-8.11.1-orange?logo=gradle)](https://gradle.org)
[![CI Status](https://github.com/cy745/LMusic-KMP/actions/workflows/main.yml/badge.svg)](https://github.com/cy745/LMusic-KMP/actions)

## 截图

<!-- TODO: 准备截图 -->

| Android | iOS | Desktop | Web |
|:---:|:---:|:---:|:---:|
| TODO | TODO | TODO | TODO |

## 特性

- 跨平台：Android、iOS、Web (WASM)、Desktop (JVM)
- 本地音乐管理：扫描、分类、搜索
- 歌词显示：支持 LRC、TTML 格式
- 局域网媒体服务：跨设备播放

## 项目结构

```
LMusic-KMP/
├── composeApp/          [应用入口](composeApp/README.md)        ⭐ 应用入口
├── common/              [通用工具库](common/README.md)         🔧 基础工具
├── component/           [UI组件库](component/README.md)         🎨 复用组件
├── lplayer/             [播放器核心](lplayer/README.md)        🎵 播放控制
├── lhome/               [主页模块](lhome/README.md)           🏠 首页UI
├── llyric/              [歌词解析](llyric/README.md)          📝 格式解析
├── llyricview/          [歌词视图](llyricview/README.md)      🎶 歌词展示
├── lmedia/              媒体库管理
│   ├── lmedia-core/     [核心媒体处理](lmedia/lmedia-core/README.md)
│   ├── lmedia-ui/       媒体库UI
│   ├── lmedia-client/   客户端服务
│   ├── lmedia-coil/    图片加载
│   └── lmedia-server/  局域网服务
└── thirdparty/          第三方封装
```

## 编译运行

### 环境要求

- JDK 21+
- Gradle 8.11.1
- Android Studio / IntelliJ IDEA 2024+
- (iOS 开发) Xcode 15+

### 各平台运行

| 平台 | 开发运行 | 打包 |
|------|----------|------|
| Android | `./gradlew :composeApp:installDebug` | `./gradlew :composeApp:assembleRelease` |
| iOS | Xcode 打开 `iosApp` | Xcode Archive |
| Desktop | `./gradlew :composeApp:run` | `./gradlew :composeApp:packageDistribution` |
| Web | `./gradlew :composeApp:wasmJsBrowserDevelopmentRun` | `./gradlew :composeApp:wasmJsBrowserProductionWebpack` |

## 贡献指南

欢迎贡献代码！请先阅读 [贡献指南](CONTRIBUTING.md)。

### 开发流程

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/your-feature`
3. 提交更改：`git commit -m 'feat: add some feature'`
4. 推送分支：`git push origin feature/your-feature`
5. 创建 Pull Request

### 代码规范

- 提交信息遵循 [Conventional Commits](https://www.conventionalcommits.org/)
- 确保代码通过 `./gradlew check`

## 协议

- 主协议：[Apache License 2.0](LICENSE)
- 第三方组件：[NOTICE](NOTICE)
