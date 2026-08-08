package com.emberinn.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emberinn.app.data.ChatRepository
import com.emberinn.app.data.ProviderState
import com.emberinn.engine.prompt.CompletionMessage
import com.emberinn.engine.provider.ConnectionProfile
import com.emberinn.engine.provider.SamplerParams
import com.emberinn.engine.provider.LlmClient
import com.emberinn.engine.provider.ProviderRegistry
import com.emberinn.engine.provider.ProviderSpec
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 提供商管理（参照命理2：列表 + 详情编辑；底层协议仍按酒馆 1:1）。 */
class ProviderViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ChatRepository(application)
    private val client = LlmClient()

    val providers: List<ProviderSpec> = ProviderRegistry.all()

    private val _profiles = MutableStateFlow(repo.profiles())
    val profiles: StateFlow<List<ConnectionProfile>> = _profiles

    private val _activeId = MutableStateFlow(repo.activeProfile()?.id ?: "")
    val activeId: StateFlow<String> = _activeId

    private val _providerId = MutableStateFlow("")
    val providerId: StateFlow<String> = _providerId

    private val _profileName = MutableStateFlow("")
    val profileName: StateFlow<String> = _profileName

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey

    private val _baseUrl = MutableStateFlow("")
    val baseUrl: StateFlow<String> = _baseUrl

    private val _region = MutableStateFlow("")
    val region: StateFlow<String> = _region

    private val _accountId = MutableStateFlow("")
    val accountId: StateFlow<String> = _accountId

    private val _apiVersion = MutableStateFlow("")
    val apiVersion: StateFlow<String> = _apiVersion

    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models

    private val _selectedModel = MutableStateFlow("")
    val selectedModel: StateFlow<String> = _selectedModel

    private val _contextWindow = MutableStateFlow(8192)
    val contextWindow: StateFlow<Int> = _contextWindow

    private val _maxTokens = MutableStateFlow(512)
    val maxTokens: StateFlow<Int> = _maxTokens

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private var editingId: String? = null
    private var editingSampler: SamplerParams = SamplerParams()

    fun provider(): ProviderSpec? = ProviderRegistry.get(_providerId.value)

    fun openDetail(id: String) {
        val spec = ProviderRegistry.get(id) ?: return
        val existing = _profiles.value.firstOrNull { it.providerId == id }
        editingId = existing?.id
        editingSampler = existing?.sampler ?: SamplerParams()
        _providerId.value = id
        _profileName.value = existing?.name?.ifBlank { spec.displayName } ?: spec.displayName
        _apiKey.value = existing?.apiKey.orEmpty()
        _baseUrl.value = existing?.baseUrlOverride?.ifBlank { spec.baseUrl }.orEmpty()
        _region.value = existing?.region.orEmpty()
        _accountId.value = existing?.accountId.orEmpty()
        _apiVersion.value = existing?.apiVersionOverride.orEmpty()
        _contextWindow.value = existing?.contextWindow ?: 8192
        _maxTokens.value = existing?.sampler?.maxTokens ?: spec.defaultMaxTokens ?: 512
        val list = spec.defaultModels.toMutableList()
        existing?.model?.takeIf { it.isNotBlank() && it !in list }?.let { list.add(0, it) }
        _models.value = list
        _selectedModel.value = existing?.model?.takeIf { it.isNotBlank() }
            ?: spec.defaultModels.firstOrNull().orEmpty()
        _message.value = null
        _testing.value = false
    }

    fun setProfileName(value: String) {
        _profileName.value = value
    }

    /** 粘贴自动去空格。 */
    fun setApiKey(value: String) {
        _apiKey.value = value.filterNot { it.isWhitespace() }
    }

    fun setBaseUrl(value: String) {
        _baseUrl.value = value.trim()
    }

    fun setRegion(value: String) {
        _region.value = value
    }

    fun setAccountId(value: String) {
        _accountId.value = value
    }

    fun setApiVersion(value: String) {
        _apiVersion.value = value.trim()
    }

    /** 上下文上限（tokens），占比胶囊分母；0/非法回退 8192。 */
    fun setContextWindow(value: String) {
        val n = value.filter { it.isDigit() }.toIntOrNull()
        _contextWindow.value = (n ?: 8192).coerceIn(256, 2_000_000)
    }

    /** 最大回复 tokens：推理模型思考会占额度，512 太小正文常被掐空。 */
    fun setMaxTokens(value: String) {
        val n = value.filter { it.isDigit() }.toIntOrNull()
        _maxTokens.value = (n ?: 512).coerceIn(64, 262_144)
    }

    fun selectModel(model: String) {
        _selectedModel.value = model
    }

    fun testConnection() {
        val spec = provider() ?: return
        _testing.value = true
        _message.value = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (hasModelsEndpoint(spec)) {
                        client.models(spec, buildProfile())
                    } else {
                        // 无模型列表端点的提供商：最小对话探测验证 Key
                        client.chatCompletions(
                            spec,
                            buildProfile().copy(sampler = buildProfile().sampler.copy(maxTokens = 1, stream = false)),
                            listOf(CompletionMessage(role = "user", content = "ping")),
                        )
                        spec.defaultModels
                    }
                }
            }
            _testing.value = false
            result.onSuccess { models ->
                val list = models.ifEmpty { spec.defaultModels }
                _models.value = list
                if (_selectedModel.value.isBlank() || _selectedModel.value !in list) {
                    _selectedModel.value = list.firstOrNull() ?: ""
                }
                _message.value = if (models.isNotEmpty()) "连接成功，共 ${models.size} 个模型" else "连接成功"
            }.onFailure { e ->
                _message.value = humanError(e)
            }
        }
    }

    fun save() {
        val spec = provider() ?: return
        if (_selectedModel.value.isBlank() && spec.defaultModels.isNotEmpty()) {
            _selectedModel.value = spec.defaultModels.first()
        }
        val profile = buildProfile()
        repo.saveProfile(profile, active = true)
        refreshProfiles()
        ProviderState.refresh(profile)
        _message.value = "已保存"
    }

    fun switchActive(id: String) {
        repo.setActiveProfile(id)
        refreshProfiles()
        ProviderState.refresh(repo.activeProfile())
    }

    fun deleteProfile(id: String) {
        repo.deleteProfile(id)
        refreshProfiles()
        ProviderState.refresh(repo.activeProfile())
    }

    fun clearMessage() {
        _message.value = null
    }

    /** 模型窗口优先，其次厂商默认窗口，兜底 8192。 */
    private fun defaultContextFor(spec: ProviderSpec, model: String): Int =
        spec.modelContexts[model] ?: spec.defaultContextWindow ?: 8192

    private fun buildProfile(): ConnectionProfile {
        val spec = provider() ?: return ConnectionProfile(providerId = "")
        val baseOverride = _baseUrl.value.trim().ifBlank { "" }
        return ConnectionProfile(
            id = editingId.orEmpty(),
            name = _profileName.value.ifBlank { spec.displayName },
            providerId = spec.id,
            apiKey = _apiKey.value,
            baseUrlOverride = if (baseOverride == spec.baseUrl) "" else baseOverride,
            model = _selectedModel.value,
            region = _region.value,
            accountId = _accountId.value,
            apiVersionOverride = _apiVersion.value,
            contextWindow = _contextWindow.value,
            sampler = editingSampler.copy(maxTokens = _maxTokens.value),
        )
    }

    private fun refreshProfiles() {
        _profiles.value = repo.profiles()
        _activeId.value = repo.activeProfile()?.id ?: ""
    }

    private fun hasModelsEndpoint(spec: ProviderSpec): Boolean =
        spec.modelsEndpoint.isNotBlank() || spec.id == "azure" || spec.id == "workers-ai"

    /** 人话报错。 */
    private fun humanError(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            e is UnknownHostException -> "网络不通，请检查网络或地址"
            e is ConnectException -> "连不上服务器，请检查地址或网络"
            e is SocketTimeoutException -> "连接超时，请检查网络或地址"
            m.contains("HTTP 401") || m.contains("HTTP 403") -> "Key 不对或没有权限"
            m.contains("HTTP 404") -> "接口地址不对（404）"
            m.contains("HTTP 429") -> "请求太频繁，请稍后再试"
            m.contains("HTTP 500") || m.contains("HTTP 502") || m.contains("HTTP 503") -> "服务端暂时不可用"
            m.contains("需要账户 ID") || m.contains("Vertex AI") -> m
            else -> "连接失败：${m.ifBlank { "未知错误" }}"
        }
    }
}
