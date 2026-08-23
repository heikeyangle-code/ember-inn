package com.emberinn.app.ui.design

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 皮肤图像资产层（docs/DESIGN_SYSTEM.md §五 ThemeSkin assets/）：
 *  - background.(png|jpg|webp) × light/dark   应用背景图
 *  - card_frame(.9).png × light/dark          九宫格卡片框
 *  - splash.png                               冷启动图
 *
 * 约定路径即 schema：assets/skins/<皮肤id>/background-dark.png 等；
 * 内置 6 套纯色皮肤无资产文件 → 全 null，渲染行为与纯色令牌完全一致。
 * 探测结果按 (id,dark) 进程内缓存；位图按文件路径缓存（切皮肤不重复解码）。
 */
data class SkinImageAssets(
    /** 当前深浅模式下的应用背景图（assets 路径），null = 无 */
    val background: String? = null,
    val cardFrame: String? = null,
    val splash: String? = null,
) {
    companion object {
        val EMPTY = SkinImageAssets()
    }
}

object SkinAssetResolver {

    private val IMAGE_EXTS = listOf("webp", "png", "jpg")
    private val assetsCache = ConcurrentHashMap<String, SkinImageAssets>()
    private val bitmapCache = ConcurrentHashMap<String, ImageBitmap?>()

    /** 探测皮肤包图像资产（只查存在性，不解码位图）。 */
    fun resolve(context: Context, skinId: String, dark: Boolean): SkinImageAssets =
        assetsCache.getOrPut("$skinId/$dark") {
            val mode = if (dark) "dark" else "light"
            SkinImageAssets(
                background = firstExisting(context, "skins/$skinId/background-$mode"),
                cardFrame = firstExisting(context, "skins/$skinId/card_frame-$mode"),
                splash = firstExisting(context, "skins/$skinId/splash"),
            )
        }

    /** 资产路径→位图（失败/缺文件回 null；调用方须有纯色兜底）。 */
    fun decode(context: Context, assetPath: String): ImageBitmap? =
        bitmapCache.getOrPut(assetPath) {
            runCatching {
                context.assets.open(assetPath).use { input ->
                    BitmapFactory.decodeStream(input)?.asImageBitmap()
                }
            }.getOrNull()
        }

    private fun firstExisting(context: Context, baseNoExt: String): String? {
        for (ext in IMAGE_EXTS) {
            val p = "$baseNoExt.$ext"
            if (bitmapCache.containsKey(p)) return p // 已缓存说明探测过
            if (runCatching { context.assets.open(p).use { true } }.getOrDefault(false)) return p
        }
        return null
    }
}

/** 当前皮肤的图像资产（随 EmberTheme 下发；默认空 = 纯色令牌渲染）。 */
val LocalEmberImageAssets = androidx.compose.runtime.staticCompositionLocalOf { SkinImageAssets.EMPTY }

/** 皮肤背景图层：无资产时返回 null 由调用方走纯色；有则铺满裁剪（§五“透明度+适应方式”的 v1：全量 Crop）。 */
@Composable
fun rememberSkinBackground(): Pair<String, ImageBitmap>? {
    val context = LocalContext.current
    val path = LocalEmberImageAssets.current.background ?: return null
    val bitmap = remember(path) { SkinAssetResolver.decode(context, path) } ?: return null
    return path to bitmap
}

/** 铺满裁剪的皮肤背景图（放根 Box 最底层、渐变之下）。 */
@Composable
fun SkinBackgroundLayer(modifier: Modifier = Modifier, alpha: Float = 1f) {
    val bg = rememberSkinBackground() ?: return
    Image(
        bitmap = bg.second,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alpha = alpha,
        modifier = modifier.fillMaxSize(),
    )
}
