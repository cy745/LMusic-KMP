# 原生二进制的来源与重建

仓库里有若干平台绑定的原生二进制，它们**不在 Gradle 的常规依赖体系里**，因此只看仓库无法判断
「这些字节从哪来、缺了什么」。本文记录每一份的来源与重建方式，避免再出现依赖缺失却无从追溯的情况。

## 1. TagLib

位置：`lmedia/lmedia-core/src/jvmMain/resources/natives/`（按平台分目录，`NativeLoader` 按 `natives/<os>_<arch>/` 查找）

作用：读取音频元数据（时长 / 标题 / 艺术家 / 封面 / 歌词）。

| 文件 | 平台 | 来源 |
|---|---|---|
| `linux_64/libtag.so` | Linux x64 | `cy745/taglib` CI（ubuntu-latest job） |
| `osx_64/libtag.dylib`、`osx_arm64/libtag.dylib` | macOS | `cy745/taglib` CI / 本地构建 |
| `windows_64/tag.dll` | Windows x64 | `cy745/taglib` CI（windows-latest MSVC job，RegisterNatives 版） |
| `windows_64/z.dll` | Windows x64 | `cy745/taglib` CI 随产物提供（见下）；本地可用上游 zlib 1.3.2 + MSVC 重建 |

**只有 `lmedia-core` 持有这份资源**。`lmedia-server` 曾复制过一份，其中 `windows_64/tag.dll` 是
更新前的旧构建，而 `shadowJar` 用 `DuplicatesStrategy.INCLUDE` 会让两份同名 dll 都进包、运行时加载哪份不确定
（加载到旧 dll 会与 core 的 RegisterNatives 版 JNI 声明不匹配而崩溃）。该副本已删除，
server 通过 `implementation(project(":lmedia:lmedia-core"))` 复用同一套原生库，
并由 `lmedia-server/src/test/.../ServerTaglibSmokeTest.kt` 守住这条约定。

⚠️ Windows 的 `tag.dll` 与 `z.dll` 应**取自同一次 CI 构建**（CI 产物 `windows-latest` 的 `bin/` 下两者齐全）。
换了 `tag.dll` 却没同步 `z.dll`（或反之）会重新引入本文描述的加载问题。

构建仓库：<https://github.com/cy745/taglib>（工作流 `.github/workflows/build.yml`，taglib v2.3.1）

### Windows 上为什么还需要 z.dll

Windows 的 `tag.dll` 是 MSVC 构建、动态链接 zlib，导入表中记录的 DLL 名是 **`z.dll`**：

- vcpkg 的 `zlib` 端口在 `x64-windows` 下从**上游 madler/zlib** 构建，而上游 `CMakeLists.txt` 中
  `set_target_properties(zlib PROPERTIES OUTPUT_NAME z)` → Windows 产物就叫 `z.dll`（不是 `zlib1.dll`）。
- CI 会把 `C:/vcpkg/installed/x64-windows/bin/z.dll` 拷到 `tag.dll` 旁边用于链接，
  **但 `upload-artifact` 只上传 CMake install 前缀（`output/`），并不包含 vcpkg 的运行时依赖**。
  该缺陷已由 <https://github.com/cy745/taglib/pull/6> 修复：workflow 在 install 之后显式把
  `z.dll` 并入 `output/bin` 并断言其存在。**拉取到早于该修复的产物时，需要自行补 `z.dll`。**
- `zlib1.dll`（旧构建的依赖，zlib-ng 那套导出）已从仓库删除：新 `tag.dll` 不使用它，
  留着只会让"依赖到底是哪个"更难判断。

### 重建 z.dll

```powershell
# 1. 取上游源码（与 vcpkg 一致：madler/zlib v1.3.2）
curl.exe -sL -o zlib.tar.gz https://api.github.com/repos/madler/zlib/tarball/v1.3.2
tar -xf zlib.tar.gz --strip-components=1 -C src

# 2. MSVC + CMake 构建共享库（产物名由上游固定为 z）
call "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"
cmake -S src -B build -G Ninja -DCMAKE_BUILD_TYPE=Release -DBUILD_SHARED_LIBS=ON
cmake --build build
# 产物 build/z.dll → 复制到 windows_64/
```

⚠️ `NativeLoader` 只抽取**被请求的那一个**库。因此 `TaglibWrapper` 会先显式加载 `z` / `zlib1`，
把它们抽到与 `tag.dll` 相同的临时目录，之后才加载 `tag`——新增/更换依赖时需要同步这一处。

## 2. VLC

位置：`lplayer/src/jvmMain/assets/<os>/vlc/`（**不入库**，`.gitignore` 排除）

由 `ir.mahozad.vlc-setup` 插件（fork：`cy745/vlc-setup`）在构建期下载并解压。重新生成：

```bash
./gradlew :composeApp:vlcSetup
```

⚠️ `composeApp` 配置了 `shouldIncludeAllVlcFiles = true`。若资源是更早的 minimal 配置生成的，
Windows 桌面会缺少 HTTP/HTTPS 与 callback-memory(imem) 插件，导致网络音源与内存字节流无法播放。
判定方法：`plugins/access/` 下是否存在 `libhttp_plugin.dll` 与 `libimem_plugin.dll`。

## 3. Rococoa

位置：`lplayer/src/jvmMain/assets/macos/`（入库）

`librococoa.dylib` + `libwrapper.dylib`，macOS 专用，用于 Objective-C 运行时桥接。
非 macOS 宿主上相关测试由 `assumeDesktopOs(DesktopOs.MACOS)` 跳过（见 `docs/testing-guide.md`）。
