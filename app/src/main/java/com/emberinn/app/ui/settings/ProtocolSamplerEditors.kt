package com.emberinn.app.ui.settings

import com.emberinn.app.ui.design.EmberTheme
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.emberinn.app.ui.design.components.EmberSwitch
import com.emberinn.app.ui.components.EmberTextField
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 协议专属采样参数编辑器（对照官方 textgen/novel/kobold 设置面板）：
 * 读写对应 SettingsStore，发请求时由 ChatRepository 合并。纯 App/UI 层，不碰引擎。
 */
object ProtocolSamplerEditors {

    private val json = Json { ignoreUnknownKeys = true }

    @Composable
    fun TextGenEditor(context: Context) {
        val store = TextgenSettingsStore.load(context)
        GenericEditor(
            context = context,
            title = "Text Completion 采样参数（官方 textgen-settings）",
            initial = store,
            fields = listOf(
                Num("temp", "温度", 0.0, 5.0, 0.1, true),
                Num("top_p", "top_p", 0.0, 1.0, 0.01, true),
                Num("top_k", "top_k", 0.0, 200.0, 1.0, false),
                Num("top_a", "top_a", 0.0, 1.0, 0.01, true),
                Num("min_p", "min_p", 0.0, 1.0, 0.01, true),
                Num("typical_p", "typical_p", 0.0, 5.0, 0.01, true),
                Num("rep_pen", "rep_pen", 0.0, 5.0, 0.01, true),
                Num("rep_pen_range", "rep_pen_range", 0.0, 4096.0, 1.0, false),
                Num("rep_pen_slope", "rep_pen_slope", 0.0, 5.0, 0.01, true),
                Num("tfs", "tfs", 0.0, 5.0, 0.01, true),
                Num("seed", "seed", -1.0, Int.MAX_VALUE.toDouble(), 1.0, false),
                Num("mirostat_mode", "mirostat_mode", 0.0, 2.0, 1.0, false),
                Num("mirostat_tau", "mirostat_tau", 0.0, 10.0, 0.1, true),
                Num("mirostat_eta", "mirostat_eta", 0.0, 1.0, 0.01, true),
                Num("min_temp", "dynatemp min_temp", 0.0, 5.0, 0.01, true),
                Num("max_temp", "dynatemp max_temp", 0.0, 5.0, 0.01, true),
                Num("dynatemp_exponent", "dynatemp_exponent", 0.0, 5.0, 0.01, true),
                Str("grammar_string", "grammar_string"),
            ),
            save = { TextgenSettingsStore.save(context, it) },
        )
    }

    @Composable
    fun NovelEditor(context: Context) {
        GenericEditor(
            context = context,
            title = "NovelAI 采样参数（官方 nai_settings）",
            initial = NovelSettingsStore.load(context),
            fields = listOf(
                Num("temperature", "temperature", 0.0, 5.0, 0.01, true),
                Num("top_p", "top_p", 0.0, 1.0, 0.01, true),
                Num("top_k", "top_k", 0.0, 200.0, 1.0, false),
                Num("top_a", "top_a", 0.0, 1.0, 0.01, true),
                Num("min_p", "min_p", 0.0, 1.0, 0.01, true),
                Num("typical_p", "typical_p", 0.0, 5.0, 0.01, true),
                Num("tail_free_sampling", "tail_free_sampling", 0.0, 2.0, 0.01, true),
                Num("repetition_penalty", "repetition_penalty", 0.0, 5.0, 0.01, true),
                Num("repetition_penalty_range", "repetition_penalty_range", 0.0, 8192.0, 1.0, false),
                Num("repetition_penalty_slope", "repetition_penalty_slope", 0.0, 5.0, 0.01, true),
                Num("repetition_penalty_frequency", "repetition_penalty_frequency", 0.0, 2.0, 0.01, true),
                Num("repetition_penalty_presence", "repetition_penalty_presence", 0.0, 2.0, 0.01, true),
                Num("min_length", "min_length", 0.0, 1024.0, 1.0, false),
                Num("mirostat_lr", "mirostat_lr", 0.0, 1.0, 0.01, true),
                Num("mirostat_tau", "mirostat_tau", 0.0, 10.0, 0.1, true),
                Str("phrase_rep_pen", "phrase_rep_pen"),
                Str("prefix", "prefix"),
            ),
            save = { NovelSettingsStore.save(context, it) },
        )
    }

    @Composable
    fun KoboldEditor(context: Context) {
        GenericEditor(
            context = context,
            title = "Kobold 采样参数（官方 kai_settings）",
            initial = KoboldSettingsStore.load(context),
            fields = listOf(
                Num("temp", "temp", 0.0, 5.0, 0.01, true),
                Num("top_p", "top_p", 0.0, 1.0, 0.01, true),
                Num("top_k", "top_k", 0.0, 200.0, 1.0, false),
                Num("top_a", "top_a", 0.0, 1.0, 0.01, true),
                Num("min_p", "min_p", 0.0, 1.0, 0.01, true),
                Num("typical", "typical", 0.0, 5.0, 0.01, true),
                Num("tfs", "tfs", 0.0, 5.0, 0.01, true),
                Num("rep_pen", "rep_pen", 0.0, 5.0, 0.01, true),
                Num("rep_pen_range", "rep_pen_range", 0.0, 8192.0, 1.0, false),
                Num("rep_pen_slope", "rep_pen_slope", 0.0, 5.0, 0.01, true),
                Num("mirostat", "mirostat", 0.0, 2.0, 1.0, false),
                Num("mirostat_tau", "mirostat_tau", 0.0, 10.0, 0.1, true),
                Num("mirostat_eta", "mirostat_eta", 0.0, 1.0, 0.01, true),
                Num("seed", "seed", -1.0, Int.MAX_VALUE.toDouble(), 1.0, false),
                Str("grammar", "grammar"),
            ),
            save = { KoboldSettingsStore.save(context, it) },
        )
    }

    private sealed class Field {
        abstract val key: String
        abstract val label: String
    }

    private data class Num(
        override val key: String,
        override val label: String,
        val min: Double,
        val max: Double,
        val step: Double,
        val decimal: Boolean,
    ) : Field()

    private data class Str(override val key: String, override val label: String) : Field()

    @Composable
    private fun GenericEditor(
        context: Context,
        title: String,
        initial: JsonObject,
        fields: List<Field>,
        save: (JsonObject) -> Unit,
    ) {
        var map by remember { mutableStateOf(initial.toMutableMap()) }
        var dirty by remember { mutableStateOf(false) }
        Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            color = EmberTheme.colors.surface,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                color = EmberTheme.colors.ink, fontSize = 15.sp,
                fields.forEach { f ->
                    when (f) {
                        is Num -> {
                            val current = (map[f.key] as? JsonPrimitive)?.contentOrNull ?: ""
                            EmberTextField(
                                value = current,
                                onValueChange = { v ->
                                    map[f.key] = JsonPrimitive(v)
                                    dirty = true
                                },
                                label = { Text(f.label) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            )
                        }
                        is Str -> {
                            val current = (map[f.key] as? JsonPrimitive)?.contentOrNull ?: ""
                            EmberTextField(
                                value = current,
                                onValueChange = { v ->
                                    map[f.key] = JsonPrimitive(v)
                                    dirty = true
                                },
                                label = { Text(f.label) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(enabled = dirty, onClick = {
                        save(JsonObject(map))
                        dirty = false
                    }) { Text("保存协议采样参数") }
                }
            }
        }
    }
}
