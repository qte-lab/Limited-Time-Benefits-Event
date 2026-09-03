package com.chronie.gift.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
    val weight: Double = 1.0,
    /**
     * Price per serving in yuan, used to derive the weight from a budget.
     *
     * Null for dishes added before pricing existed (or typed in with a hand
     * picked weight), which is why the field is optional: old saved data simply
     * deserialises to null instead of failing.
     */
    val price: Double? = null
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
     * The seed menu shipped with the app: what the dishes actually cost.
     *
     * Weights are *not* listed here — they are derived from the price against
     * [FoodStore.DEFAULT_BUDGET], so the menu stays internally consistent: a
     * cheaper dish is likelier to be picked, and typing a price into the edit
     * dialog lands on the same number the seed data uses. Change the default
     * budget and the whole seed menu re-derives with it.
     */
    fun defaults(): List<FoodItem> = listOf(
        FoodItem(id = 1, name = "各式美味泡面", price = 2.0),
        FoodItem(id = 2, name = "蛋汁大排面", price = 20.0),
        FoodItem(id = 3, name = "凉皮", price = 8.0),
        FoodItem(id = 4, name = "鑫花溪牛肉米粉", price = 23.0),
        FoodItem(id = 5, name = "港式虾仁滑蛋", price = 30.0),
        FoodItem(id = 6, name = "热干面", price = 12.0),
        FoodItem(id = 7, name = "云南过桥米线", price = 21.0),
        FoodItem(id = 8, name = "汉堡", price = 40.0),
        FoodItem(id = 9, name = "奶茶", price = 15.0),
        FoodItem(id = 10, name = "馄饨", price = 12.0),
        FoodItem(id = 11, name = "火鸡面", price = 15.0)
    ).map { it.copy(weight = FoodStore.weightFromPrice(it.price!!, FoodStore.DEFAULT_BUDGET) ?: 1.0) }
}

/**
 * Process wide, observable list of wheel entries.
 *
 * The wheel screen and the food management screen live on different navigation
 * destinations, so both read and write this single source of truth; every
 * mutation is written through to [FoodPrefs] so it survives process death.
 */
object FoodStore {

    /**
     * Upper bound for a single dish's weight.
     *
     * Without a cap a very cheap dish (a 0.5 yuan sachet of instant noodles)
     * would get a weight so large that the wheel becomes a single slice and the
     * "draw" stops being a draw.
     */
    const val MAX_WEIGHT = 15.0

    /** Default per-meal budget (yuan) used when deriving a weight from a price. */
    const val DEFAULT_BUDGET = 12.0

    val items: SnapshotStateList<FoodItem> = mutableStateListOf()

    private var loaded = false

    /** Loads from disk the first time it is called in this process. */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        items.clear()
        items.addAll(FoodPrefs.load(context))
        loaded = true
    }

    /**
     * Appends a dish to the wheel.
     *
     * [price] is optional: a dish can be added with a hand typed weight alone,
     * in which case the price box was simply left empty.
     */
    fun add(context: Context, name: String, weight: Double, price: Double? = null) {
        val nextId = (items.maxOfOrNull { it.id } ?: 0) + 1
        items.add(FoodItem(id = nextId, name = name, weight = weight, price = price))
        FoodPrefs.save(context, items)
    }

    fun delete(context: Context, id: Int) {
        if (items.removeAll { it.id == id }) {
            FoodPrefs.save(context, items)
        }
    }

    /**
     * Renames an existing dish and/or reweights it.
     *
     * The list is a [SnapshotStateList], so the in place assignment notifies
     * observers (the wheel screen redraws its sectors) without needing the item
     * to be removed and re-added, which would also change its position.
     *
     * [price] is passed explicitly rather than defaulted because "no price" and
     * "leave the price alone" are different outcomes: clearing the price box
     * must be able to drop the stored value.
     */
    fun update(context: Context, id: Int, name: String, weight: Double, price: Double?) {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return
        items[index] = items[index].copy(name = name, weight = weight, price = price)
        FoodPrefs.save(context, items)
    }

    /**
     * Weight implied by a dish's price for a given budget: `budget / price`,
     * capped at [MAX_WEIGHT].
     *
     * This is the inverse of how the seed menu in [FoodPrefs.defaults] was
     * built (an 8 yuan 凉皮 carries 1.47 ≈ 12/8), so editing a dish by price
     * produces weights that sit on the same scale as the shipped ones. Returns
     * null for inputs that cannot produce a meaningful weight, so callers can
     * leave whatever the user already typed untouched instead of overwriting it
     * with a placeholder mid-edit.
     */
    fun weightFromPrice(price: Double, budget: Double): Double? {
        if (price <= 0.0 || price.isNaN() || price.isInfinite()) return null
        if (budget <= 0.0 || budget.isNaN() || budget.isInfinite()) return null
        return (budget / price).coerceIn(0.0, MAX_WEIGHT)
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
     * The wheel is drawn with sector 0 starting at 12 o'clock (-90 degrees) and
     * sweeping clockwise (see `Wheel.drawSectors`), and the pointer is fixed at
     * 12 o'clock (-90 degrees). Rotating the wheel clockwise by R puts the
     * middle of the picked sector at `middle + R`, so we need `R ≡ -90 - middle
     * (mod 360)`. A few whole turns are added on top to keep the spin long
     * enough to feel like a draw.
     *
     * The result is expressed as `base + turns*360 + needed`, where `base` is the
     * largest multiple of 360 not exceeding [current]. Because `base` is already a
     * multiple of 360, `R mod 360 == needed` for *every* spin — not just the
     * first — so the pointer lands on the picked sector regardless of where the
     * wheel currently sits.
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
        val base = current - currentMod // largest multiple of 360 <= current
        val turns = 4 + Random.nextInt(3) // 4-6 whole turns
        return base + turns * 360.0 + needed
    }
}
