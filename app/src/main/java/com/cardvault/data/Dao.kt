package com.cardvault.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards")
    fun observeAll(): Flow<List<Card>>

    @Query("SELECT * FROM cards")
    suspend fun getAll(): List<Card>

    @Query("SELECT * FROM cards WHERE id = :id")
    fun observe(id: Long): Flow<Card?>

    @Query("SELECT * FROM cards WHERE id = :id")
    suspend fun get(id: Long): Card?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: Card): Long

    @Update
    suspend fun update(card: Card)

    @Delete
    suspend fun delete(card: Card)

    @Query("DELETE FROM cards")
    suspend fun deleteAll()

    @Query("SELECT * FROM price_points WHERE cardId = :cardId ORDER BY day ASC")
    fun observeHistory(cardId: Long): Flow<List<PricePoint>>

    @Query("SELECT * FROM price_points")
    suspend fun getAllHistory(): List<PricePoint>

    @Query("SELECT * FROM price_points WHERE cardId = :cardId AND day = :day LIMIT 1")
    suspend fun pointOn(cardId: Long, day: Long): PricePoint?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: PricePoint): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoints(points: List<PricePoint>)

    @Query("DELETE FROM price_points WHERE cardId = :cardId AND source = 'manual'")
    suspend fun deleteManualPoints(cardId: Long)
}
