package com.cardvault.net

import com.cardvault.data.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class PsaCert(
    val certNumber: String,
    val year: String,
    val brand: String,
    val player: String,
    val cardNumber: String,
    val variety: String,
    val grade: String,
    val category: String,
)

/** PSA's free public API: looks up a graded slab by its cert number. */
class PsaClient(private val settings: Settings) {

    suspend fun lookup(certNumber: String): PsaCert = withContext(Dispatchers.IO) {
        if (!settings.hasPsa) throw ApiException("Add your PSA token in Settings to look up slabs.")
        val cert = certNumber.filter { it.isDigit() }
        if (cert.isEmpty()) throw ApiException("That doesn't look like a PSA cert number.")
        val req = Request.Builder()
            .url("https://api.psacard.com/publicapi/cert/GetByCertNumber/$cert")
            .header("Authorization", "bearer ${settings.psaToken}")
            .build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            when {
                resp.code == 401 || resp.code == 403 -> throw ApiException("PSA rejected the token. Check it in Settings.")
                resp.code == 429 -> throw ApiException("PSA's daily lookup limit was reached. Try again tomorrow.")
                !resp.isSuccessful -> throw ApiException("PSA lookup failed (${resp.code}).")
            }
            val root = JSONObject(text)
            val c = root.optJSONObject("PSACert") ?: root
            if (c.optString("CertNumber").isBlank() && c.optString("Subject").isBlank()) {
                throw ApiException("PSA has no record of cert $cert.")
            }
            PsaCert(
                certNumber = c.optString("CertNumber", cert),
                year = c.optString("Year"),
                brand = c.optString("Brand"),
                player = c.optString("Subject").toTitleCase(),
                cardNumber = c.optString("CardNumber"),
                variety = c.optString("Variety"),
                grade = c.optString("CardGrade").ifBlank { c.optString("GradeDescription") },
                category = c.optString("Category"),
            )
        }
    }
}

private fun String.toTitleCase(): String =
    lowercase().split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
