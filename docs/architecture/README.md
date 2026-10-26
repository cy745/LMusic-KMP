# 播放与媒体架构评估

- `player-media-architecture-review.html`：离线阅读版，内嵌全部 SVG，提供决策摘要、UML 并排对照和完整报告；图可放大。
- `player-media-architecture-review.md`：报告原稿，包含源码证据与外部资料链接。
- `uml/`：12 张标准 UML 的 PlantUML 源文件。
- `diagrams/`：对应 SVG 渲染结果。
- `tools/render-report.mjs`：从 Markdown 与 SVG 生成 HTML，依赖 Node.js 与 marked。

本报告基于 `8423ba72` 和需求讨论，不代表建议已实施或全部已经用户确认。

重新生成 UML（本次使用 PlantUML 1.2025.2，自带 Smetana 布局，无需 Graphviz）：

```sh
java -Djava.awt.headless=true -jar /path/to/plantuml.jar -tsvg -charset UTF-8 -o ../diagrams docs/architecture/uml/*.puml
```

重新生成 HTML：

```sh
node docs/architecture/tools/render-report.mjs /path/to/marked/lib/marked.esm.js
```

若 Node 可直接解析 `marked`，可以省略最后一个参数。所有生成在本地进行，不向外部渲染服务上传源码。HTML 文件可直接在浏览器打开；相对源码和 UML 链接需要保留目录结构。
