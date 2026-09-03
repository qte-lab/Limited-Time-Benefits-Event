package com.chronie.gift.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.floor
import kotlin.random.Random

/**
 * A single entry of the "今天吃什么" wheel.
 *
 * Ported from `flutter_app/lib/models/food_item.dart`. [category] is kept for
 * compatibility with data exported by the Flutter app, but the wheel itself no
 * longer groups items by category.
 */
@Serializable
data class FoodItem(
    val id: Int,
    val name: String,
    val category: String = "",
    val weight: Double = 1.0
)

/**
 * Persistence for the wheel contents.
 *
 * Mirrors `FoodProvider` from the Flutter app, which kept a JSON file named
 * `food_data.json`. Here the list is stored as a single JSON blob in
 * SharedPreferences, the same pattern [QuizPrefs] uses, which avoids the file
 * IO and error handling the Flutter version needed.
 */
object FoodPrefs {
    private const val PREFS = "food_data"
    private const val K_ITEMS = "foods_v1"

    private val json = Json { ignoreUnknownKeys = true }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): List<FoodItem> {
        val raw = prefs(context).getString(K_ITEMS, null) ?: return defaults()
        return runCatching { json.decodeFromString<List<FoodItem>>(raw) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: defaults()
    }

    fun save(context: Context, items: List<FoodItem>) {
        prefs(context).edit().putString(K_ITEMS, json.encodeToString(items)).apply()
    }

    /**
     * The seed menu shipped with the app.
     *
     * Weights encode price: cheaper dishes are more likely to be picked, so the
     * wheel nudges towards a budget friendly lunch.
     */
    fun defaults(): List<FoodItem> = listOf(
        FoodItem(id = 1, name = "各式美味泡面", weight = 11.0),
        FoodItem(id = 2, name = "蛋汁大排面", weight = 1.1),
        FoodItem(id = 3, name = "凉皮", weight = 1.47),
        FoodItem(id = 4, name = "鑫花溪牛肉米粉", weight = 0.88),
        FoodItem(id = 5, name = "港式虾仁滑蛋", weight = 0.88),
        FoodItem(id = 6, name = "热干面", weight = 1.47),
        FoodItem(id = 7, name = "云南过桥米线", weight = 1.1),
        FoodItem(id = 8, name = "汉堡", weight = 0.55),
        FoodItem(id = 9, name = "奶茶", weight = 1.29),
        FoodItem(id = 10, name = "馄饨", weight = 1.47),
        FoodItem(id = 11, name = "火鸡面", weight = 0.88)
    )
}

/**
 * Process wide, observable list of wheel entries.
 *
 * The wheel screen and the food management screen live on different navigation
 * destinations, so both read and write this single source of truth; every
 * mutation is written through to [FoodPrefs] so it survives process death.
 */
object FoodStore {

    val items: SnapshotStateList<FoodItem> = mutableStateListOf()

    private var loaded = false

    /** Loads from disk the first time it is called in this process. */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        items.clear()
        items.addAll(FoodPrefs.load(context))
        loaded = true
    }

    fun add(context: Context, name: String, weight: Double) {
        val nextId = (items.maxOfOrNull { it.id } ?: 0) + 1
        items.add(FoodItem(id = nextId, name = name, weight = weight))
        FoodPrefs.save(context, items)
    }

    fun delete(context: Context, id: Int) {
        if (items.removeAll { it.id == id }) {
            FoodPrefs.save(context, items)
        }
    }

    fun restoreDefaults(context: Context) {
        items.clear()
        items.addAll(FoodPrefs.defaults())
        FoodPrefs.save(context, items)
    }

    /**
     * Weighted random pick, same algorithm as `FoodProvider.getRandomFood`.
     *
     * Returns null when the menu is empty so callers can show an empty state
     * instead of spinning a wheel with nothing on it.
     */
    fun randomFood(): FoodItem? {
        val snapshot = items.toList()
        if (snapshot.isEmpty()) return null
        val total = snapshot.sumOf { it.weight }
        if (total <= 0.0) return snapshot.random()
        var ticket = Random.nextDouble() * total
        for (food in snapshot) {
            ticket -= food.weight
            if (ticket <= 0.0) return food
        }
        return snapshot.last()
    }

    /**
     * Absolute wheel rotation (degrees) that parks the pointer on [picked].
     *
     * The wheel is drawn with sector 0 starting at 3 o'clock and sweeping
     * clockwise, and the pointer is fixed at 12 o'clock (-90 degrees). Rotating
     * the wheel clockwise by R puts the middle of the picked sector at
     * `middle + R`, so `R = (-90 - middle) mod 360`. A few whole turns are added
     * on top to keep the spin long enough to feel like a draw.
     */
    fun targetRotation(current: Double, picked: FoodItem, items: List<FoodItem>): Double {
        if (items.isEmpty()) return current
        val total = items.sumOf { it.weight }
        if (total <= 0.0) return current

        var start = -90.0
        var middle = -90.0
        for (food in items) {
            val sweep = 360.0 * (food.weight / total)
            if (food.id == picked.id) {
                middle = start + sweep / 2.0
                break
            }
            start += sweep
        }

        val needed = ((-90.0 - middle) % 360.0 + 360.0) % 360.0
        val currentMod = ((current % 360.0) + 360.0) % 360.0
        val base = current - currentMod
        val delta = ((needed - currentMod) % 360.0 + 360.0) % 360.0
        val turns = 4 + Random.nextInt(3) // 4-6 whole turns
        return floor(base) + turns * 360.0 + delta
    }
}
