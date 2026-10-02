package com.numbered.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.DayOfWeek
import java.time.LocalDate

@Database(
    entities = [Profile::class, Commitment::class, SomedayItem::class, WeekReview::class, Chapter::class],
    version = NumberedDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class NumberedDatabase : RoomDatabase() {
    abstract fun profiles(): ProfileDao
    abstract fun commitments(): CommitmentDao
    abstract fun someday(): SomedayDao
    abstract fun reviews(): WeekReviewDao
    abstract fun chapters(): ChapterDao

    companion object {
        const val FILE_NAME = "numbered.db"
        const val VERSION = 2

        fun open(context: Context, name: String = FILE_NAME): NumberedDatabase =
            Room.databaseBuilder(context, NumberedDatabase::class.java, name).addMigrations(*MIGRATIONS).build()

        fun inMemory(context: Context): NumberedDatabase =
            Room.inMemoryDatabaseBuilder(context, NumberedDatabase::class.java).build()
    }
}

class Converters {
    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun dayOfWeekToIso(day: DayOfWeek?): Int? = day?.value

    @TypeConverter
    fun isoToDayOfWeek(value: Int?): DayOfWeek? = value?.let(DayOfWeek::of)
}
