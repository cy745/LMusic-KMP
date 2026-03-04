# 贡献指南

感谢你对 LMusic 的关注！欢迎贡献代码。

## 开发环境

### 环境要求

- JDK 21+
- Gradle 8.11.1
- Android Studio 2024+ 或 IntelliJ IDEA 2024+
- macOS (iOS 开发)
- Xcode 15+ (iOS 开发)

### 快速开始

```bash
# 1. 克隆仓库
git clone https://github.com/<your-repo>/LMusic-KMP.git
cd LMusic-KMP

# 2. 构建项目
./gradlew build

# 3. 运行应用
# Android
./gradlew :composeApp:installDebug

# Desktop
./gradlew :composeApp:run
```

## 分支规范

| 类型 | 命名规范 | 示例 |
|------|----------|------|
| 功能分支 | `feature/<issue-id>-<描述>` | `feature/123-add-playlist` |
| 修复分支 | `bugfix/<issue-id>-<描述>` | `bugfix/456-fix-crash` |
| 热修复 | `hotfix/<issue-id>-<描述>` | `hotfix/789-urgent-fix` |

## 提交规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/)：

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Type 类型

| 类型 | 说明 |
|------|------|
| `feat` | 新功能 |
| `fix` | Bug 修复 |
| `docs` | 文档更改 |
| `style` | 代码格式（不影响功能）|
| `refactor` | 重构（既不是新功能也不是修复）|
| `test` | 测试相关 |
| `chore` | 构建过程或辅助工具变动 |

### 示例

```bash
# 新功能
git commit -m 'feat(player): add shuffle mode'

# 修复
git commit -m 'fix(lyric): fix timestamp parsing error'

# 文档
git commit -m 'docs: update README'
```

## PR 规范

1. 保持 PR 简洁，专注于单一功能或修复
2. 描述清楚 PR 的目的和改动内容
3. 确保所有测试通过：`./gradlew check`
4. 更新相关文档（如有必要）

## 代码规范

- 使用 Kotlin 官方代码风格
- 遵循项目现有的代码结构
- 添加必要的注释解释复杂逻辑

## 行为准则

- 尊重他人，保持友好
- 欢迎初学者，耐心解答问题
- 提交有意义的 PR，避免无意义的改动
