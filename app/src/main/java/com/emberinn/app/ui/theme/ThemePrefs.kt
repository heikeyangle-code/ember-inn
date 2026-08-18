package com.emberinn.app.ui.theme

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** 主题偏好（模式 + 预设主题），SharedPreferences 持久化，后续可迁 DataStore。 */
object ThemePrefs {

    private const val NAME = "ember_theme"
    private const val KEY_MODE = "mode"
    private const val KEY_PRESET = "preset"

    fun mode(context: Context): ThemeMode = runCatching {
        val id = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODE, ThemeMode.SYSTEM.id) ?: ThemeMode.SYSTEM.id
        ThemeMode.entries.firstOrNull { it.id == id } ?: ThemeMode.SYSTEM
    }.getOrDefault(ThemeMode.SYSTEM)

    fun preset(context: Context): ThemePreset {
        val id = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_PRESET, ThemePresets.first().id) ?: ThemePresets.first().id
        return ThemePresets.byId(id)
    }

    fun save(context: Context, mode: ThemeMode, preset: ThemePreset) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_MODE, mode.id)
            .putString(KEY_PRESET, preset.id)
            .apply()
    }
}

/** 视觉氛围偏好：预设 id + 自定义三项参数，全部走同一份 ember_theme 首选项。 */
object VibePrefs {

    private const val NAME = "ember_theme"
    private const val KEY_VIBE = "vibe_id"
    private const val KEY_DESAT = "vibe_desat"
    private const val KEY_WARMTH = "vibe_warmth"
    private const val KEY_GLOW = "vibe_glow"

    private fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun resolve(context: Context): VibePreset {
        val id = prefs(context).getString(KEY_VIBE, "standard") ?: "standard"
        val base = VibePresets.vibeById(id)
        if (base.id != "custom") return base
        return VibePreset(
            id = "custom",
            name = "自定义",
            desc = "手动调节三项参数",
            desaturateLight = prefs(context).getFloat(KEY_DESAT, 0.15f),
            desaturateDark = prefs(context).getFloat(KEY_DESAT, 0.15f),
            warmth = prefs(context).getFloat(KEY_WARMTH, 0f),
            glow = prefs(context).getFloat(KEY_GLOW, 0.6f),
        )
    }

    fun save(context: Context, vibe: VibePreset) {
        val e = prefs(context).edit().putString(KEY_VIBE, vibe.id)
        if (vibe.id == "custom") {
            e.putFloat(KEY_DESAT, vibe.desaturateLight)
            e.putFloat(KEY_WARMTH, vibe.warmth)
            e.putFloat(KEY_GLOW, vibe.glow)
        }
        e.apply()
    }
}

/** 画布偏好：跟随主题预设（默认）或全局自定义配方（色域+渐变+纹理三层），同一份 ember_theme 首选项。
 *  完整 BackdropSpec 序列化为 JSON 存 SharedPreferences（色值走 ARGB Long 往返）。 */
object BackdropPrefs {

    private const val NAME = "ember_theme"
    private const val KEY_CUSTOM = "backdrop_custom"
    private const val KEY_SPEC = "backdrop_spec_json"
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** 是否启用全局自定义画布（false = 跟随主题预设）。 */
    fun custom(context: Context): Boolean = prefs(context).getBoolean(KEY_CUSTOM, false)

    /** 读自定义配方（未启用时返回值无意义，仅作编辑初值；解析失败回退默认）。 */
    fun spec(context: Context): BackdropSpec = try {
        val raw = prefs(context).getString(KEY_SPEC, null) ?: return BackdropSpec()
        parse(json.parseToJsonElement(raw).jsonObject)
    } catch (_: Exception) {
        BackdropSpec()
    }

    /** 生效画布：未启用自定义 = null（各处 resolveBackdrop 回退主题预设）。 */
    fun resolve(context: Context): BackdropSpec? = if (custom(context)) spec(context) else null

    fun saveCustom(context: Context, custom: Boolean, spec: BackdropSpec) {
        val payload = buildJsonObject {
            put("washes", kotlinx.serialization.json.JsonArray(spec.washes.map { w ->
                buildJsonObject {
                    put("c", w.color.toArgb().toLong())
                    put("x", w.x)
                    put("y", w.y)
                    put("r", w.radius)
                    put("a", w.alpha)
                }
            }))
            spec.gradient?.let { g ->
                put("grad", buildJsonObject {
                    put("c", kotlinx.serialization.json.JsonArray(g.colors.map { c -> kotlinx.serialization.json.JsonPrimitive(c.toArgb().toLong()) }))
                    put("ang", g.angle)
                    put("a", g.alpha)
                })
            }
            put("tex", buildJsonObject {
                put("weave", spec.texture.weave)
                put("stipple", spec.texture.stipple)
                put("hatch", spec.texture.hatch)
                put("cross", spec.texture.crossHatch)
                put("angle", spec.texture.hatchAngle)
                put("fiber", spec.texture.fiber)
                put("grain", spec.texture.grain)
                put("scale", spec.texture.scale)
                put("intensity", spec.texture.intensity)
            })
        }
        prefs(context).edit()
            .putBoolean(KEY_CUSTOM, custom)
            .putString(KEY_SPEC, payload.toString())
            .apply()
    }

    private fun parse(root: kotlinx.serialization.json.JsonObject): BackdropSpec {
        val washes = root["washes"]?.jsonArrayOrNull()?.mapNotNull { el ->
            val o = el.jsonObjectOrNull() ?: return@mapNotNull null
            ColorWash(
                color = Color(o["c"]?.jsonPrimitiveOrNull()?.longOrNull ?: 0L),
                x = o["x"]?.jsonPrimitiveOrNull()?.floatOrNull ?: 0.5f,
                y = o["y"]?.jsonPrimitiveOrNull()?.floatOrNull ?: 0.5f,
                radius = o["r"]?.jsonPrimitiveOrNull()?.floatOrNull ?: 0.55f,
                alpha = o["a"]?.jsonPrimitiveOrNull()?.floatOrNull ?: 0.2f,
            )
        } ?: emptyList()
        val gradient = root["grad"]?.jsonObjectOrNull()?.let { g ->
            CanvasGradient(
                colors = g["c"]?.jsonArrayOrNull()?.mapNotNull { c ->
                    c.jsonPrimitiveOrNull()?.longOrNull?.let { Color(it) }
                } ?: emptyList(),
                angle = g["ang"]?.jsonPrimitiveOrNull()?.floatOrNull ?: 0f,
                alpha = g["a"]?.jsonPrimitiveOrNull()?.floatOrNull ?: 0f,
            )
        }
        val t = root["tex"]?.jsonObjectOrNull()
        val texture = TextureSpec(
            weave = t?.get("weave")?.jsonPrimitiveOrNull()?.floatOrNull ?: 0f,
            stipple = t?.get("stipple")?.jsonPrimitiveOrNull()?.floatOrNull ?: 0f,
            hatch = t?.get("hatch")?.jsonPrimitiveOrNull()?.floatOrNull ?: 0f,
            crossHatch = t?.get("cross")?.jsonPrimitiveOrNull()?.floatOrNull ?: 0f,
            hatchAngle = t?.get("angle")?.jsonPrimitiveOrNull()?.floatOrNull ?: 45f,
            fiber = t?.get("fiber")?.jsonPrimitiveOrNull()?.floatOrNull ?: 0f,
            grain = t?.get("grain")?.jsonPrimitiveOrNull()?.floatOrNull ?: 0f,
            scale = t?.get("scale")?.jsonPrimitiveOrNull()?.floatOrNull ?: 1f,
            intensity = t?.get("intensity")?.jsonPrimitiveOrNull()?.floatOrNull ?: 1f,
        )
        return BackdropSpec(washes = washes, gradient = gradient, texture = texture)
    }

    private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull(): kotlinx.serialization.json.JsonObject? =
        try { jsonObject } catch (_: Exception) { null }

    private fun kotlinx.serialization.json.JsonElement.jsonArrayOrNull(): kotlinx.serialization.json.JsonArray? =
        try { jsonArray } catch (_: Exception) { null }

    private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveOrNull(): kotlinx.serialization.json.JsonPrimitive? =
        try { jsonPrimitive } catch (_: Exception) { null }
}
