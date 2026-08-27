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

/** Flat body of POST /api/quiz/submit (success + results + balance). */
@Serializable
data class SubmitResponse(
    val success: Boolean = false,
    val results: List<QuizResultItem>? = null,
    val totalAwarded: Int = 0,
    val balance: Int = 0,
    val dailyUsed: Int = 0,
    val dailyLimit: Int = 0,
    /** True when the user already submitted the quiz before; the submit was rejected. */
    val alreadySubmitted: Boolean = false,
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
