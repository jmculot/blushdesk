package com.blushdesk.app.domain.model

import com.blushdesk.app.data.local.database.BuyerWithOrders
import com.blushdesk.app.data.local.database.ExportSummary
import com.blushdesk.app.data.local.database.OperatorProfile
import java.time.Instant

/**
 * Everything the Excel export writes, read in one database transaction so the Summary sheet's
 * figures always agree with the Buyers and Orders sheets.
 */
data class ExportSnapshot(
    val operator: OperatorProfile,
    val buyers: List<BuyerWithOrders>,
    val summary: ExportSummary,
    val takenAt: Instant,
)
