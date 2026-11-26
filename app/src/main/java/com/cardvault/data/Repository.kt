package com.cardvault.data

import android.content.Context
import androidx.room.withTransaction
import com.cardvault.net.EbayClient
import com.cardvault.net.PsaClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.LocalDate

class Repository(
    private val context: Context,
    private val db: AppDatabase,
    val settings: Settings,
) {
    private val dao = db.cards()
    val ebay = EbayClient(settings)
    val psa = PsaClient(settings)

    val imageDir: File get() = File(context.filesDir, "images").apply { mkdirs() }

    fun observeCards(): Flow<List<Card>> = dao.observeAll()
    fun observeCard(id: Long): Flow<Card?> = dao.observe(id)
    fun observeHistory(id: Long): Flow<List<PricePoint>> = dao.observeHistory(id)
    suspend fun getCard(id: Long): Card? = dao.get(id)

    private fun today(): Long = LocalDate.now().toEpochDay()

    /** Saves a card; returns its id. Records a manual value in the price history. */
    suspend fun save(card: Card): Long {
        val old = if (card.id != 0L) dao.get(card.id) else null
        val id = if (card.id == 0L) dao.insert(card) else { dao.update(card); card.id }

        // Remove photos that were replaced.
        old?.frontImage?.takeIf { it != card.frontImage }?.let { File(it).delete() }
        old?.backImage?.takeIf { it != card.backImage }?.let { File(it).delete() }

        val manual = card.manualValue
        if (manual != null && manual != old?.manualValue) {
            dao.insertPoint(PricePoint(cardId = id, day = today(), value = manual, source = "manual"))
        }
        return id
    }

    suspend fun delete(card: Card) {
        card.frontImage?.let { File(it).delete() }
        card.backImage?.let { File(it).delete() }
        dao.delete(card)
    }

    /** Re-prices one card from eBay and logs today's value. Returns the updated card. */
    suspend fun refreshPrice(cardId: Long): Card {
        val card = dao.get(cardId) ?: throw IllegalStateException("Card not found")
        val result = ebay.price(card)
        val now = System.currentTimeMillis()
        val updated = if (result == null) {
            card.copy(lastPriced = now, marketSampleSize = 0)
        } else {
            card.copy(
                marketValue = result.median,
                marketSampleSize = result.sampleSize,
                marketLow = result.low,
                marketHigh = result.high,
                lastPriced = now,
            )
        }
        dao.update(updated)
        if (result != null) {
            val existing = dao.pointOn(cardId, today())
            if (existing == null || existing.source != "manual") {
                dao.insertPoint(
                    PricePoint(
                        id = existing?.id ?: 0,
                        cardId = cardId,
                        day = today(),
                        value = result.median,
                        source = "ebay",
                        sampleSize = result.sampleSize,
                    )
                )
            }
        }
        return updated
    }

    /**
     * Re-prices every card, a little at a time to stay well inside eBay's free
     * 5,000-calls-a-day limit. Returns (priced, failed).
     */
    suspend fun refreshAll(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Pair<Int, Int> {
        val cards = dao.getAll()
        var ok = 0
        var failed = 0
        cards.forEachIndexed { i, card ->
            try {
                refreshPrice(card.id)
                ok++
            } catch (e: Exception) {
                failed++
                if (e is com.cardvault.net.ApiException && e.message?.contains("keys") == true) throw e
            }
            onProgress(i + 1, cards.size)
            delay(400)
        }
        settings.lastRefreshAll = System.currentTimeMillis()
        return ok to failed
    }

    suspend fun allCards(): List<Card> = dao.getAll()
    suspend fun allHistory(): List<PricePoint> = dao.getAllHistory()

    suspend fun replaceAll(cards: List<Card>, history: List<PricePoint>) {
        db.withTransaction {
            dao.deleteAll()
            cards.forEach { dao.insert(it) }
            dao.insertPoints(history)
        }
    }
}
