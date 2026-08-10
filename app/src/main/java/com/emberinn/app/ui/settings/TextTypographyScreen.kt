package com.emberinn.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.components.EmberSwitch

/**
 * 文字排版设置：把 mikepenz markdown 渲染器支持的排版维度全部暴露（正文/标题/引用/代码/间距）。
 * 只影响聊天消息显示；保存即持久化，回聊天页立即生效。
 */
@Composable
fun TextTypographyScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    var textSize by remember { mutableStateOf(AppearancePrefs.textSize(context)) }
    var lineHeight by remember { mutableStateOf(AppearancePrefs.lineHeight(context)) }
    var bodyWeight by remember { mutableStateOf(AppearancePrefs.bodyWeight(context)) }
    var headingStyle by remember { mutableStateOf(AppearancePrefs.headingStyle(context)) }
    var h1 by remember { mutableStateOf(AppearancePrefs.headingH1(context)) }
    var h2 by remember { mutableStateOf(AppearancePrefs.headingH2(context)) }
    var quoteItalic by remember { mutableStateOf(AppearancePrefs.quoteItalic(context)) }
    var codeSize by remember { mutableStateOf(AppearancePrefs.codeSize(context)) }
    var inlineCodeSize by remember { mutableStateOf(AppearancePrefs.inlineCodeSize(context)) }
    var blockSpacing by remember { mutableStateOf(AppearancePrefs.blockSpacing(context)) }
    var listIndent by remember { mutableStateOf(AppearancePrefs.listIndent(context)) }

    Column(modifier = Modifier.fillMaxSize()) {
        SettingsTopBar(title = "文字排版", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                TypeCard("正文") {
                    TypeChips(
                        "字号",
                        listOf("small" to "小 14", "normal" to "标准 16", "large" to "大 18", "xlarge" to "特大 20"),
                        textSize,
                    ) { textSize = it; AppearancePrefs.saveTextSize(context, it) }
                    TypeChips(
                        "行高",
                        listOf("compact" to "紧凑 1.4", "normal" to "标准 1.55", "loose" to "宽松 1.7"),
                        lineHeight,
                    ) { lineHeight = it; AppearancePrefs.saveLineHeight(context, it) }
                    TypeChips(
                        "字重",
                        listOf("normal" to "常规", "medium" to "中等", "semibold" to "半粗"),
                        bodyWeight,
                    ) { bodyWeight = it; AppearancePrefs.saveBodyWeight(context, it) }
                }
            }
            item {
                TypeCard("标题") {
                    TypeChips(
                        "层级",
                        listOf("flat" to "聊天风（缩小）", "real" to "正常层级（放大）"),
                        headingStyle,
                    ) { headingStyle = it; AppearancePrefs.saveHeadingStyle(context, it) }
                    TypeChips(
                        "H1 大小",
                        listOf("0.9" to "小", "1.0" to "标准", "1.2" to "大", "1.5" to "特大"),
                        h1.toString(),
                    ) { h1 = it.toFloat(); AppearancePrefs.saveHeadingH1(context, it.toFloat()) }
                    TypeChips(
                        "H2 大小",
                        listOf("0.9" to "小", "1.0" to "标准", "1.15" to "大", "1.35" to "特大"),
                        h2.toString(),
                    ) { h2 = it.toFloat(); AppearancePrefs.saveHeadingH2(context, it.toFloat()) }
                }
            }
            item {
                TypeCard("引用与代码") {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("引用斜体", style = MaterialTheme.typography.bodyMedium)
                        EmberSwitch(
                            checked = quoteItalic,
                            onCheckedChange = { quoteItalic = it; AppearancePrefs.saveQuoteItalic(context, it) },
                        )
                    }
                    TypeChips(
                        "代码块字号",
                        listOf("0.8" to "小", "0.9" to "标准", "1.0" to "大"),
                        codeSize.toString(),
                    ) { codeSize = it.toFloat(); AppearancePrefs.saveCodeSize(context, it.toFloat()) }
                    TypeChips(
                        "行内代码字号",
                        listOf("0.8" to "小", "0.9" to "标准", "1.0" to "大"),
                        inlineCodeSize.toString(),
                    ) { inlineCodeSize = it.toFloat(); AppearancePrefs.saveInlineCodeSize(context, it.toFloat()) }
                }
            }
            item {
                TypeCard("间距") {
                    TypeChips(
                        "块间距",
                        listOf("compact" to "紧凑", "normal" to "标准", "loose" to "宽松"),
                        blockSpacing,
                    ) { blockSpacing = it; AppearancePrefs.saveBlockSpacing(context, it) }
                    TypeChips(
                        "列表缩进",
                        listOf("8" to "8dp", "10" to "10dp", "12" to "12dp"),
                        listIndent,
                    ) { listIndent = it; AppearancePrefs.saveListIndent(context, it) }
                }
            }
        }
    }
}

@Composable
private fun TypeCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun TypeChips(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (v, text) ->
            FilterChip(
                selected = selected == v,
                onClick = { onSelect(v) },
                label = { Text(text) },
            )
        }
    }
}
