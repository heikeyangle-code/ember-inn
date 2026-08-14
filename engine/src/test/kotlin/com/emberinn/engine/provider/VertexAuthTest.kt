package com.emberinn.engine.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.util.Base64

/**
 * 官方 google.js generateJWTToken / getProjectIdFromServiceAccount / URL 分支单测。
 * 用临时 RSA 密钥对验证 JWT 三段结构与 RS256 签名。
 */
class VertexAuthTest {

    private fun serviceAccountJson(privateKeyPem: String): String {
        val sa = buildJsonObject {
            put("type", "service_account")
            put("project_id", "demo-project")
            put("private_key", privateKeyPem)
            put("client_email", "demo@demo.iam.gserviceaccount.com")
            put("client_id", "12345")
        }
        return Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), sa)
    }

    private fun rsaPem(): Pair<String, RSAPublicKey> {
        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val kp = gen.generateKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\n" +
            Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(kp.private.encoded) +
            "\n-----END PRIVATE KEY-----\n"
        return pem to (kp.public as RSAPublicKey)
    }

    @Test
    fun `validate accepts official required fields`() {
        val (pem, _) = rsaPem()
        assertNull(VertexAuth.validate(serviceAccountJson(pem)))
        assertEquals("Invalid JSON format", VertexAuth.validate("not json"))
        val bad = buildJsonObject {
            put("type", "other")
            put("project_id", "demo-project")
            put("private_key", pem)
            put("client_email", "demo@demo.iam.gserviceaccount.com")
            put("client_id", "12345")
        }
        assertEquals(
            "Invalid service account type. Expected \"service_account\"",
            VertexAuth.validate(Json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), bad)),
        )
    }

    @Test
    fun `jwt has three segments and valid RS256 signature`() {
        val (pem, pub) = rsaPem()
        val sa = VertexAuth.serviceAccount(serviceAccountJson(pem))!!
        val jwt = VertexAuth.jwt(sa)
        val parts = jwt.split(".")
        assertEquals(3, parts.size)
        val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]))
        assertEquals("demo@demo.iam.gserviceaccount.com", Json.parseToJsonElement(payloadJson).jsonObject["iss"]?.toString()?.trim('"'))
        assertEquals("https://oauth2.googleapis.com/token", Json.parseToJsonElement(payloadJson).jsonObject["aud"]?.toString()?.trim('"'))
        val sig = Signature.getInstance("SHA256withRSA")
        sig.initVerify(pub)
        sig.update((parts[0] + "." + parts[1]).toByteArray(Charsets.UTF_8))
        assert(sig.verify(Base64.getUrlDecoder().decode(parts[2])))
    }

    @Test
    fun `url branches match official google js`() {
        assertEquals(
            "https://us-central1-aiplatform.googleapis.com/v1/publishers/google/models/gemini-2.5-pro:generateContent",
            VertexAuth.url("us-central1", "gemini-2.5-pro", null, "generateContent"),
        )
        assertEquals(
            "https://us-central1-aiplatform.googleapis.com/v1/projects/p/locations/us-central1/publishers/google/models/m:streamGenerateContent",
            VertexAuth.url("us-central1", "m", "p", "streamGenerateContent"),
        )
        assertEquals(
            "https://aiplatform.googleapis.com/v1/projects/p/locations/global/publishers/google/models/m:generateContent",
            VertexAuth.url("global", "m", "p", "generateContent"),
        )
    }

    @Test
    fun `project id extracted from service account`() {
        val (pem, _) = rsaPem()
        val sa = VertexAuth.serviceAccount(serviceAccountJson(pem))!!
        assertEquals("demo-project", VertexAuth.projectId(sa))
        assertNotNull(sa)
    }
}
