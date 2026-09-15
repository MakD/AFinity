package com.makd.afinity.data.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AfinityMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AfinityDatabase::class.java,
        )

    @Test
    @Throws(IOException::class)
    fun currentSchemaOpensWithAllMigrationsRegistered() {
        helper.createDatabase(TEST_DB, AFINITY_DB_VERSION).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db =
            Room.databaseBuilder(context, AfinityDatabase::class.java, TEST_DB)
                .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                .build()

        assertTrue(db.openHelper.writableDatabase.isOpen)
        db.close()
    }

    private companion object {
        const val TEST_DB = "afinity-migration-test"
    }
}
