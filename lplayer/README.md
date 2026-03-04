# lplayer

播放器核心模块，处理音频播放的核心逻辑。

## 功能

- 播放控制（播放/暂停/上一首/下一首）
- 播放模式（顺序/随机/单曲循环）
- 播放队列管理
- 歌词同步

## 平台实现

| 平台 | 实现 |
|------|------|
| Android | Media3/ExoPlayer |
| iOS | AVFoundation |
| Desktop (JVM) | VLCJ |
| Web | Web Audio API |

## 依赖

- `lmedia-core` - 媒体数据
- `llyricview` - 歌词显示
- `component` - UI 组件
