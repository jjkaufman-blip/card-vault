package com.cardvault.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backs up the whole inventory (cards, price history and photos) to a single .zip
 * file wherever he picks: phone storage, a USB drive, Google Drive, etc.
 */
class Backup(private val context: Context, private val repo: Repository) {

    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val cards = repo.allCards()
        val history = repo.allHistory()
        val out = context.contentResolver.openOutputStream(uri) ?: error("Couldn't open the backup file")
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("cards.json"))
            zip.write(JSONArray(cards.map { it.toJson() }).toString().toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("history.json"))
            zip.write(JSONArray(history.map { it.toJson() }).toString().toByteArray())
            zip.closeEntry()

            cards.flatMap { listOfNotNull(it.frontImage, it.backImage) }
                .map { File(it) }
                .filter { it.exists() }
                .forEach { f ->
                    zip.putNextEntry(ZipEntry("images/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
        cards.size
    }

    /** Replaces the current inventory with the backup's contents. */
    suspend fun import(uri: Uri): Int = withContext(Dispatchers.IO) {
        var cardsJson: String? = null
        var historyJson: String? = null
        val imageDir = repo.imageDir
        val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open the backup file")
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == "cards.json" -> cardsJson = zip.readBytes().decodeToString()
                    entry.name == "history.json" -> historyJson = zip.readBytes().decodeToString()
                    entry.name.startsWith("images/") && !entry.isDirectory -> {
                        val name = File(entry.name).name // strips any path tricks
                        File(imageDir, name).outputStream().use { zip.copyTo(it) }
                    }
                }
                entry = zip.nextEntry
            }
        }
        val cardsText = cardsJson ?: error("This file isn't a Card Vault backup")
        val cards = JSONArray(cardsText).let { arr ->
            (0 until arr.length()).map { cardFromJson(arr.getJSONObject(it), imageDir) }
        }
        val history = historyJson?.let { text ->
            val arr = JSONArray(text)
            (0 until arr.length()).map { pointFromJson(arr.getJSONObject(it)) }
        } ?: emptyList()
        repo.replaceAll(cards, history)
        cards.size
    }
}

private fun Card.toJson() = JSONObject().apply {
    put("id", id); put("player", player); put("year", year); put("brand", brand)
    put("setName", setName); put("cardNumber", cardNumber); put("team", team)
    put("position", position); put("variation", variation); put("isGraded", isGraded)
    put("grader", grader); put("grade", grade); put("certNumber", certNumber)
    put("frontImage", frontImage?.let { File(it).name } ?: JSONObject.NULL)
    put("backImage", backImage?.let { File(it).name } ?: JSONObject.NULL)
    put("marketValue", marketValue ?: JSONObject.NULL)
    put("marketSampleSize", marketSampleSize)
    put("marketLow", marketLow ?: JSONObject.NULL)
    put("marketHigh", marketHigh ?: JSONObject.NULL)
    put("lastPriced", lastPriced ?: JSONObject.NULL)
    put("manualValue", manualValue ?: JSONObject.NULL)
    put("searchOverride", searchOverride); put("notes", notes); put("createdAt", createdAt)
}

private fun JSONObject.optDoubleOrNull(k: String): Double? = if (isNull(k) || !has(k)) null else optDouble(k)
private fun JSONObject.optLongOrNull(k: String): Long? = if (isNull(k) || !has(k)) null else optLong(k)
private fun JSONObject.optStringOrNull(k: String): String? = if (isNull(k) || !has(k)) null else optString(k)

private fun cardFromJson(o: JSONObject, imageDir: File) = Card(
    id = o.optLong("id"),
    player = o.optString("player"), year = o.optString("year"), brand = o.optString("brand"),
    setName = o.optString("setName"), cardNumber = o.optString("cardNumber"), team = o.optString("team"),
    position = o.optString("position"), variation = o.optString("variation"),
    isGraded = o.optBoolean("isGraded"), grader = o.optString("grader"), grade = o.optString("grade"),
    certNumber = o.optString("certNumber"),
    frontImage = o.optStringOrNull("frontImage")?.let { File(imageDir, it).absolutePath },
    backImage = o.optStringOrNull("backImage")?.let { File(imageDir, it).absolutePath },
    marketValue = o.optDoubleOrNull("marketValue"),
    marketSampleSize = o.optInt("marketSampleSize"),
    marketLow = o.optDoubleOrNull("marketLow"),
    marketHigh = o.optDoubleOrNull("marketHigh"),
    lastPriced = o.optLongOrNull("lastPriced"),
    manualValue = o.optDoubleOrNull("manualValue"),
    searchOverride = o.optString("searchOverride"), notes = o.optString("notes"),
    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
)

private fun PricePoint.toJson() = JSONObject().apply {
    put("id", id); put("cardId", cardId); put("day", day); put("value", value)
    put("source", source); put("sampleSize", sampleSize)
}

private fun pointFromJson(o: JSONObject) = PricePoint(
    id = o.optLong("id"), cardId = o.optLong("cardId"), day = o.optLong("day"),
    value = o.optDouble("value"), source = o.optString("source"), sampleSize = o.optInt("sampleSize"),
)
