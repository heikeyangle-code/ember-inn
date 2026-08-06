package com.emberinn.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.emberinn.app.data.ChatRepository
import com.emberinn.engine.prompt.CompletionMessage
import com.emberinn.engine.provider.ConnectionProfile
import com.emberinn.engine.provider.LlmClient
import com.emberinn.engine.provider.ProviderRegistry
import com.emberinn.engine.provider.ProviderSpec
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 提供商三步配置（README：选供应商 → 粘贴 Key 并测试 → 选模型完成）。
 * 支持多连接档案：每个提供商可存一个档案，顶部可切换激活档案。
 */
class ProviderSetupViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ChatRepository(application)
    private val client = LlmClient()

    val providers: List<ProviderSpec> = ProviderRegistry.all()

    private val _step = MutableStateFlow(1)
    val step: StateFlow<Int> = _step

    private val _profiles = MutableStateFlow(repo.profiles())
    val profiles: StateFlow<List<ConnectionProfile>> = _profiles

    private val _activeId = MutableStateFlow(repo.activeProfile()?.id ?: "")
    val activeId: StateFlow<String> = _activeId

    // 正在编辑的档案（编辑已有档案时保留 id，新建时为 null）
    private var editingProfileId: String? = null

    private val _providerId = MutableStateFlow("")
    val providerId: StateFlow<String> = _providerId
    private val _profileName = MutableStateFlow("")
    val profileName: StateFlow<String> = _profileName
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey
    private val _region = MutableStateFlow("")
    val region: StateFlow<String> = _region
    private val _accountId = MutableStateFlow("")
    val accountId: StateFlow<String> = _accountId
    private val _baseUrlOverride = MutableStateFlow("")
    val baseUrlOverride: StateFlow<String> = _baseUrlOverride
    private val _apiVersionOverride = MutableStateFlow("")
    val apiVersionOverride: StateFlow<String> = _apiVersionOverride
    private val _advancedExpanded = MutableStateFlow(false)
    val advancedExpanded: StateFlow<Boolean> = _advancedExpanded

    private val _models = MutableStateFlow<List<String>>(emptyList())
    val models: StateFlow<List<String>> = _models
    private val _selectedModel = MutableStateFlow("")
    val selectedModel: StateFlow<String> = _selectedModel
    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        // 已有激活档案 → 直接进“完成”摘要页（README：打开即聊，不强制重配）
        if (repo.activeProfile() != null) _step.value = 4
    }

    fun provider(): ProviderSpec? = ProviderRegistry.get(_providerId.value)

    fun selectProvider(id: String) {
        val spec = ProviderRegistry.get(id) ?: return
        val existing = _profiles.value.firstOrNull { it.providerId == id }
        editingProfileId = existing?.id
        _providerId.value = id
        _profileName.value = existing?.name?.ifBlank { spec.displayName } ?: spec.displayName
        _apiKey.value = existing?.apiKey.orEmpty()
        _region.value = existing?.region.orEmpty()
        _accountId.value = existing?.accountId.orEmpty()
        _baseUrlOverride.value = existing?.baseUrlOverride.orEmpty()
        _apiVersionOverride.value = existing?.apiVersionOverride.orEmpty()
        _models.value = existing?.model?.let { if (it.isNotBlank()) listOf(it) else null }
            ?: spec.defaultModels
        _selectedModel.value = existing?.model?.takeIf { it.isNotBlank() }
            ?: spec.defaultModels.firstOrNull().orEmpty()
        _message.value = null
        _step.value = 2
    }

    fun switchProfile(id: String) {
        repo.setActiveProfile(id)
        refreshProfiles()
        _message.value = null
        _step.value = 4
    }

    fun deleteProfile(id: String) {
        repo.deleteProfile(id)
        refreshProfiles()
        if (_activeId.value == id) _step.value = 1
    }

    fun setProfileName(value: String) {
        _profileName.value = value
    }

    /** 粘贴自动去空格（README：粘贴自动去空格）。 */
    fun setApiKey(value: String) {
        _apiKey.value = value.filterNot { it.isWhitespace() }
    }

    fun setRegion(value: String) {
        _region.value = value
    }

    fun setAccountId(value: String) {
        _accountId.value = value
    }

    fun setBaseUrlOverride(value: String) {
        _baseUrlOverride.value = value.trim()
    }

    fun setApiVersionOverride(value: String) {
        _apiVersionOverride.value = value.trim()
    }

    fun toggleAdvanced() {
        _advancedExpanded.update { !it }
    }

    fun back() {
        _step.value = if (_step.value > 1) _step.value - 1 else 1
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
                        // 无模型列表端点的提供商（Perplexity / 自定义等）：最小对话探测验证 Key
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
                _selectedModel.value = list.firstOrNull() ?: ""
                _message.value = if (models.isNotEmpty() && hasModelsEndpoint(spec)) {
                    "连接成功，已拉到 ${models.size} 个模型"
                } else {
                    "连接成功，已用预填模型兜底"
                }
                _step.value = 3
            }.onFailure { e ->
                _message.value = humanError(e)
            }
        }
    }

    private fun hasModelsEndpoint(spec: ProviderSpec): Boolean =
        spec.modelsEndpoint.isNotBlank() || spec.id == "azure" || spec.id == "workers-ai"

    fun selectModel(model: String) {
        _selectedModel.value = model
    }

    fun finish() {
        val spec = provider() ?: return
        if (_selectedModel.value.isBlank() && spec.defaultModels.isNotEmpty()) {
            _selectedModel.value = spec.defaultModels.first()
        }
        repo.saveProfile(buildProfile(), active = true)
        refreshProfiles()
        _message.value = "已保存：${spec.displayName} / ${_selectedModel.value}"
        _step.value = 4
    }

    fun restart() {
        _message.value = null
        _step.value = 1
    }

    private fun buildProfile(): ConnectionProfile {
        val spec = provider() ?: return ConnectionProfile(providerId = "")
        return ConnectionProfile(
            id = editingProfileId.orEmpty(),
            name = _profileName.value.ifBlank { spec.displayName },
            providerId = spec.id,
            apiKey = _apiKey.value,
            baseUrlOverride = _baseUrlOverride.value,
            model = _selectedModel.value,
            region = _region.value,
            accountId = _accountId.value,
            apiVersionOverride = _apiVersionOverride.value,
        )
    }

    private fun refreshProfiles() {
        _profiles.value = repo.profiles()
        _activeId.value = repo.activeProfile()?.id ?: ""
    }

    /** README：人话报错，不抛裸异常。 */
    private fun humanError(e: Throwable): String {
        val m = e.message.orEmpty()
        return when {
            e is UnknownHostException -> "网络不通，请检查网络或地址"
            e is ConnectException -> "连不上服务器，请检查地址或网络"
            e is SocketTimeoutException -> "连接超时，请检查网络或地址"
            m.contains("HTTP 401") || m.contains("HTTP 403") -> "Key 不对或没有权限，请检查后重试"
            m.contains("HTTP 404") -> "接口地址不对（404），请检查地址或区域"
            m.contains("HTTP 429") -> "请求太频繁（429），等一分钟再试"
            m.contains("HTTP 500") || m.contains("HTTP 502") || m.contains("HTTP 503") -> "服务端暂时不可用，稍后再试"
            m.contains("需要账户 ID") || m.contains("Vertex AI") -> m
            else -> "连接失败：${m.ifBlank { "未知错误" }}"
        }
    }
}
