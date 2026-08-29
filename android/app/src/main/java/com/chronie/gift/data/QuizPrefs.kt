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
 * summary) under one SharedPreferences key. A fingerprint of the current
 * question set is kept alongside it: if the server swaps in a different quiz,
 * stale selections from the previous quiz are dropped on load.
 */
object QuizPrefs {
    private const val PREFS = "quiz_state"
    private const val K_STATE = "state_v1"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Saved(
        val fp: String = "",
        val single: Map<String, Int> = emptyMap(),
        val multi: Map<String, List<Int>> = emptyMap(),
        val bool: Map<String, Boolean> = emptyMap(),
        val completed: Boolean = false,
        val totalAwarded: Int = 0,
        val balance: Int = 0,
        val dailyUsed: Int = 0,
        val dailyLimit: Int = 0
    )

    /** Stable-ish fingerprint of the current question set. */
    fun fingerprint(qs: List<QuestionPublic>): String =
        "${qs.size}:${qs.firstOrNull()?.id ?: "-"}.${qs.lastOrNull()?.id ?: "-"}"

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
        fp: String,
        single: Map<String, Int>,
        multi: Map<String, List<Int>>,
        bool: Map<String, Boolean>
    ) {
        val cur = load(context)
        save(context, cur.copy(fp = fp, single = single, multi = multi, bool = bool))
    }

    /** Mark the quiz as submitted, keeping the selections and recording the reward. */
    fun saveCompleted(context: Context, fp: String, result: SubmitResponse?) {
        val cur = load(context)
        save(
            context,
            cur.copy(
                fp = fp,
                completed = true,
                totalAwarded = result?.totalAwarded ?: cur.totalAwarded,
                balance = result?.balance ?: cur.balance,
                dailyUsed = result?.dailyUsed ?: cur.dailyUsed,
                dailyLimit = result?.dailyLimit ?: cur.dailyLimit
            )
        )
    }

    /** Replace persisted state for a (possibly new) quiz, dropping stale selections. */
    fun resetFor(context: Context, fp: String, completed: Boolean) {
        save(context, Saved(fp = fp, completed = completed))
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(K_STATE).apply()
    }
}
