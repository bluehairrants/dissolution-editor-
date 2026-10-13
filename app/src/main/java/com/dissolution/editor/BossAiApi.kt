package com.dissolution.editor

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val tools: List<ChatTool>,
    @SerializedName("tool_choice") val toolChoice: String = "auto",
)

data class ChatMessage(
    val role: String,
    val content: String? = null,
)

data class ChatTool(
    val type: String = "function",
    val function: ToolFunction,
)

data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: Map<String, Any>,
)

data class ChatResponse(
    val choices: List<ChatChoice> = emptyList(),
)

data class ChatChoice(
    val message: AssistantMessage,
)

data class AssistantMessage(
    val content: String? = null,
    @SerializedName("tool_calls") val toolCalls: List<AssistantToolCall>? = null,
)

data class AssistantToolCall(
    val function: AssistantToolFunction,
)

data class AssistantToolFunction(
    val name: String,
    val arguments: String,
)

interface BossAiService {
    @POST("chat/completions")
    suspend fun complete(
        @Header("Authorization") authorization: String,
        @Body request: ChatRequest,
    ): ChatResponse
}

object BossAiApi {
    fun create(baseUrl: String): BossAiService {
        require(baseUrl.startsWith("https://") && baseUrl.endsWith("/")) {
            "BossAI base URL must use HTTPS and end with /"
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BossAiService::class.java)
    }

    fun downloadTool() = ChatTool(
        function = ToolFunction(
            name = "download_video",
            description = "Open a public video URL in Seal so the user can review and confirm its download.",
            parameters = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "url" to mapOf("type" to "string", "description" to "The video page URL"),
                ),
                "required" to listOf("url"),
                "additionalProperties" to false,
            ),
        ),
    )

    fun editorTools() = listOf(
        downloadTool(),
        ChatTool(
            function = ToolFunction(
                name = "set_trim",
                description = "Set the selected clip's trim range using seconds from the start of the source video.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "start_seconds" to mapOf("type" to "number"),
                        "end_seconds" to mapOf("type" to "number"),
                    ),
                    "required" to listOf("start_seconds", "end_seconds"),
                    "additionalProperties" to false,
                ),
            ),
        ),
        ChatTool(
            function = ToolFunction(
                name = "set_export_profile",
                description = "Choose output size, frame rate, codec, and bitrate. Frame rate may not exceed the source, and hardware capability is checked.",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "height" to mapOf("type" to "integer", "enum" to listOf(1080, 2160)),
                        "frame_rate" to mapOf("type" to "integer", "enum" to listOf(30, 60, 120)),
                        "bitrate_mbps" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 150),
                        "codec" to mapOf("type" to "string", "enum" to listOf("h264", "hevc")),
                    ),
                    "additionalProperties" to false,
                ),
            ),
        ),
    )
}