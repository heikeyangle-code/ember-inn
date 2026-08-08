package com.emberinn.engine.media

/**
 * 媒体 token 成本估算（对齐官方 openai.js Message.getImageTokenCost / addVideo / addAudio）。
 *
 * 官方规则：
 * - 图片 low → 85；auto 且 <=512x512 → 85；否则先缩到 2048 方形内，再让短边=768，
 *   按 512 方格数计费（每格 170，底价 85）
 * - 视频 263 tokens/秒（向上取整），时长拿不到回退 263×40
 * - 音频 32 tokens/秒（向上取整），时长拿不到回退 32×300
 */
object MediaTokenCost {

    const val TOKENS_PER_IMAGE = 85

    fun imageTokens(width: Int, height: Int, quality: String): Int {
        if (quality == "low") return TOKENS_PER_IMAGE

        // 官方 getImageSizeFromDataURL 返回图片尺寸
        if (quality == "auto" && width <= 512 && height <= 512) return TOKENS_PER_IMAGE

        val scale = 2048.0 / minOf(width, height)
        val scaledWidth = Math.round(width * scale)
        val scaledHeight = Math.round(height * scale)

        val finalScale = 768.0 / minOf(scaledWidth, scaledHeight)
        val finalWidth = Math.round(scaledWidth * finalScale)
        val finalHeight = Math.round(scaledHeight * finalScale)

        val squares = Math.ceil(finalWidth / 512.0) * Math.ceil(finalHeight / 512.0)
        return squares.toInt() * 170 + TOKENS_PER_IMAGE
    }

    /** 官方 addVideo：263 tokens/秒，向上取整。 */
    fun videoTokens(durationSeconds: Double): Int = 263 * Math.ceil(durationSeconds).toInt()

    /** 官方 addVideo 时长拿不到时的保守估算（约 40 秒）。 */
    fun videoTokensFallback(): Int = 263 * 40

    /** 官方 addAudio：32 tokens/秒，向上取整。 */
    fun audioTokens(durationSeconds: Double): Int = 32 * Math.ceil(durationSeconds).toInt()

    /** 官方 addAudio 时长拿不到时的保守估算（约 5 分钟）。 */
    fun audioTokensFallback(): Int = 32 * 300
}
