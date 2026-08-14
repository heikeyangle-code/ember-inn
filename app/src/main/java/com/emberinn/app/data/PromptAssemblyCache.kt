package com.emberinn.app.data

import com.emberinn.engine.prompt.CompletionMessage

/**
 * 最近一次总装（发送或 dryRun 预览）的消息集合，供 Prompt Manager 检查弹窗按 identifier 查看。
 * 对齐官方 PromptManager.messages：只在总装后填充，未总装过则为 null（检查面板无内容）。
 */
object PromptAssemblyCache {
    @Volatile
    var lastMessages: List<CompletionMessage>? = null
}
