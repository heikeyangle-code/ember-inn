# RenderKernel 黄金测试

验证 `app/src/main/assets/kernel/js/render.js` 的格式化管线与官方 SillyTavern
messageFormatting 显示段逐字等价（release 8172dcd 基线）。

运行（需 Node ≥18）：
    cd scripts/kernel-golden && npm install && npm test

当前覆盖 25 例：markdown 基础 / 引号包裹(5种) / encode_tags 双态 / DOMPurify
消毒与 hooks(class改写/target/_blank/未知元素br) / style标签前缀化 / 表格 /
官方扩展(underscore/exclusion) / name2 / fixMarkdown / LaTeX align 链。

注意三条"官方怪癖"是有意保留的对齐行为：
1. showdown 2.1.0 表格分隔行需 ≥3 横线
2. name2 剥离不作用于首行 <p> 之后（^ 锚行为）
3. `$$` 经 showdown 输出链变为单个 $
后续 CI 应增加 Puppeteer 对比真实官方前端的 DOM 级断言。
