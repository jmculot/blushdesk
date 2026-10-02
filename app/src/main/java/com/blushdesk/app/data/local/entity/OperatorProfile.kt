package com.blushdesk.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The person running the showroom tablet. There is exactly one row (id [SINGLETON_ID]); writing
 * it again replaces it. Receipts and the Excel summary take their branding from this record.
 */
@Entity(tableName = "operator_profile")
data class OperatorProfile(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val fullName: String = "",
    val storeName: String = "",
    val email: String = "",
    val phone: String = "",
    /** Absolute path of a copy inside the app's private storage, never a transient picker URI. */
    val photoPath: String? = null,
) {
    /** False until the operator has filled in the two fields every receipt needs. */
    val isSetUp: Boolean get() = fullName.isNotBlank() && storeName.isNotBlank()

    companion object {
        const val SINGLETON_ID = 1
    }
}
