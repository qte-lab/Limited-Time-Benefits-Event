package com.chronie.gift.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Local persistence for the quiz answer state so a user's progress is not lost
 * when the app is backgrounded or killed.
 *
 * We store a single JSON blob (selections + completion flag + last reward
 * summary + the server submission) under one SharedPreferences key. A
 * fingerprint of the current period + question set is kept alongside it: if the
 * server deploys a different quiz (or a new period), stale selections from the
 * previous quiz/period are dropped on load, and a fresh draft begins for the
 * new period.
 */
object QuizPrefs {
    private const val PREFS = "quiz_state"
    private const val K_STATE = "state_v1"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Saved(
        val period: String = "",
        val fp: String = "",
        val single: Map<String, Int> = emptyMap(),
        val multi: Map<String, List<Int>> = emptyMap(),
        val bool: Map<String, Boolean> = emptyMap(),
        val completed: Boolean = false,
        val totalAwarded: Int = 0,
        /** The user's submitted answers, kept so grading can be shown offline. */
        val answers: List<AnswerSubmission>? = null,
        /** The per-question grading, kept so grading can be shown offline. */
        val results: List<QuizResultItem>? = null
    )

    /** Stable-ish fingerprint of the current period + question set. */
    fun fingerprint(period: String, qs: List<QuestionPublic>): String =
        "$period|${qs.size}:${qs.firstOrNull()?.id ?: "-"}.${qs.lastOrNull()?.id ?: "-"}"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): Saved {
        val raw = prefs(context).getString(K_STATE, null) ?: return Saved()
        return runCatching { json.decodeFromString<Saved>(raw) }.getOrDefault(Saved())
    }

    private fun save(context: Context, s: Saved) {
        prefs(context).edit().putString(K_STATE, json.encodeToString(s)).apply()
    }

    /** Persist the current answer selections (merges with anything already saved). */
    fun saveSelections(
        context: Context,
        period: String,
        fp: String,
        single: Map<String, Int>,
        multi: Map<String, List<Int>>,
        bool: Map<String, Boolean>
    ) {
        val cur = load(context)
        save(context, cur.copy(period = period, fp = fp, single = single, multi = multi, bool = bool))
    }

    /** Mark the period as submitted, keeping the selections and recording the reward + grading. */
    fun saveCompleted(
        context: Context,
        period: String,
        fp: String,
        result: SubmitResponse?,
        answers: List<AnswerSubmission>?
    ) {
        val cur = load(context)
        save(
            context,
            cur.copy(
                period = period,
                fp = fp,
                completed = true,
                totalAwarded = result?.totalAwarded ?: cur.totalAwarded,
                answers = answers ?: cur.answers,
                results = result?.results ?: cur.results
            )
        )
    }

    /** Replace persisted state for a (possibly new) quiz/period, dropping stale selections. */
    fun resetFor(context: Context, period: String, fp: String, completed: Boolean) {
        save(context, Saved(period = period, fp = fp, completed = completed))
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(K_STATE).apply()
    }
}
