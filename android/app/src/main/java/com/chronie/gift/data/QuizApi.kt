package com.chronie.gift.data

import com.chronie.gift.ui.screens.ApiClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Pure-Kotlin client for the quiz / activity endpoints on the event server.
 * Questions are fetched as rich text (Markdown) and rendered by the UI; the
 * user's GPC token authorizes the submit call which mints rewards server-side.
 */
object QuizApi {
    private val client = ApiClient.client
    private val json = Json { ignoreUnknownKeys = true }

    /** GET /api/quiz/questions — period + questions (answers stripped server-side). */
    suspend fun fetchQuestions(baseUrl: String): QuizQuestionsResponse {
        val text = withContext(Dispatchers.IO) {
            client.get("$baseUrl/api/quiz/questions").bodyAsText()
        }
        val env = json.decodeFromString<QuizQuestionsResponse>(text)
        if (env.success && env.data != null) return env
        throw Exception(env.message ?: "获取题目失败")
    }

    /** POST /api/quiz/submit — validates answers and mints GPC for correct ones. */
    suspend fun submit(
        baseUrl: String,
        token: String,
        answers: List<AnswerSubmission>
    ): SubmitResponse {
        val text = withContext(Dispatchers.IO) {
            client.post("$baseUrl/api/quiz/submit") {
                contentType(ContentType.Application.Json)
                setBody(SubmitRequest(token, answers))
            }.bodyAsText()
        }
        val resp = json.decodeFromString<SubmitResponse>(text)
        // A rejected re-submit (alreadySubmitted) is returned as success=false but is
        // not an error — the caller surfaces it as a "您已提交过" state, so return it.
        if (resp.success || resp.alreadySubmitted) return resp
        throw Exception(resp.message ?: "提交失败")
    }

    /** GET /api/quiz/status — whether this period was submitted, plus the stored submission. */
    suspend fun getStatus(baseUrl: String, token: String): QuizStatusData {
        val text = withContext(Dispatchers.IO) {
            client.get("$baseUrl/api/quiz/status?token=${java.net.URLEncoder.encode(token, "UTF-8")}").bodyAsText()
        }
        val env = json.decodeFromString<QuizStatusData>(text)
        if (env.success) return env
        return QuizStatusData()
    }
}
