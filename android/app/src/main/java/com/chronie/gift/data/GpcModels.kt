package com.chronie.gift.data

import kotlinx.serialization.Serializable

/**
 * Unified `{ success, data, message }` envelope returned by most GPC / event-server
 * endpoints. The quiz questions and the OAuth token exchange are wrapped like this;
 * the quiz submit and the gpc-config endpoints return a flat body instead (see the
 * dedicated models below).
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean = false,
    val data: T? = null,
    val message: String? = null
)

/** Payload of GET /api/oauth/gpc-config (flat JSON, not wrapped). */
@Serializable
data class GpcOAuthConfig(
    val clientId: String = "",
    val clientSecret: String = "",
    val gpcBaseUrl: String = "",
    val redirectUri: String = "",
    val scope: String = ""
)

/** Inner data of the OAuth token response (wrapped in [ApiResponse]). */
@Serializable
data class GpcTokenData(
    val access_token: String = "",
    val token_type: String? = null,
    val expires_in: Int? = null,
    val refresh_token: String? = null,
    val scope: String? = null,
    val userId: String? = null,
    val username: String? = null
)

/** Authorization state observed by the quiz screen. */
sealed interface GpcAuthState {
    data object Unauthorized : GpcAuthState
    data object Authorizing : GpcAuthState
    data class Authorized(
        val token: String,
        val userId: String?,
        val username: String?
    ) : GpcAuthState

    data class Error(val message: String) : GpcAuthState
}

/** A question as served by GET /api/quiz/questions (answer stripped server-side). */
@Serializable
data class QuestionPublic(
    val id: String = "",
    val type: String = "single", // single | multiple | bool
    val content: String = "",
    val options: List<String>? = null,
    val reward: Int = 0
)

/** Per-question outcome returned by POST /api/quiz/submit. */
@Serializable
data class QuizResultItem(
    val id: String = "",
    val correct: Boolean = false,
    val awarded: Int = 0,
    val alreadyClaimed: Boolean? = null,
    val limited: Boolean? = null
)

/** Full, server-side record of one user's submission for one period. */
@Serializable
data class SubmissionData(
    val submittedAt: Long = 0,
    val totalAwarded: Int = 0,
    val answers: List<AnswerSubmission> = emptyList(),
    val results: List<QuizResultItem> = emptyList()
)

/** Flat body of GET /api/quiz/questions (period + questions, answers stripped). */
@Serializable
data class QuizQuestionsResponse(
    val success: Boolean = false,
    val period: String = "",
    val data: List<QuestionPublic>? = null,
    val message: String? = null
)

/** Inner data of GET /api/quiz/status (period + completion + stored submission). */
@Serializable
data class QuizStatusData(
    val success: Boolean = false,
    val period: String = "",
    val submitted: Boolean = false,
    val submission: SubmissionData? = null
)

/** Flat body of POST /api/quiz/submit (success + results + period). */
@Serializable
data class SubmitResponse(
    val success: Boolean = false,
    val results: List<QuizResultItem>? = null,
    val totalAwarded: Int = 0,
    val period: String = "",
    /** True when the user already submitted this period; the submit was rejected. */
    val alreadySubmitted: Boolean = false,
    /** Present on an alreadySubmitted response so the client can redraw grading. */
    val submission: SubmissionData? = null,
    val message: String? = null
)

/** One submitted answer. `value` is polymorphic: Int | Boolean | JsonArray. */
@Serializable
data class AnswerSubmission(
    val id: String,
    val value: kotlinx.serialization.json.JsonElement
)

@Serializable
data class SubmitRequest(
    val token: String,
    val answers: List<AnswerSubmission>
)
