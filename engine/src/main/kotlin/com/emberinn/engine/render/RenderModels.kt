package com.emberinn.engine.render

/**
 * 路线 A（静态 HTML→原生 UI）引擎层数据模型。
 * 纯 JVM、无 UI 依赖；应用层只负责把 [RenderNode] 树映射成原生组件。
 */

/** 长度值。Px 用 dp 语义（引擎输出纯数值，应用层按 dp 使用）。 */
sealed interface CssLength {
    data class Px(val value: Float) : CssLength
    data class Percent(val value: Float) : CssLength
    data object Auto : CssLength
}

/** RGBA 颜色。channel 0f..1f。 */
data class RgbaColor(val r: Float, val g: Float, val b: Float, val a: Float = 1f)

/** 盒模型四个方向（单位长度）。 */
data class CssBox(val top: CssLength?, val right: CssLength?, val bottom: CssLength?, val left: CssLength?) {
    companion object {
        val None = CssBox(null, null, null, null)
    }
}

/** 边框。 */
data class CssBorder(
    val width: CssLength?,
    val style: String?,   // none/solid/dashed/dotted/double
    val color: RgbaColor?,
)

/** 解析并消毒后的样式。所有字段可选，null = 未指定（用应用层默认）。 */
data class ResolvedStyle(
    val display: String? = null,          // block / inline / inline-block / flex / none
    val color: RgbaColor? = null,
    val backgroundColor: RgbaColor? = null,
    val fontSizePx: Float? = null,        // 引擎按 px 计算，应用层转 sp/dp
    val fontWeight: Int? = null,          // 100..900
    val fontStyle: String? = null,        // italic / normal / oblique
    val textDecoration: String? = null,   // none / underline / line-through / underline line-through
    val textAlign: String? = null,        // left / center / right / justify
    val lineHeight: Float? = null,        // 相对字体倍率（normal=1.2 由应用层定）
    val letterSpacingPx: Float? = null,
    val margin: CssBox = CssBox.None,
    val padding: CssBox = CssBox.None,
    val border: CssBorder = CssBorder(null, null, null),
    val borderRadius: CssLength? = null,
    val width: CssLength? = null,
    val height: CssLength? = null,
    val minHeight: CssLength? = null,
    val opacity: Float? = null,           // 0..1
    val whiteSpace: String? = null,       // normal / pre / pre-wrap / nowrap
    val overflow: String? = null,         // visible / hidden / auto / scroll
    val verticalAlign: String? = null,    // baseline / top / middle / bottom
    val flexDirection: String? = null,    // row / column
    val justifyContent: String? = null,   // flex-start / center / flex-end / space-between / space-around
    val alignItems: String? = null,       // flex-start / center / flex-end / stretch
    val gapPx: Float? = null,
    val backgroundImage: String? = null,  // 仅本 origin/data URI（外部已按规则过滤）
    val fontFamily: List<String>? = null, // 首名字（不含引号）
) {
    val isBlock: Boolean get() =
        display in setOf(
            "block", "flex", "inline-block", "grid", "list-item",
            "table", "table-row", "table-cell", "table-caption",
        )
}

/** 样式块规则（官方消毒后的 scoped CSS）。 */
data class ScopedCssRule(
    val selector: String,              // 官方前缀化后的选择器（含 .mes_text custom- 语义）
    val declarations: Map<String, String>, // property -> sanitized value
    val specificity: Int,              // 简化 specificity（class/type 计数），越大越优先
)

/** 消毒结果。 */
data class SanitizeResult(
    val root: SanitizedNode,           // 可见 DOM 树（不含 style/script 内容）
    val styleRules: List<ScopedCssRule>,
)

/** 消毒后的中间节点（引擎内部，应用层不直接使用）。 */
sealed interface SanitizedNode {
    data class Text(val text: String) : SanitizedNode
    data class Tag(
        val name: String,              // 小写
        val attrs: Map<String, String>,
        val children: List<SanitizedNode>,
    ) : SanitizedNode
}

/** 渲染树（应用层映射为原生组件）。 */
sealed interface RenderNode {
    data class Text(val text: String) : RenderNode
    data class Element(
        val tag: String,               // 小写
        val attrs: Map<String, String>,// 消毒后属性
        val classes: Set<String>,      // 消毒后（custom- 前缀）
        val style: ResolvedStyle,      // 合并后样式
        val children: List<RenderNode>,
        /** 无脚本交互语义：details/summary 折叠等。 */
        val interactive: InteractiveKind = InteractiveKind.None,
    ) : RenderNode {
        val href: String? get() = attrs["href"]
        val src: String? get() = attrs["src"]
        val alt: String? get() = attrs["alt"]
        val isOpen: Boolean get() = attrs["open"] != null
        val isChecked: Boolean get() = attrs["checked"] != null
    }
}

/** 原生交互语义（路线 A，无需 JS）。 */
enum class InteractiveKind {
    None,
    Details,      // <details>
    Summary,      // <summary>
    Link,         // <a href>
    Image,        // <img>
    Video,        // <video>
    Audio,        // <audio>
    InputCheckbox,// <input type=checkbox>
}
