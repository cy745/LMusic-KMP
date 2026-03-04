# composeApp

应用入口模块，包含各平台的启动入口和主应用配置。

## 平台入口

| 平台 | 入口文件 |
|------|----------|
| Android | `src/androidMain/kotlin/.../MainActivity.kt` |
| iOS | `src/iosMain/kotlin/.../MainViewController.kt` |
| Desktop | `src/desktopMain/kotlin/.../main.kt` |
| Web | `src/wasmJsMain/kotlin/.../main.kt` |

## 功能

- 应用启动初始化
- 主题配置
- 导航栈管理
- Koin 依赖注入配置

## 依赖模块

- `common` - 通用工具
- `component` - UI 组件
- `lplayer` - 播放器
- `lhome` - 主页
- `lmedia:*` - 媒体库
