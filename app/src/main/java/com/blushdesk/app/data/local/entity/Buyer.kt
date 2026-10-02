package com.blushdesk.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "buyers",
    indices = [Index("fullName")],
)
data class Buyer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    /** Phone number or other way to reach the buyer. */
    val contact: String,
    val email: String = "",
    val dateAdded: Instant,
    val photoPath: String? = null,
)
