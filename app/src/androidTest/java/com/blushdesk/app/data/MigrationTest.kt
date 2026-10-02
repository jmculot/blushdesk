package com.blushdesk.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.blushdesk.app.data.local.database.AppDatabase
import com.blushdesk.app.data.local.database.MIGRATION_1_2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Builds a version-1 database from the exported 1.json schema, fills it the way the first release
 * stored data, runs [MIGRATION_1_2] and lets Room validate the result against 2.json. Then checks
 * every renamed or derived value survived.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val dbName = "migration-test.db"

    @Test
    fun migrate_1_to_2_keeps_and_renames_all_data() {
        helper.createDatabase(dbName, 1).apply {
            execSQL(
                "INSERT INTO operator_profile (id, fullName, storeName, email, phone, photoPath) " +
                    "VALUES (1, 'Lia Santos', 'Rosé Showroom', 'lia@rose.example', '0917 555 0100', '/data/photos/lia.jpg')",
            )
            execSQL(
                "INSERT INTO buyers (id, fullName, contact, email, dateAdded, photoPath) " +
                    "VALUES (7, 'Ana Reyes', '0917 123 4567', 'ana@example.com', 1759276800000, NULL)",
            )
            execSQL(
                "INSERT INTO orders (id, buyerId, productName, unitPriceMinor, quantity, purchasedAt, paymentMode, paymentStatus, orderStatus) " +
                    "VALUES (42, 7, 'Velvet Sofa', 99999, 3, 1759363200000, 'ONLINE', 'PENDING', 'PREPARING')",
            )
            execSQL(
                "INSERT INTO orders (id, buyerId, productName, unitPriceMinor, quantity, purchasedAt, paymentMode, paymentStatus, orderStatus) " +
                    "VALUES (43, 7, 'Lamp', 5000, 1, 1759363300000, 'CASH', 'PAID', 'DELIVERED')",
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)

        db.query("SELECT fullName, phoneNumber, profileImageUri, createdAt, updatedAt FROM operator_profile").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Lia Santos", c.getString(0))
            assertEquals("0917 555 0100", c.getString(1))
            assertEquals("file:///data/photos/lia.jpg", c.getString(2))
            assertTrue(c.getLong(3) > 0 && c.getLong(3) == c.getLong(4))
        }

        db.query("SELECT id, contactNumber, profileImageUri, dateAdded, createdAt, updatedAt FROM buyers").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(7L, c.getLong(0))
            assertEquals("0917 123 4567", c.getString(1))
            assertTrue(c.isNull(2))
            assertEquals(1759276800000L, c.getLong(3))
            assertEquals(1759276800000L, c.getLong(4)) // createdAt starts from dateAdded
            assertEquals(1759276800000L, c.getLong(5))
        }

        db.query(
            "SELECT id, unitPrice, quantity, totalAmount, purchaseDateTime, paymentMode, paymentStatus, fulfillmentStatus, createdAt " +
                "FROM orders ORDER BY id",
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(42L, c.getLong(0))
            assertEquals(99999L, c.getLong(1)) // centavos unchanged
            assertEquals(3, c.getInt(2))
            assertEquals(299997L, c.getLong(3)) // total = 999.99 x 3, computed during the migration
            assertEquals(1759363200000L, c.getLong(4))
            assertEquals("ONLINE_PAYMENT", c.getString(5)) // renamed enum value
            assertEquals("PENDING", c.getString(6))
            assertEquals("PREPARING", c.getString(7))
            assertEquals(1759363200000L, c.getLong(8))

            assertTrue(c.moveToNext())
            assertEquals("CASH", c.getString(5))
            assertEquals("DELIVERED", c.getString(7))
        }

        // The foreign key still cascades after the rebuild.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM buyers WHERE id = 7")
        db.query("SELECT COUNT(*) FROM orders").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun migrating_an_empty_database_works() {
        helper.createDatabase(dbName, 1).close()
        val db = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)
        db.query("SELECT COUNT(*) FROM buyers").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        db.query("SELECT * FROM operator_profile").use { c -> assertEquals(0, c.count) }
        db.close()
    }
}
