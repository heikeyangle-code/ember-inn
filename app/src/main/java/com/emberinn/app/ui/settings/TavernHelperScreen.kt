package com.emberinn.app.ui.settings

import com.emberinn.app.ui.design.components.GroupLabel
import com.emberinn.app.ui.design.components.ShellChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.sp
import com.emberinn.app.ui.design.EmberTheme

/**
 * 酒馆助手（TH 兼容层）独立页——用户拍板：调整项不进现有外观/渲染页。
 *
 * 选项对照 js-slash-runner 设置面板（src/type/settings.ts 默认值）：
 *   脚本执行总开关 script.enabled.global=true / 渲染开关 render.enabled=true /
 *   渲染深度 depth=0(全部) / 深度忽略隐藏 depth_ignore_hidden=false /
 *   折叠代码块 collapse_code_block=frontend_only / 流式期间渲染 allow_streaming=false。
 */
@Composable
fun TavernHelperScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var cfg by remember { mutableStateOf(TavernHelperPrefs.read(context)) }

    fun commit() {
        cfg = TavernHelperPrefs.current
    }

    SettingsGlassPage { settingsSky ->
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(
                title = "酒馆助手",
                subtitle = "前端卡脚本沙箱 · TH API 兼容层",
                onBack = onBack,
                sky = settingsSky,
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                            Text("这是什么", color = EmberTheme.colors.accent, fontSize = EmberTheme.typo.bodySmall.fontSize)
                            Text(
                                "兼容酒馆助手(JS-Slash-Runner)生态：消息里的 js/ts 代码块在" +
                                    "同源沙箱 iframe 中运行，可直接调用 getVariables / triggerSlash / " +
                                    "getChatMessages / eventOn 等酒馆助手同名 API。",
                                color = EmberTheme.colors.inkMute,
                                fontSize = EmberTheme.typo.caption.fontSize,
                            )
                    }
                }
                item {
                    GroupLabel("脚本执行")
                    SwitchPrefRow(
                        title = "允许消息脚本运行",
                        subtitle = "script.enabled.global（关=所有代码块按普通代码显示）",
                        checked = cfg.scriptEnabled,
                    ) { enabled ->
                        TavernHelperPrefs.setScriptEnabled(context, enabled)
                        commit()
                    }
                    SwitchPrefRow(
                        title = "启用脚本块渲染",
                        subtitle = "render.enabled（关=不把 js 块转成 iframe）",
                        checked = cfg.renderEnabled,
                    ) { enabled ->
                        TavernHelperPrefs.setRenderEnabled(context, enabled)
                        commit()
                    }
                    SwitchPrefRow(
                        title = "流式期间也渲染",
                        subtitle = "allow_streaming（官方默认关；开=生成中每 tick 重放，耗电）",
                        checked = cfg.allowStreaming,
                    ) { enabled ->
                        TavernHelperPrefs.setAllowStreaming(context, enabled)
                        commit()
                    }
                }
                item {
                    GroupLabel("渲染范围")
                    SwitchPrefRow(
                        title = "深度计数忽略隐藏楼层",
                        subtitle = "depth_ignore_hidden（AI 不可见楼层不计入深度）",
                        checked = cfg.depthIgnoreHidden,
                    ) { enabled ->
                        TavernHelperPrefs.setDepthIgnoreHidden(context, enabled)
                        commit()
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 8.dp, bottom = 6.dp),
                    ) {
                        listOf(
                            "全部" to 0,
                            "最近 1 层" to 1,
                            "最近 2 层" to 2,
                            "最近 5 层" to 5,
                            "最近 10 层" to 10,
                        ).forEach { (label, value) ->
                            ShellChip(label, selected = cfg.depth == value) {
                                TavernHelperPrefs.setDepth(context, value)
                                commit()
                            }
                        }
                    }
                }
                item {
                    GroupLabel("代码块外观")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 4.dp, bottom = 6.dp),
                    ) {
                        listOf(
                            "不折叠" to TavernHelperPrefs.COLLAPSE_NONE,
                            "仅脚本折叠" to TavernHelperPrefs.COLLAPSE_FRONTEND_ONLY,
                            "全部折叠" to TavernHelperPrefs.COLLAPSE_ALL,
                        ).forEach { (label, value) ->
                            ShellChip(label, selected = cfg.collapseCodeBlock == value) {
                                TavernHelperPrefs.setCollapseCodeBlock(context, value)
                                commit()
                            }
                        }
                    }
                }
            }
        }
    }
}
