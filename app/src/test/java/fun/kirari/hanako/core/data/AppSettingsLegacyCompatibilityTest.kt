package `fun`.kirari.hanako.core.data

import `fun`.kirari.llm.core.ProviderKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 固化 v0.0.19 及以前真实落盘的设置 JSON 的升级兼容行为。
 *
 * 解码必须复用生产配置 [HanakoSettingsJson]，不要在这里另写一份 Json：
 * 否则将来生产配置里的 `coerceInputValues` 被删掉，测试依然会通过。
 */
class AppSettingsLegacyCompatibilityTest {

    @Test
    fun legacyV019Json_decodesWithoutResettingProvidersAssistantsAndHistory() {
        val decoded = HanakoSettingsJson.decodeFromString<AppSettings>(LEGACY_V019_SETTINGS_JSON)

        assertEquals(listOf("user-provider-1"), decoded.providers.map { it.id })
        assertEquals("sk-user-key", decoded.providers.single().apiKey)
        assertEquals(listOf("assistant-1"), decoded.assistants.map { it.id })
        assertEquals(listOf("history-1", "history-2"), decoded.history.map { it.id })

        val normalized = decoded.normalize()

        assertEquals(listOf("user-provider-1"), normalized.providers.map { it.id })
        assertEquals("sk-user-key", normalized.providers.single().apiKey)
        assertEquals(listOf("assistant-1"), normalized.assistants.map { it.id })
        assertEquals(listOf("history-1", "history-2"), normalized.history.map { it.id })
        assertEquals("答案一", normalized.history.first { it.id == "history-1" }.answer)
    }

    @Test
    fun normalize_remapsLegacyGatewayModelSelectionsOntoUserProviderModels() {
        val normalized = HanakoSettingsJson.decodeFromString<AppSettings>(LEGACY_V019_SETTINGS_JSON).normalize()

        val provider = normalized.providers.single { it.id == "user-provider-1" }
        assertEquals(ModelSelection(provider.id, provider.chatModel), normalized.textModelSelection)
        assertEquals(ModelSelection(provider.id, provider.visionModel), normalized.visionModelSelection)
        assertEquals(ModelSelection(provider.id, provider.ocrModel), normalized.ocrModelSelection)

        // 只改 providerId、保留 kirari-* 模型名会让用户拿自己的 Key 请求 404，必须整条清掉。
        val selectedModels = listOf(
            normalized.textModelSelection.model,
            normalized.visionModelSelection.model,
            normalized.ocrModelSelection.model
        )
        assertTrue(selectedModels.none { it.startsWith("kirari") })
        assertTrue(selectedModels.none { it.isBlank() })
    }

    @Test
    fun normalize_remapsLegacyGatewaySelectedProviderToFirstUserProvider() {
        val normalized = HanakoSettingsJson.decodeFromString<AppSettings>(LEGACY_V019_SETTINGS_JSON).normalize()

        assertEquals("user-provider-1", normalized.selectedProviderId)
    }

    @Test
    fun unknownLegacyProviderKindIsCoercedInsteadOfResettingWholeSettings() {
        val decoded = HanakoSettingsJson.decodeFromString<AppSettings>(LEGACY_PROVIDER_KIND_GATEWAY_JSON)

        assertEquals(ProviderKind.OPENAI_COMPATIBLE, decoded.providers.single().kind)
        assertEquals("user-provider-1", decoded.providers.single().id)
        assertEquals("sk-user-key", decoded.providers.single().apiKey)
        assertFalse(decoded.history.isEmpty())
        assertEquals(listOf("history-1"), decoded.history.map { it.id })
    }

    private companion object {
        /** v0.0.19 及以前由 AppSettings 序列化产生，含作者网关的 `kirari` 段与网关模型选择。 */
        val LEGACY_V019_SETTINGS_JSON = """
            {
              "schemaVersion": 3,
              "providers": [
                {
                  "id": "user-provider-1",
                  "name": "我的 OpenAI",
                  "kind": "OPENAI_COMPATIBLE",
                  "baseUrl": "https://api.example.com/v1",
                  "apiKey": "sk-user-key",
                  "chatModel": "gpt-4o-mini",
                  "visionModel": "gpt-4o",
                  "ocrModel": "gpt-4.1-mini",
                  "favoriteModels": ["gpt-4o-mini"],
                  "enabled": true
                }
              ],
              "selectedProviderId": "__kirari_network__",
              "assistants": [
                {
                  "id": "assistant-1",
                  "name": "题目解答助手",
                  "ocrPrompt": "请准确提取图片中的题目、选项、公式和注释，尽量保持原有结构，不要解释。",
                  "textPrompt": "你是题目解答助手。",
                  "visionPrompt": "你是题目解答助手。请直接阅读图片。"
                }
              ],
              "selectedAssistantId": "assistant-1",
              "processingRoute": "OCR_THEN_LLM",
              "screenCaptureMethod": "MEDIA_PROJECTION",
              "automation": {
                "completionNotificationEnabled": true,
                "autoModeTimeoutSeconds": 30
              },
              "trustAllHttpsCertificates": false,
              "textModelSelection": { "providerId": "__kirari_network__", "model": "kirari-text" },
              "visionModelSelection": { "providerId": "__kirari_network__", "model": "kirari-multimodal" },
              "ocrModelSelection": { "providerId": "__kirari_network__", "model": "kirari-ocr" },
              "localOcr": {
                "installed": true,
                "providerId": "__local_mlkit__",
                "modelId": "mlkit_chinese_ocr",
                "displayName": "ML Kit 中文 OCR"
              },
              "kirari": {
                "serverUrl": "https://legacy-gateway.example.com",
                "auth": {
                  "accessToken": "legacy-access-token",
                  "refreshToken": "legacy-refresh-token",
                  "idToken": "",
                  "tokenType": "Bearer",
                  "scope": "openid",
                  "accessTokenExpiresAtMillis": 0
                },
                "profile": {
                  "subject": "sub-1",
                  "email": "user@example.com",
                  "name": "Hanako",
                  "preferredUsername": "hanako",
                  "nickname": "hanako",
                  "lastSyncedAtMillis": 0
                }
              },
              "webSearch": {
                "enabled": false,
                "provider": {
                  "kind": "TAVILY",
                  "baseUrl": "https://api.tavily.com/search",
                  "apiKey": ""
                },
                "maxResults": 3,
                "automationAlsoSearch": false
              },
              "lastResult": null,
              "history": [
                {
                  "id": "history-1",
                  "assistantName": "题目解答助手",
                  "route": "OCR_THEN_LLM",
                  "answer": "答案一",
                  "createdAtMillis": 1700000000000
                },
                {
                  "id": "history-2",
                  "assistantName": "题目解答助手",
                  "route": "OCR_THEN_LLM",
                  "answer": "答案二",
                  "createdAtMillis": 1700000001000
                }
              ],
              "historyGroups": [],
              "historyMetadata": []
            }
        """.trimIndent()

        /** 纯防御用例：已核对该 kind 没有任何写入路径能落盘，用于防未来再删枚举值。 */
        val LEGACY_PROVIDER_KIND_GATEWAY_JSON = """
            {
              "schemaVersion": 3,
              "providers": [
                {
                  "id": "user-provider-1",
                  "name": "我的 OpenAI",
                  "kind": "KIRARI_NETWORK",
                  "baseUrl": "https://legacy-gateway.example.com",
                  "apiKey": "sk-user-key",
                  "chatModel": "kirari-text",
                  "visionModel": "kirari-multimodal",
                  "ocrModel": "kirari-ocr"
                }
              ],
              "selectedProviderId": "user-provider-1",
              "history": [
                {
                  "id": "history-1",
                  "assistantName": "题目解答助手",
                  "route": "OCR_THEN_LLM",
                  "answer": "答案一"
                }
              ]
            }
        """.trimIndent()
    }
}
