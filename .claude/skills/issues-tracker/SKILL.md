---
name: issues-tracker
description: 用 GitHub Issues 追踪项目任务和进度。支持智能搜索相似 issue、创建前查重（重复则补充评论而非新建）、状态看板、进度报告。触发词：issue、问题、任务、追踪、进度、看板、bug、feature。
---

# Issues Tracker — GitHub Issues 任务追踪

## 项目上下文

| 字段 | 值 |
|------|-----|
| 仓库 | `cy745/LMusic-KMP` |
| 远程 | `https://github.com/cy745/LMusic-KMP` |
| GitHub CLI | `gh` (已认证) |
| 现有 Labels | bug, feature, task, enhancement, documentation, duplicate, good first issue, help wanted, invalid, question, wontfix, platform:android, platform:ios, platform:jvm, platform:web, priority:high, priority:medium, priority:low, needs-triage, needs-info |
| 当前分支 | `dev` |
| 默认分支 | `main` |

---

## 概览

本 skill 帮助 Agent 在 LMusic-KMP 项目中通过 GitHub Issues 系统化地追踪任务、bug 和功能需求。

### 🧭 核心理念：渐进式信息收集

> **「先记下来，再慢慢补全」**
>
> 用户不总是能提供完整信息——他们可能记不清版本号、不会看日志、没截图。这没关系。
>
> **原则：**
> - 能问的就问，问不到的不强求
> - 信息不足的 issue 照样创建，标记 `needs-info` 并注明缺失项即可
> - 后续有相似反馈的用户，会在搜索时找到这个 issue，通过补充评论逐步拼凑完整画像
> - 每一次补充评论都是一次信息增益，问题会越变越清晰
>
> **不要因为信息不全就拒绝用户。** 一个模糊的问题加上 `needs-info`，比一个从未被创建的 issue 有价值得多。

核心能力：

| 能力 | 说明 |
|------|------|
| **智能搜索** | 通过多轮问询收集完整上下文，在现有 issues 中精确匹配相似问题，输出可信度评分 |
| **智能创建** | 创建前自动查重，发现重复则补充评论而非新建，保持 issue 收敛 |
| **状态看板** | 按 label / milestone / assignee / 时间范围 快速查看项目全景 |
| **进度报告** | 生成项目健康度报告、各维度统计、待办汇总 |
| **生命周期管理** | 关闭/标记/分配/关联 issue，批量操作 |

---

## 核心流程

### 一、智能搜索 — 在现有 issues 中匹配相似问题

#### 触发条件

用户描述了一个问题/bug/需求，且未要求「直接创建 issue」。

#### 执行步骤

##### Step 1: 多轮问询收集上下文（缺失即问）

不要一次性抛出所有问题。逐轮问，用户每轮回答后判断是否够用。**最少问询清单**（按优先级排列）：

| # | 信息项 | 问询形式 | 必备？ |
|---|--------|---------|-------|
| 1 | **问题简述** | 一句话描述你遇到的问题 | ✅ |
| 2 | **复现路径** | 从哪个页面/操作开始？具体步骤？ | ✅ |
| 3 | **预期行为 vs 实际行为** | 你期望发生什么？实际发生了什么？ | ✅ |
| 4 | **环境信息** | 平台(Android/iOS/JVM/Web)、版本号、设备型号 | ✅ |
| 5 | **日志/错误信息** | 有 crash log / 错误堆栈 / 控制台输出吗？ | 强烈建议 |
| 6 | **截图/录屏** | 有截图或屏幕录制吗？（路径位于文件系统可读） | 建议 |
| 7 | **触发频率** | 必现 / 偶尔出现 / 仅特定条件下出现？ | 建议 |
| 8 | **影响范围** | 阻塞开发 / 影响体验 / 轻微瑕疵 | 建议 |
| 9 | **代码上下文** | 涉及哪个模块/组件？找到对应文件了吗？ | 强烈建议（开发场景） |

**问询原则：**
- 优先问必备项（1-4），用户回答完后自动提取关键词开始搜索
- 搜索初步结果回来后再追问建议项（5-9），以提升搜索精度
- 不要在用户刚说完一句话时就一次追问 9 个问题
- **信息不足不阻止** — 用户说"不知道版本"或"没日志"就直接跳过，能问到多少算多少
- 问 2-3 轮后如果用户仍无法提供更多信息，停止追问，进入下一步


##### Step 1.5: 版本追踪（当用户说"最新版"时主动定位 commit）

用户大多记不住版本号，但这不代表我们没法知道。**主动去查，不要只记录"最新版"三个字。**

**具体操作：**

```bash
# 查 GitHub Actions 最新构建（用于 snapshot.lalilu.cn 的包）
gh run list --repo cy745/LMusic-KMP --limit 5 --json headBranch,headSha,createdAt,displayTitle,status,workflowName

# 查最近 commit log（开发环境用）
git log --oneline -10

# 查最新 tag / release
gh release list --repo cy745/LMusic-KMP --limit 5
```

| 用户说法 | 你的操作 |
|---------|---------|
| "从 snapshot.lalilu.cn 下的" | 查 GitHub Actions 最近一次成功构建 → 拿到 commit SHA 和构建时间 → 问"你是今天还是昨天下的？"→ 锁定 commit |
| "GitHub Releases 下的" | `gh release list` 看最新 release 的 tag + commit → 直接写入 issue |
| "Play Store / App Store 装的" | 查 `build.gradle.kts` 中的 versionName，或让用户在 App 设置页找版本号 |
| "自己编译的" | 让用户 `git log --oneline -1` 或 `git rev-parse HEAD` |
| "朋友发给我的" | 问大概什么时候收到的、文件名叫什么（可能包含 commit SHA） |

> **原则：** 不要接受「最新版」作为版本记录。至少要记录一个**时间范围** + **commit SHA 范围**。
> snapshot.lalilu.cn 每次新构建都会覆盖旧包，只说"从 snapshot 下的"没有价值，必须锁定 commit。

---

##### Step 2: 关键词提取

从用户提供的全部信息中提取关键词。策略：

```
优先级分组：
  P0 (高权重) — 错误签名、crash 类名、错误码、API 路径
  P1 (中权重) — 功能名、组件名、文件路径、操作名
  P2 (低权重) — 平台、版本号、环境描述、UI 文案
```

提取示例：
```
用户描述: "点击播放列表的歌曲时崩溃，java.lang.NullPointerException at MediaPlayerService.play()"
→ P0: NullPointerException, MediaPlayerService.play()
→ P1: 播放列表, 歌曲, 点击, MediaPlayerService
→ P2: Android, 崩溃
```

##### Step 3: 搜索现有 issues

```bash
# 宽搜（P0 + 部分 P1）
gh issue list --repo cy745/LMusic-KMP --state all --search "NullPointerException MediaPlayerService" --limit 20 --json number,title,state,labels,body,createdAt,comments

# 窄搜（P0 精确匹配）
gh issue list --repo cy745/LMusic-KMP --state all --search "NullPointerException in:title" --limit 10 --json number,title,state,labels,body,createdAt,comments
```

搜索策略：
1. 先用 P0 关键词宽搜（最多 20 条）
2. 若无结果，降级为 P1+P2 宽搜
3. 若还有结果过少（<3 条），不加关键词限制搜相关 label 下所有 open issues

##### Step 4: 获取候选 issue 详情

对搜索返回的候选 issues，逐一获取完整内容：

```bash
gh issue view <number> --repo cy745/LMusic-KMP --json number,title,state,labels,body,comments,createdAt,updatedAt,closedAt
```

##### Step 5: 相似度分析 & 可信度评分

对每个候选 issue 逐一评分。评分维度及权重：

| 维度 | 权重 | 评分逻辑 |
|------|------|----------|
| **关键词重叠率** | 40% | P0 匹配 +30%，P1 匹配 +15%，P2 匹配 +5%。基准：P0 匹配数/P0 总数 |
| **错误签名匹配** | 25% | crash 堆栈/错误码完全匹配 → 100%；部分匹配 → 50%；无 → 0% |
| **功能区域匹配** | 15% | 同一组件/文件/功能区域 → 100%；相邻区域 → 50%；无关 → 0% |
| **环境匹配** | 10% | 同一平台+同一模块 → 100%；仅同一平台 → 50%；不同 → 0% |
| **场景相似度** | 10% | 复现步骤的关键操作序列重叠度（人工判断） |

**可信度分级输出：**

```
📊 相似度分析结果（共扫描 N 条 issues，找到 M 个候选）

─────────────────────────────────────────────
🔴 >85% 高度匹配 ──── 极大概率是同一个问题
   → 直接标记为 duplicate，在该 issue 下补充案例

🟡 60-85% 中度匹配 ── 可能是同一问题或强关联问题
   → 在该 issue 下回复补充案例，并 @ 作者确认

🟢 30-60% 弱匹配 ──── 有部分相似但不确定关联
   → 列出匹配点和差异点，让用户决定是否创建新 issue

⚪ <30% 低匹配 ────── 基本不相关
   → 可安全创建新 issue
─────────────────────────────────────────────
```

##### Step 6: 输出搜索结果

对每个候选，输出以下格式：

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
#{number} — {title}  [{state}]  🔴可信度 {score}%
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📅 创建: {createdAt}  |  更新: {updatedAt}  |  💬 {comments} 条评论
🏷️ Labels: {labels}
📋 描述摘要: {body 前 200 字}
✅ 匹配依据: {列出匹配到的关键词/错误签名/组件}
❌ 差异项: {列出未能匹配的内容}
📌 建议操作: {补充评论 / 关闭当前 / 创建新 issue}
─────────────────────────────────────────────
```

---

### 二、智能创建 — 创建新 issue（带查重）

#### 触发条件

用户明确要求「创建 issue」或经智能搜索后确认需要新建。

#### 执行步骤

##### Step 1: 收集信息（能收多少收多少，不够先记着）

使用模板引导用户提供信息，**但信息不足时仍然创建 issue，标记 `needs-info` 并注明缺失项即可**。

<details>
<summary><b>Bug 报告模板</b></summary>

```markdown
> 📝 不知道的字段留空即可，后续有人补充时会在这里累积

### 描述
{请描述你遇到的问题，越详细越好}

### 复现步骤（如果有的话）
1. {从哪一步开始？}
2. {然后做了什么？}
3. {问题出现了吗？}

### 预期行为
{你觉得应该发生什么}

### 实际行为
{实际发生了什么}

### 环境
- **适用平台（可多选）**:
  - [ ] Android
  - [ ] iOS
  - [ ] JVM / Desktop
  - [ ] Web
- **版本**: {版本号 或 commit SHA（优先从 GitHub Actions / git log 查，不接受"最新版"作为记录）}
- **设备**: {设备型号}
- **系统版本**: {Android 版本 / iOS 版本 / macOS 版本}

### 日志 / 错误信息（有的话请贴上来，没有就不填）
```
{错误堆栈或日志}
```

### 截图 / 录屏（有的话可以贴文件路径）
{文件路径}

### 补充信息
- 触发频率: {必现 / 偶尔 / 特定条件下}
```
</details>

<details>
<summary><b>Feature 请求模板</b></summary>

```markdown
> 📝 不知道的字段留空即可

### 描述
{你想添加什么功能}

### 使用场景
{什么场景下需要这个功能？解决了什么问题}

### 预期行为
{你期望这个功能如何工作}

### 适用平台（可多选）
- [ ] Android
- [ ] iOS
- [ ] JVM / Desktop
- [ ] Web

### 可选方案
{其他可行的替代方案}

### 实现思路（可选）
{如果你有技术层面的想法，可以写在这里}
```
</details>

<details>
<summary><b>Task / 任务模板</b></summary>

```markdown
### 任务描述
{做什么事情}

### 适用平台（可多选）
- [ ] Android
- [ ] iOS
- [ ] JVM / Desktop
- [ ] Web

### 验收标准
- [ ] {标准 1}
- [ ] {标准 2}
- [ ] {标准 3}

### 关联
- 关联 issue: #{number}
- 依赖: #{number}
```
</details>

**宽容规则（不拒稿，标注缺失即可）：**

| 场景 | 做法 |
|------|------|
| 用户只给了模糊描述（如"有 bug"） | → 先引导问一轮，问不到仍创建，标记 `needs-info` |
| 缺少「复现步骤」 | → 创建并标注 `缺失: 复现步骤` |
| 缺少「日志/错误信息」 | → 创建并标注 `缺失: 日志` |
| 缺少「系统版本」 | → 创建并标注 `缺失: 系统版本` |
| 用户明确表示不清楚某个字段 | → 跳过，不追问 |
| 用户给了足够多信息（≥4 个必备项） | → 正常创建，不加 `needs-info` |

> **核心理念：** 一个信息不完整的 issue 是一个种子。第二个用户遇到同样问题时会搜到它，补充评论说"我也遇到了，我的版本是 xxx"——信息就拼上了。第三、第四个用户继续补充，这个 issue 会自己长大。

> **一句话启动场景：** 如果用户只丢来一句话（如"播放列表有 bug"），先执行**智能搜索流程**查重，确认无重复后按模板引导一轮，问不到的直接跳过，创建 issue 加 `needs-info` 即可。

##### Step 2: 执行智能搜索（查重）

调用「智能搜索」流程（见第一章），在当前 issues 中搜索相似问题。

**决策树：**

```
智能搜索结果
│
├─ 🔴 高度匹配 (≥85%)
│  → 不创建新 issue
│  → 在该 issue 下补充评论："案例补充: {用户提供的补充信息}"
│  → 如果当前 issue 已 closed，建议 reopen 或在评论中 @ 作者
│
├─ 🟡 中度匹配 (60-84%)
│  → 不创建新 issue
│  → 在该 issue 下回复："此案例与 #{number} 相似，但存在以下差异: {差异点}。请确认是否为同一问题"
│  → 等待用户确认后，再决定补充或新建
│
├─ 🟢 弱匹配 (30-59%)
│  → 展示匹配点和差异项给用户
│  → 让用户选择：在现有 issue 下补充 / 创建新 issue
│
└─ ⚪ 低匹配 (<30%)
   → 直接创建新 issue（进入 Step 3）
```

##### Step 3: 创建 issue

**创建前检查清单（重要！）：**

| # | 检查项 | 说明 |
|---|--------|------|
| 1 | ✅ 标题前缀 | 标题必须以 `【Bug】` `【Feature】` `【Task】` 开头，对应 issue 类型 |
| 2 | ❌ 模板说明文字 | 去掉 `> 📝 不知道的字段留空即可`、`（有的话请贴上来）`、`（如果有的话）` 等 Agent 内部指引 |
| 3 | ❌ 占位符残留 | 确认没有 `{版本号或 commit hash}` 等模板占位符未被替换 |
| 4 | ✅ commit SHA | 版本字段必须填具体 commit SHA 或 tag，不接受"最新版""最新 snapshot 包" |
| 5 | ✅ 复现步骤去掉"如果" | `### 复现步骤` 下直接写步骤，不要写"如果有的话" |
| 6 | 🚫 **敏感信息检查** | 没有 API Key / Token / 密码 / 证书 / 本地绝对路径 / 私有会话引用 |

**标题前缀规则：**

| 类型 | 前缀 | 示例 |
|------|------|------|
| Bug | `【Bug】` | `【Bug】Windows 卸载时删除整个安装目录...` |
| Feature | `【Feature】` | `【Feature】桌面端全局快捷键支持` |
| Task | `【Task】` | `【Task】重构 MediaPlayerService 生命周期管理` |

```bash
# 创建 bug
gh issue create --repo cy745/LMusic-KMP \
  --title "【Bug】<标题>" \
  --label "bug" \
  --body "<body>"

# 创建 feature
gh issue create --repo cy745/LMusic-KMP \
  --title "【Feature】<标题>" \
  --label "feature" \
  --body "<body>"

# 创建 task（带 assignee）
gh issue create --repo cy745/LMusic-KMP \
  --title "【Task】<标题>" \
  --label "task" \
  --assignee "@me" \
  --body "<body>"
  --label "enhancement" \
  --assignee "@me" \
  --body "<body>"

# 创建带 milestone 的 issue
gh issue create --repo cy745/LMusic-KMP \
  --title "<title>" \
  --label "bug" \
  --milestone "<milestone-title>" \
  --body "<body>"
```

**Labels 选择指南：**

| 场景 | Label |
|------|-------|
| 程序崩溃、异常行为、UI 错乱 | `bug` |
| 新功能、能力增强 | `feature` |
| 开发任务、重构、Chore、迁移 | `task` |
| 改进文档、添加注释 | `documentation` |
| 需求不明确的讨论 | `question` |
| 适合新手的任务 | `good first issue` + `help wanted` |
| 已经确认不影响发布的边缘问题 | `wontfix`（需先共识） |

> `enhancement` 是 GitHub 默认 label，与 `feature` 用途重叠。本项目中优先使用 `feature`，`enhancement` 作为备选保留。

##### Step 4: 确认创建结果

创建后立即验证：

```bash
gh issue view <number> --repo cy745/LMusic-KMP --json number,title,state,labels,url
```

向用户输出确认信息：

```
✅ Issue #{number} 已创建
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📌 标题: {title}
🏷️ Labels: {labels}
📎 链接: {url}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**同时记录到项目内存**（通过 Memory 功能）：

将创建的 issue 基本信息记录到项目 memory 中，方便后续追踪。格式：

```
## Issue #{number} — {title}
- 创建时间: {datetime}
- 标签: {labels}
- 状态: open
```

放置到 `issues-tracker` 相关的 memory 文件中（如 `issues-summary.md`）。

---

### 三、状态看板 — 快速查看项目全景

```bash
# 查看所有 open issues
gh issue list --repo cy745/LMusic-KMP --state open --limit 50

# 按 label 筛选
gh issue list --repo cy745/LMusic-KMP --label bug --state open --limit 20
gh issue list --repo cy745/LMusic-KMP --label enhancement --state open --limit 20

# 按 assignee 筛选
gh issue list --repo cy745/LMusic-KMP --assignee "@me" --state open --limit 20

# 按时间范围（最近 7 天创建的）
gh issue list --repo cy745/LMusic-KMP --search "created:>$(date -v-7d +%Y-%m-%d)" --limit 20

# 查看详细统计（用于看板输出）
gh issue list --repo cy745/LMusic-KMP --state all --json number,title,state,labels,assignees,createdAt,updatedAt
```

**看板输出格式（整合后）：**

```
📊 LMusic-KMP Issue 看板
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📋 统计概览
   Open: {N}  |  Closed: {N}  |  总数: {N}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🐛 Bug ({bug_count}):
   #{number}  {title}  [{label}]  {createdAt}
   #{number}  {title}  [{label}]  {createdAt}

✨ Enhancement ({enhancement_count}):
   ...

📌 待处理的 question ({question_count}):
   ...

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
💡 最近 7 天新增: {N}  |  无更新超过 14 天: {N}
```

---

### 四、进度报告 — 按维度汇总

```bash
# 按 label 汇总统计
gh issue list --repo cy745/LMusic-KMP --state all --json labels \
  | jq 'group_by(.labels[].name) | map({label: .[0].labels[].name, count: length})'

# 按 milestone 汇总
gh issue list --repo cy745/LMusic-KMP --state all --json milestone \
  | jq 'group_by(.milestone.title // "无里程碑") | map({milestone: .[0], count: length})'

# 查看 close 速率（最近 30 天关闭的 issues）
gh issue list --repo cy745/LMusic-KMP --state closed --search "closed:>$(date -v-30d +%Y-%m-%d)" --limit 50
```

**报告输出格式：**

```
📈 项目进度报告 — {date}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

🏷️ 按类型分布
   Bug: {N} open / {N} closed this month
   Enhancement: {N} open / {N} closed this month
   Docs: {N} open / {N} closed this month

📉 健康度指标
   总 Open Issues: {N}
   14 天无更新的 Open Issues: {N}  ← 需要关注
   无 label 的 Issues: {N}  ← 需补标签
   无 milestone 的 Issues: {N}

⏱️ 近期活动
   本周新增: {N}
   本周关闭: {N}
   待回应 (含 question label): {N}

💡 建议
   - 关注 14 天无更新问题: #{number}...
   - 待分配优先级: #{number}...
```

---

### 五、生命周期管理 — 更新/关闭/标记/关联

```bash
# 关闭 issue（附带关闭原因注释）
gh issue close <number> --repo cy745/LMusic-KMP --comment "关闭原因: {详细原因}"

# 重新打开
gh issue reopen <number> --repo cy745/LMusic-KMP

# 添加 label
gh issue edit <number> --repo cy745/LMusic-KMP --add-label "bug,help wanted"

# 移除 label
gh issue edit <number> --repo cy745/LMusic-KMP --remove-label "question"

# 分配 assignee
gh issue edit <number> --repo cy745/LMusic-KMP --add-assignee "username"

# 修改标题
gh issue edit <number> --repo cy745/LMusic-KMP --title "新标题"

# 添加评论
gh issue comment <number> --repo cy745/LMusic-KMP --body "评论内容"

# 标记为重复（先添加 duplicate label，再评论关联）
gh issue edit <number> --repo cy745/LMusic-KMP --add-label "duplicate"
gh issue comment <number> --repo cy745/LMusic-KMP --body "此 issue 与 #{original} 重复，将在 #{original} 中继续追踪。"

# 设置 milestone
gh issue edit <number> --repo cy745/LMusic-KMP --milestone "v1.0"
```

---

### 六、批量操作

```bash
# 批量关闭 issues（通过搜索筛选）
gh issue list --repo cy745/LMusic-KMP --label "wontfix" --state open --json number -q '.[].number' \
  | xargs -I{} gh issue close {} --repo cy745/LMusic-KMP --comment "标记为 wontfix，批量关闭"

# 批量添加 label
gh issue list --repo cy745/LMusic-KMP --label "" --state open --json number -q '.[].number' \
  | head -10 | xargs -I{} gh issue edit {} --repo cy745/LMusic-KMP --add-label "needs-triage"
```

---

## 项目配置建议

以下配置推荐为项目启用，以提升 issues-tracker skill 的效果。

### Labels 使用指南

当前项目已启用以下标签体系（均已创建）：

#### 平台标签（可多选，标识问题影响的平台）

| Label | 颜色 | 说明 |
|-------|------|------|
| `platform:android` | `#1d76db` | Android 特有 |
| `platform:ios` | `#1d76db` | iOS 特有 |
| `platform:jvm` | `#1d76db` | Desktop/JVM 特有 |
| `platform:web` | `#1d76db` | Web 特有 |

#### 优先级标签

| Label | 颜色 | 说明 |
|-------|------|------|
| `priority:high` | `#b60205` | 高优先级 |
| `priority:medium` | `#fbca04` | 中优先级 |
| `priority:low` | `#0e8a16` | 低优先级 |

#### 工作流标签（核心！）

| Label | 颜色 | 说明 | 使用场景 |
|-------|------|------|---------|
| `needs-triage` | `#e4e669` | 待分类 | 新创建的 issue，尚未分配优先级和确认类型 |
| `needs-info` | `#e4e669` | 信息不足 | **用户仅提供了模糊描述，需后续补充信息。此标签是渐进式收集的核心** |

> **`needs-info` 的工作流：**
> 1. 用户 A 报了一个问题，但信息不全 → 创建 issue + 加 `needs-info`
> 2. 用户 B 搜索时找到这个 issue，遇到同样问题 → 补充评论："我也遇到了，我的版本是 xxx"
> 3. 维护者看到信息够了 → 移 `needs-info`，开始排查
> 4. 如果排查中发现还缺信息 → 评论追问，重新加回 `needs-info`

#### 类型标签（项目自定义）

| Label | 颜色 | 说明 |
|-------|------|------|
| `bug` | `#d73a4a` 红 | 程序 Bug |
| `feature` | `#a2eeef` 绿 | 新功能请求（优先使用） |
| `task` | `#5319E7` 紫 | 开发任务、重构、Chore |

> 注意：GitHub 默认自带 `enhancement` label，与 `feature` 用途重叠。本项目中优先使用 `feature` 和 `task` 做区分，`enhancement` 作为备选保留。

#### GitHub 默认标签

| Label | 说明 |
|-------|------|
| `documentation` | 文档相关 |
| `question` | 需讨论的问题 |
| `duplicate` | 重复 issue |
| `good first issue` | 适合新手 |
| `help wanted` | 寻求协助 |
| `invalid` | 无效 |
| `wontfix` | 确认不修复 |

### Issue Templates

建议在 `.github/ISSUE_TEMPLATE/` 下创建本 skill 中定义的三个模板（Bug Report / Feature Request / Task），以匹配 issue 创建时的引导流程。模板文件可用本 skill 的模板内容生成。

---

## 场景决策速查

| 用户说 | 行为 |
|--------|------|
| "帮我找个 issue，xxx 问题" | 执行 **智能搜索** 流程 |
| "建一个 issue" | 执行 **智能创建** 流程（含查重） |
| "我遇到了 xxx 问题" | 先判断意图：如果像求助 → 智能搜索；如果像报 bug → 智能搜索 → 问是否要创建 |
| "关掉这个 issue" | 尝试 `gh issue close`（需指定 number） |
| "改一下这个 issue" | 尝试 `gh issue edit` |
| "看看项目进度" / "看板" | 输出 **状态看板** |
| "生成进度报告" | 输出 **进度报告** |
| "这个 issue 和 xxx 重复" | 先确认，然后添加 `duplicate` label + 关联评论 + close |
| "帮我记录一下" | 追问：是记录到 issue 还是记录到内存？如果 issue 则走创建流程 |
| "看看有哪些 bug" | `gh issue list --label bug --state open` |
| "帮我看看有没有 xxx 的 issue" | 执行 **智能搜索** |
| "更新一下 issue #{number}" | `gh issue view` 后问修改内容，然后 `gh issue edit` |
| "批量 xxx" | 确认范围后执行 **批量操作** |

---

## 与项目内存的集成

每次创建或关闭一个重要 issue 后，记录到项目 memory 中。使用统一的 memory 文件 `issues-summary.md`：

```markdown
---
name: issues-summary
description: LMusic-KMP 项目 issues 追踪状态摘要
metadata:
  type: project
---

# Issues 追踪摘要

最后更新: 2026-07-29

## Open Issues
- #{number} — {title} [{labels}] — {createdAt}

## 本周动态
- 新增: {N}
- 关闭: {N}
- 待处理: {N}
```

---

## 错误处理

| 错误场景 | 处理方式 |
|---------|---------|
| `gh` 未认证 | 提示运行 `gh auth login`，提供引导 |
| 网络错误 / API rate limit | 提示稍后重试，保存当前输入上下文到 memory |
| 无搜索结果 | 输出 "未找到匹配 issue，可以创建新 issue" |
| issue number 不存在 | 提示 number 可能已变更，建议先搜索 |
| 用户提供的信息不足以创建 | 仍然创建，标记 `needs-info`，列出缺失字段供后续补充 |

---

## 使用示例

### 示例 1: 用户报 bug，智能搜索找到匹配

```
用户: "点击设置页的关于按钮会闪退"

Agent: "我来查一下。这个现象是必现的吗？在哪个平台出现？有错误日志吗？"

用户: "Android 平台，偶尔出现。日志:
Fatal Exception: java.lang.NullPointerException
       at com.lalilu.setting.AboutActivity.onCreate(AboutActivity.kt:42)"

Agent: (执行智能搜索)

📊 搜索结果
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
#12 — 设置页「关于」入口点击崩溃  [open]  🔴 可信度 92%
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ 完全匹配: NullPointerException, AboutActivity, 设置页
✅ 环境匹配: Android
💬 已有 3 条评论，其中一条描述了 Linux 下的相同现象

📌 这是同一个问题，我已在 #12 下补充了你的案例。
```

### 示例 2: 用户要求建 issue，查重后补充到现有 issue

```
用户: "帮我建个 issue，iOS 上播放 AAC 格式音频会自动跳到下一首"

Agent: (先执行智能搜索)

📊 搜索结果 — 找到 2 个候选
#8 — iOS 播放 ALAC 格式音频异常  [open]  🟡 可信度 72%
#3 — 播放队列中特定格式音频会触发跳曲  [closed]  🔴 可信度 42%

#8 的差异项: AAC vs ALAC 编码不同，但平台(iOS)和症状(跳曲)一致

是否确认在 #8 下补充你的 AAC 案例？(该 issue 可能覆盖更广泛的编码支持问题)
```

### 示例 3: 项目看板

```
用户: "看一下项目进度"

Agent:
📊 LMusic-KMP Issue 看板
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📋 Open: 6  |  Closed: 3  |  总数: 9
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
🐛 Bug (3):
   #12  设置页「关于」点击崩溃  [bug, priority:high]
   #8   iOS 播放 ALAC 异常  [bug, platform:ios]
   #6   Web 端搜索框无法聚焦  [bug, platform:web]

✨ Enhancement (2):
   #10  添加歌词滚动同步显示  [enhancement]
   #4   桌面端全局快捷键  [enhancement]

❓ Question (1):
   #9  如何添加自定义播放列表封面  [question]

💡 最近 14 天无更新: #4, #6, #9
```
