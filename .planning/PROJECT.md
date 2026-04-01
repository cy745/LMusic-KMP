# 项目：SongsScreen 选择功能迁移

**创建时间：** 2026-04-01
**项目类型：** 功能迁移
**状态：** 需求收集中

---

## What This Is

将原 Android 项目（LMusic）中 SongsScreen 的歌曲选择功能迁移到当前 KMP 项目的 AudioItemCard 组件中。

**核心价值：** 让用户能够在 SongsScreen 中批量选择歌曲进行操作（添加到收藏、添加到播放列表等）。

**项目背景：**
- 这是一个 Kotlin Multiplatform 音乐播放器项目
- 原项目已有完整的选择功能实现
- 当前项目已迁移大部分功能，但选择功能尚未完成

---

## Context

### 项目类型
- **类型：** Kotlin Multiplatform 项目（Android + Desktop）
- **技术栈：** Compose Multiplatform, Koin, Voyager
- **状态管理：** ViewModel + State

### 已有的基础设施
- ✅ `ItemSelector<T>` 类：管理选择状态
- ✅ `SongsSelectorPanel` UI 组件：底部选择操作面板
- ✅ `SongsVM` 中的 `selector` 实例
- ✅ "选择"按钮已集成在 ScreenAction 中

### 需要完成的核心任务
- ❌ `AudioItemCard` 组件不支持选择功能
- ❌ SongsScreenContent 中的点击逻辑未适配选择模式

---

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] **REQ-01**: AudioItemCard 支持选择状态显示
  - 选中时显示半透明灰色背景
  - 使用动画过渡背景色变化

- [ ] **REQ-02**: AudioItemCard 支持选择模式交互
  - 选择模式下：点击切换选中状态
  - 非选择模式下：点击播放歌曲
  - 长按封面图片：进入选择模式并选中当前歌曲
  - 长按卡片（非封面）：保持原有行为（跳转详情页）

- [ ] **REQ-03**: SongsScreenContent 集成选择功能
  - 传递 `isSelecting`, `isSelected`, `onSelect` 到 AudioItemCard
  - 根据选择模式调整点击行为

- [ ] **REQ-04**: 视觉效果与原项目一致
  - 选中背景色：`MaterialTheme.colors.onBackground.copy(0.15f)`
  - 动画过渡：`animateColorAsState`
  - 圆角：`RoundedCornerShape(2.dp)`

### Out of Scope

- 添加新的批量操作功能（保持与原项目一致的操作）
- 修改选择面板的布局或功能
- 添加收藏状态显示（favouriteIds 功能）
- 添加排序前缀显示（prefixContent 功能）

---

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| 在 AudioItemCard 上扩展而非创建新组件 | 现有组件已在使用，扩展可减少修改范围 | — Pending |
| 保持与原项目完全相同的视觉效果 | 用户明确要求，减少设计风险 | — Pending |
| 不迁移 favouriteIds 和 prefixContent | 简化范围，专注核心选择功能 | — Pending |

---

## Constraints

### 技术约束
- 必须使用 Compose Multiplatform（跨平台兼容）
- 必须使用现有的 `ItemSelector<T>` 状态管理
- 不能破坏现有的播放功能

### 设计约束
- 视觉效果必须与原 Android 项目一致
- 交互模式必须与原项目一致（避免用户混淆）

### 范围约束
- 仅修改 AudioItemCard 和 SongsScreenContent
- 不添加新的批量操作功能
- 不修改选择面板 UI

---

## Assumptions

1. **ItemSelector 实现已完善** - 假设现有的 `ItemSelector<T>` 类功能完整，无需修改
2. **SongsVM 已集成 selector** - 假设 ViewModel 中的选择器已正确初始化
3. **SongsSelectorPanel 功能正常** - 假设底部面板已正确显示和操作
4. **不需要跨平台特殊处理** - 假设选择功能在 Android 和 Desktop 平台行为一致

---

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---

*Last updated: 2026-04-01 after initialization*
