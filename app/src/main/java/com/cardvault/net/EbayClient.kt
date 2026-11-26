package com.cardvault.net

import android.util.Base64
import com.cardvault.data.Card
import com.cardvault.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

val http: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()

class ApiException(message: String) : Exception(message)

data class PriceResult(
    val median: Double,
    val sampleSize: Int,
    val low: Double,
    val high: Double,
    val query: String,
)

/**
 * Prices a card from eBay's free Browse API. The Browse API only returns ACTIVE listings,
 * so the result is the median asking price of matching Buy It Now listings, not a sold price.
 */
class EbayClient(private val settings: Settings) {

    private suspend fun token(): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (settings.ebayToken.isNotBlank() && settings.ebayTokenExpires > now + 60_000) {
            return@withContext settings.ebayToken
        }
        if (!settings.hasEbay) throw ApiException("Add your eBay keys in Settings first.")
        val creds = "${settings.ebayClientId}:${settings.ebayClientSecret}"
        val basic = Base64.encodeToString(creds.toByteArray(), Base64.NO_WRAP)
        val body = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .add("scope", "https://api.ebay.com/oauth/api_scope")
            .build()
        val req = Request.Builder()
            .url("https://api.ebay.com/identity/v1/oauth2/token")
            .header("Authorization", "Basic $basic")
            .post(body)
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ApiException("eBay sign-in failed (${resp.code}). Check the eBay keys in Settings.")
            val json = JSONObject(text)
            val tok = json.getString("access_token")
            settings.ebayToken = tok
            settings.ebayTokenExpires = now + json.optLong("expires_in", 7200L) * 1000L
            tok
        }
    }

    /** Lets the Settings screen check the keys. */
    suspend fun testKeys() {
        settings.ebayToken = ""
        token()
    }

    suspend fun price(card: Card): PriceResult? = withContext(Dispatchers.IO) {
        val query = buildQuery(card)
        if (query.isBlank()) throw ApiException("Add a player or description so the card can be priced.")
        val url = "https://api.ebay.com/buy/browse/v1/item_summary/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("category_ids", SPORTS_CARD_SINGLES)
            .addQueryParameter("filter", "buyingOptions:{FIXED_PRICE},priceCurrency:USD")
            .addQueryParameter("limit", "100")
            .build()
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${token()}")
            .header("X-EBAY-C-MARKETPLACE-ID", "EBAY_US")
            .build()
        val listings = http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (resp.code == 401) {
                settings.ebayToken = ""
                throw ApiException("eBay rejected the request. Try again; if it repeats, check the eBay keys.")
            }
            if (!resp.isSuccessful) throw ApiException("eBay search failed (${resp.code}).")
            parseListings(text)
        }
        summarize(PricingRules.filter(card, listings), query)
    }

    private fun parseListings(text: String): List<Listing> {
        val items = JSONObject(text).optJSONArray("itemSummaries") ?: return emptyList()
        val out = ArrayList<Listing>()
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            val title = it.optString("title")
            val price = it.optJSONObject("price")?.optString("value")?.toDoubleOrNull() ?: continue
            if (price <= 0) continue
            out += Listing(title, price)
        }
        return out
    }

    private fun summarize(listings: List<Listing>, query: String): PriceResult? {
        if (listings.isEmpty()) return null
        val prices = PricingRules.trimOutliers(listings.map { it.price }.sorted())
        return PriceResult(
            median = PricingRules.median(prices),
            sampleSize = prices.size,
            low = prices.first(),
            high = prices.last(),
            query = query,
        )
    }

    companion object {
        /** eBay category "Sports Trading Card Singles". */
        const val SPORTS_CARD_SINGLES = "261328"

        fun buildQuery(card: Card): String {
            if (card.searchOverride.isNotBlank()) return card.searchOverride.trim()
            val parts = mutableListOf<String>()
            if (card.year.isNotBlank()) parts += card.year
            if (card.brand.isNotBlank()) parts += card.brand
            if (card.setName.isNotBlank() && !card.setName.equals(card.brand, ignoreCase = true)) parts += card.setName
            if (card.player.isNotBlank()) parts += card.player
            if (card.cardNumber.isNotBlank()) parts += card.cardNumber.trim().removePrefix("#")
            if (card.variation.isNotBlank()) parts += card.variation
            if (card.isGraded) {
                parts += card.grader.ifBlank { "PSA" }
                PricingRules.gradeNumber(card.grade)?.let { parts += it }
            }
            return parts.joinToString(" ").replace(Regex("\\s+"), " ").trim()
        }
    }
}

data class Listing(val title: String, val price: Double)

/** Pure functions so the matching rules are easy to follow and to test. */
object PricingRules {
    private val junk = Regex(
        "\\b(lot|lots|reprint|rp|custom|you pick|u pick|pick your|choose|complete set|team set|proxy|digital|facsimile|break|case hit box)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val gradedMarker = Regex(
        "\\b(psa|bgs|sgc|cgc|bvg|csg|hga|graded|slab|slabbed|beckett \\d)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun gradeNumber(grade: String): String? =
        Regex("(10|[1-9](\\.5)?)").findAll(grade).lastOrNull()?.value

    fun filter(card: Card, listings: List<Listing>): List<Listing> {
        var out = listings.filter { !junk.containsMatchIn(it.title) }

        if (card.searchOverride.isBlank()) {
            val lastName = card.player.trim().split(Regex("\\s+")).lastOrNull()
                ?.takeIf { it.length >= 2 }
            if (lastName != null) {
                out = out.filter { it.title.contains(lastName, ignoreCase = true) }
            }
        }

        out = if (card.isGraded) {
            val grader = Regex.escape(card.grader.ifBlank { "PSA" })
            val num = gradeNumber(card.grade)
            if (num != null) {
                val rx = Regex("\\b$grader\\b[^0-9]{0,16}${Regex.escape(num)}(?![0-9.])", RegexOption.IGNORE_CASE)
                out.filter { rx.containsMatchIn(it.title) }
            } else out
        } else {
            out.filter { !gradedMarker.containsMatchIn(it.title) }
        }

        // Prefer listings that mention the card number, when enough of them do.
        val num = card.cardNumber.trim().removePrefix("#").trimStart('0')
        if (num.isNotEmpty()) {
            val rx = if (num.length >= 2)
                Regex("(#\\s*0*${Regex.escape(num)}\\b)|(\\b${Regex.escape(num)}\\b)", RegexOption.IGNORE_CASE)
            else
                Regex("#\\s*0*${Regex.escape(num)}\\b|\\bno\\.?\\s*${Regex.escape(num)}\\b", RegexOption.IGNORE_CASE)
            val withNum = out.filter { rx.containsMatchIn(it.title) }
            if (withNum.size >= 3) out = withNum
        }
        return out
    }

    fun median(sorted: List<Double>): Double {
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }

    /** Drops prices far outside the typical range (1.5x interquartile range). */
    fun trimOutliers(sorted: List<Double>): List<Double> {
        if (sorted.size < 5) return sorted
        val q1 = sorted[sorted.size / 4]
        val q3 = sorted[(sorted.size * 3) / 4]
        val iqr = q3 - q1
        val lo = q1 - 1.5 * iqr
        val hi = q3 + 1.5 * iqr
        val kept = sorted.filter { it in lo..hi }
        return kept.ifEmpty { sorted }
    }
}
