package com.makd.afinity.data.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationChainTest {

    @Test
    fun chainIsContiguousAndReachesCurrentVersion() {
        val steps =
            DatabaseMigrations.ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
                .sortedBy { it.first }

        assertTrue("No migrations registered in ALL_MIGRATIONS", steps.isNotEmpty())

        var expected = steps.first().first
        for ((start, end) in steps) {
            assertEquals("Gap or overlap in the migration chain near $start..$end", expected, start)
            expected = end
        }

        assertEquals(
            "Migration chain ends at $expected but the database version is $AFINITY_DB_VERSION",
            AFINITY_DB_VERSION,
            expected,
        )
    }

    @Test
    fun everyMigrationAdvancesExactlyOneVersion() {
        DatabaseMigrations.ALL_MIGRATIONS.forEach { migration ->
            assertEquals(
                "Migration ${migration.startVersion}..${migration.endVersion} skips a version",
                migration.startVersion + 1,
                migration.endVersion,
            )
        }
    }

    @Test
    fun noDuplicateMigrationsAreRegistered() {
        val keys = DatabaseMigrations.ALL_MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals(
            "ALL_MIGRATIONS contains duplicate entries",
            keys.size,
            keys.toSet().size,
        )
    }
}
