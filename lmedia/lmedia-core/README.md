# lmedia-core

媒体库核心模块，处理本地音乐文件的扫描和元数据读取。

## 功能

- 音频文件扫描
- 元数据读取（Taglib）
- 媒体数据模型：LAudio、LAlbum、LArtist、LFolder、LGenre
- 跨平台媒体源适配

## 平台支持

| 平台 | 媒体源 |
|------|--------|
| Android | MediaStore |
| iOS | MusicKit |
| Desktop | 文件系统 |
| Web | 文件系统 (WASM) |

## 依赖

- `common` - 基础工具
- Taglib - 音频标签读取
