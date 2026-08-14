package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 官方 google.js generateJWTToken / getProjectIdFromServiceAccount / getVertexAIAuth /
 * getGoogleApiConfig 差分（getAccessToken 打桩为固定串；Date.now 冻结）。
 * fixture 由 scripts/diff/vertex-auth-official.mjs（官方函数逐字）生成，禁止手改。
 */
class VertexAuthDiffTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun cases(): List<kotlinx.serialization.json.JsonObject> {
        val resource = checkNotNull(javaClass.getResource("/diff/vertex-auth.json"))
        return json.parseToJsonElement(resource.readText()).jsonObject.getValue("cases").jsonArray.map { it.jsonObject }
    }

    @Test
    fun `jwt and project id match official fixtures`() {
        val all = cases()
        val sa = all.first { it.getValue("id").jsonPrimitive.content == "service_account" }
            .getValue("serviceAccount").jsonObject
        val jwtCase = all.first { it.getValue("id").jsonPrimitive.content == "jwt" }
        val expectedJwt = jwtCase.getValue("jwt").jsonPrimitive.content
        val now = jwtCase.getValue("nowEpochSec").jsonPrimitive.content.toLong()
        assertEquals("jwt", expectedJwt, VertexAuth.jwt(sa, now))
        val projectCase = all.first { it.getValue("id").jsonPrimitive.content == "project_id" }
        assertEquals("project_id", projectCase.getValue("projectId").jsonPrimitive.content, VertexAuth.projectId(sa))
    }

    @Test
    fun `request url and headers match official fixtures`() {
        val all = cases()
        val sa = all.first { it.getValue("id").jsonPrimitive.content == "service_account" }
            .getValue("serviceAccount").jsonObject
        for (caseEl in all) {
            val id = caseEl.getValue("id").jsonPrimitive.content
            if (id in setOf("service_account", "jwt", "project_id")) continue
            val input = caseEl.getValue("input").jsonObject
            val authMode = input.getValue("vertexai_auth_mode").jsonPrimitive.content
            val region = input["vertexai_region"]?.jsonPrimitive?.content ?: "us-central1"
            val model = input.getValue("model").jsonPrimitive.content
            val projectId = input["vertexai_express_project_id"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: if (authMode == "full") VertexAuth.projectId(sa) else null
            val reverseProxy = input["reverse_proxy"]?.jsonPrimitive?.content.orEmpty()
            val proxyPassword = input["proxy_password"]?.jsonPrimitive?.content.orEmpty()
            val apiKey = input.getValue("apiKey").jsonPrimitive.content
            val endpoint = input.getValue("endpoint").jsonPrimitive.content
            val accessToken = input["accessToken"]?.jsonPrimitive?.content?.takeIf { authMode == "full" && reverseProxy.isBlank() }
            val req = VertexAuth.requestUrlAndHeaders(
                authMode = authMode,
                region = region,
                model = model,
                projectId = projectId,
                reverseProxy = reverseProxy,
                proxyPassword = proxyPassword,
                apiKey = apiKey,
                endpoint = endpoint,
                accessToken = accessToken,
            )
            val expectedUrl = caseEl.getValue("url").jsonPrimitive.content
            assertEquals("case $id url", expectedUrl, req.url)
            val expectedHeaders = caseEl.getValue("headers").jsonObject.entries
                .filter { it.key != "Content-Type" }
                .associate { (k, v) -> k to (v as JsonPrimitive).content }
            assertEquals("case $id headers", expectedHeaders, req.headers)
        }
    }
}
