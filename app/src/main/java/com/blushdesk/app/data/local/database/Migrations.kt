package com.blushdesk.app.data.local.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Version 1 -> 2: columns renamed to the specification's names (contact -> contactNumber,
 * phone -> phoneNumber, photoPath -> profileImageUri, unitPriceMinor -> unitPrice,
 * purchasedAt -> purchaseDateTime, orderStatus -> fulfillmentStatus), a stored totalAmount,
 * createdAt / updatedAt on every table, and the payment mode ONLINE renamed ONLINE_PAYMENT.
 *
 * SQLite cannot rename or add NOT NULL columns in place on API 29, so each table is rebuilt:
 * create the new shape, copy the rows across, drop the old table, rename. Room runs migrations
 * before it switches foreign keys on, so dropping `buyers` cannot cascade into `orders`, and it
 * checks the foreign keys afterwards.
 *
 * Old photo paths become file:// URIs. createdAt / updatedAt start from the best timestamp each
 * row already had (dateAdded, purchase time); the operator row, which had none, gets the
 * migration time.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()

        db.execSQL(
            """
            CREATE TABLE `operator_profile_new` (
                `id` INTEGER NOT NULL, `fullName` TEXT NOT NULL, `storeName` TEXT NOT NULL,
                `email` TEXT NOT NULL, `phoneNumber` TEXT NOT NULL, `profileImageUri` TEXT,
                `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `operator_profile_new`
                (id, fullName, storeName, email, phoneNumber, profileImageUri, createdAt, updatedAt)
            SELECT id, fullName, storeName, email, phone,
                   CASE WHEN photoPath IS NULL THEN NULL ELSE 'file://' || photoPath END,
                   $now, $now
            FROM `operator_profile`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `operator_profile`")
        db.execSQL("ALTER TABLE `operator_profile_new` RENAME TO `operator_profile`")

        db.execSQL(
            """
            CREATE TABLE `buyers_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `fullName` TEXT NOT NULL,
                `contactNumber` TEXT NOT NULL, `email` TEXT NOT NULL, `dateAdded` INTEGER NOT NULL,
                `profileImageUri` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `buyers_new`
                (id, fullName, contactNumber, email, dateAdded, profileImageUri, createdAt, updatedAt)
            SELECT id, fullName, contact, email, dateAdded,
                   CASE WHEN photoPath IS NULL THEN NULL ELSE 'file://' || photoPath END,
                   dateAdded, dateAdded
            FROM `buyers`
            """.trimIndent(),
        )

        // References `buyers` by its final name, which exists again once buyers_new is renamed.
        db.execSQL(
            """
            CREATE TABLE `orders_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `buyerId` INTEGER NOT NULL,
                `productName` TEXT NOT NULL, `unitPrice` INTEGER NOT NULL, `quantity` INTEGER NOT NULL,
                `totalAmount` INTEGER NOT NULL, `purchaseDateTime` INTEGER NOT NULL,
                `paymentMode` TEXT NOT NULL, `paymentStatus` TEXT NOT NULL, `fulfillmentStatus` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`buyerId`) REFERENCES `buyers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `orders_new`
                (id, buyerId, productName, unitPrice, quantity, totalAmount, purchaseDateTime,
                 paymentMode, paymentStatus, fulfillmentStatus, createdAt, updatedAt)
            SELECT id, buyerId, productName, unitPriceMinor, quantity, unitPriceMinor * quantity, purchasedAt,
                   CASE paymentMode WHEN 'ONLINE' THEN 'ONLINE_PAYMENT' ELSE paymentMode END,
                   paymentStatus, orderStatus, purchasedAt, purchasedAt
            FROM `orders`
            """.trimIndent(),
        )

        db.execSQL("DROP TABLE `orders`")
        db.execSQL("DROP TABLE `buyers`")
        db.execSQL("ALTER TABLE `buyers_new` RENAME TO `buyers`")
        db.execSQL("ALTER TABLE `orders_new` RENAME TO `orders`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_buyers_fullName` ON `buyers` (`fullName`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_buyerId` ON `orders` (`buyerId`)")
    }
}

/**
 * Version 2 -> 3: an order can hold several products. The product columns move out of `orders`
 * into a new `order_items` table; every existing order becomes an order with one item, so nothing
 * is lost and every total stays the same.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `orders_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `buyerId` INTEGER NOT NULL,
                `totalAmount` INTEGER NOT NULL, `purchaseDateTime` INTEGER NOT NULL,
                `paymentMode` TEXT NOT NULL, `paymentStatus` TEXT NOT NULL, `fulfillmentStatus` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL,
                FOREIGN KEY(`buyerId`) REFERENCES `buyers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `orders_new`
                (id, buyerId, totalAmount, purchaseDateTime, paymentMode, paymentStatus, fulfillmentStatus, createdAt, updatedAt)
            SELECT id, buyerId, totalAmount, purchaseDateTime, paymentMode, paymentStatus, fulfillmentStatus, createdAt, updatedAt
            FROM `orders`
            """.trimIndent(),
        )

        // References `orders` by its final name, which the renamed orders_new takes below.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `order_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `orderId` INTEGER NOT NULL,
                `position` INTEGER NOT NULL, `productName` TEXT NOT NULL, `unitPrice` INTEGER NOT NULL,
                `quantity` INTEGER NOT NULL, `lineTotal` INTEGER NOT NULL,
                FOREIGN KEY(`orderId`) REFERENCES `orders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `order_items` (orderId, position, productName, unitPrice, quantity, lineTotal)
            SELECT id, 0, productName, unitPrice, quantity, totalAmount FROM `orders`
            """.trimIndent(),
        )

        db.execSQL("DROP TABLE `orders`")
        db.execSQL("ALTER TABLE `orders_new` RENAME TO `orders`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_orders_buyerId` ON `orders` (`buyerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_items_orderId` ON `order_items` (`orderId`)")
    }
}
