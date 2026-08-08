package com.emberinn.app.data

import com.emberinn.engine.provider.ConnectionProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 提供商“已配置”状态的进程内共享：设置页保存/切换/删除后刷新，
 * 聊天页直接订阅，不再每次发送都读 profiles.json。
 * （更彻底的方案是 Room/DataStore Flow，列入 P1 数据层迁移。）
 */
object ProviderState {

    private val _configured = MutableStateFlow(false)
    val configured: StateFlow<Boolean> = _configured

    fun refresh(profile: ConnectionProfile?) {
        _configured.value = profile != null
    }

    fun isConfigured(): Boolean = _configured.value
}
