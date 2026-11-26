package com.cardvault.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val player: String = "",
    val year: String = "",
    val brand: String = "",
    val setName: String = "",
    val cardNumber: String = "",
    val team: String = "",
    val position: String = "",
    val variation: String = "",
    val isGraded: Boolean = false,
    val grader: String = "",
    val grade: String = "",
    val certNumber: String = "",
    val frontImage: String? = null,
    val backImage: String? = null,
    /** Estimated value from eBay asking prices (median of matching listings). */
    val marketValue: Double? = null,
    val marketSampleSize: Int = 0,
    val marketLow: Double? = null,
    val marketHigh: Double? = null,
    val lastPriced: Long? = null,
    /** A value he typed in (e.g. a sold price he looked up). Overrides marketValue when set. */
    val manualValue: Double? = null,
    /** Custom eBay search text; when blank the app builds one from the card's details. */
    val searchOverride: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

/** The value shown in the app: his typed-in value if set, otherwise the eBay estimate. */
val Card.value: Double? get() = manualValue ?: marketValue

val Card.title: String
    get() = listOf(year, brand, setName.takeIf { it != brand }, player)
        .filter { !it.isNullOrBlank() }
        .joinToString(" ")
        .ifBlank { "Unidentified card" }

val Card.gradeLabel: String
    get() = if (isGraded) listOf(grader.ifBlank { "PSA" }, grade).filter { it.isNotBlank() }.joinToString(" ") else "Raw"

@Entity(
    tableName = "price_points",
    foreignKeys = [ForeignKey(
        entity = Card::class,
        parentColumns = ["id"],
        childColumns = ["cardId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("cardId")],
)
data class PricePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: Long,
    /** Day number (epoch day) so there is at most one point per card per day. */
    val day: Long,
    val value: Double,
    val source: String, // "ebay" or "manual"
    val sampleSize: Int = 0,
)
