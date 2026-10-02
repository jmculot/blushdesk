package com.blushdesk.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.blushdesk.app.data.local.entity.Buyer
import com.blushdesk.app.data.local.entity.OperatorProfile
import com.blushdesk.app.data.local.entity.Order

@Database(
    entities = [OperatorProfile::class, Buyer::class, Order::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ShowroomDatabase : RoomDatabase() {

    abstract fun dao(): ShowroomDao

    companion object {
        const val FILE_NAME = "showroom.db"

        /**
         * No destructive-migration fallback on purpose: this is the shop's only copy of its
         * records, so a missing migration should fail loudly in development, not wipe data.
         */
        fun create(context: Context): ShowroomDatabase =
            Room.databaseBuilder(context.applicationContext, ShowroomDatabase::class.java, FILE_NAME).build()
    }
}
