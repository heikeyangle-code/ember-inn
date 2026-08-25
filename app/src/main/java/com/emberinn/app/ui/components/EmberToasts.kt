package com.emberinn.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emberinn.app.data.OfficialThemeManager
import com.emberinn.app.ui.design.EmberTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import android.content.Context

/**
 * 官方 toastr 对应物（应用内浮层）：六位置由主题 toastr_position 驱动，
 * 不经系统 Toast——Android 11+ 忽略 setGravity 的平台限制就此消除，全版本对齐。
 * 时序对齐 script.js L347-351：timeOut 恒定 4000ms，fadeIn/fadeOut 250ms。
 * 类型色取官方 toastr.css 默认板（success/error/info/warning 左缘条）。
 */
object EmberToasts {
    data class Msg(val id: Long, val text: String, val type: String)

    private val _messages = MutableStateFlow<List<Msg>>(emptyList())
    val messages: StateFlow<List<Msg>> = _messages
    private var seq = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** 兼容旧签名；官方 toastr 全类型统一 4000ms（script.js L350），long 参数不再区分时长 */
    fun show(context: Context, message: String, long: Boolean = false) {
        show(context, message, "info")
    }

    /** type: info/success/error/warning（官方 toastr 四类型） */
    fun show(context: Context, message: String, type: String) {
        val msg = Msg(++seq, message, type)
        _messages.value = _messages.value + msg
        scope.launch {
            delay(4000)
            _messages.value = _messages.value.filterNot { it.id == msg.id }
        }
    }

    /** 官方 toastr.css 类型色板 */
    val TYPE_COLORS = mapOf(
        "success" to Color(0xFF51A351),
        "error" to Color(0xFFBD362F),
        "warning" to Color(0xFFF89406),
        "info" to Color(0xFF2F96B4),
    )
}

/** 根级挂载的 toastr 浮层：位置随主题 toastr_position 六枚举实时切换。 */
@Composable
fun EmberToastHost(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { OfficialThemeManager.shared(context) }
    val themeName by manager.currentThemeJson.collectAsState()
    // 官方 toastr.options.positionClass = power_user.toastr_position（power-user.js L1078）
    val position = remember(themeName) { manager.shellSettings().toastrPosition }
    val alignment = when (position) {
        "toast-top-left" -> Alignment.TopStart
        "toast-top-right" -> Alignment.TopEnd
        "toast-bottom-left" -> Alignment.BottomStart
        "toast-bottom-right" -> Alignment.BottomEnd
        "toast-bottom-center" -> Alignment.BottomCenter
        else -> Alignment.TopCenter // 官方缺省 toast-top-center（script.js L348）
    }
    val messages by EmberToasts.messages.collectAsState()

    Box(modifier = modifier.fillMaxSize(), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 48.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = when (alignment) {
                Alignment.TopStart, Alignment.BottomStart -> Alignment.Start
                Alignment.TopEnd, Alignment.BottomEnd -> Alignment.End
                else -> Alignment.CenterHorizontally
            },
        ) {
            messages.forEach { msg ->
                ToastItem(msg)
            }
        }
    }
}

@Composable
private fun ToastItem(msg: EmberToasts.Msg) {
    // 官方时序：进场即 fade 250ms；t=3750 起退场 250ms，t=4000 由对象侧移除（无缝衔接）
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(msg.id) {
        shown = true
        delay(3750)
        shown = false
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(animationSpec = tween(250)), // 官方 showDuration 250
        exit = fadeOut(animationSpec = tween(250)), // 官方 hideDuration 250
    ) {
        ToastCard(msg)
    }
}

@Composable
private fun ToastCard(msg: EmberToasts.Msg) {
    val accent = EmberToasts.TYPE_COLORS[msg.type] ?: EmberToasts.TYPE_COLORS.getValue("info")
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color(0xCC000000.toInt()), // toastr 默认 rgba(0,0,0,.8)
        shadowElevation = 6.dp,
        modifier = Modifier
            .widthIn(max = 420.dp)
            .border(2.dp, accent, RoundedCornerShape(6.dp)),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(
                text = msg.text,
                color = Color.White,
                fontSize = EmberTheme.typo.body.fontSize,
                lineHeight = 19.sp,
            )
        }
    }
}
