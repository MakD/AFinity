package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.makd.afinity.data.database.entities.ItemPeopleCrossRef
import com.makd.afinity.data.database.entities.PersonEntity
import java.util.UUID

@Dao
interface PeopleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: PersonEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeople(people: List<PersonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemPeopleCrossRef(crossRef: ItemPeopleCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemPeopleCrossRefs(crossRefs: List<ItemPeopleCrossRef>)

    @Query("SELECT * FROM people WHERE id = :personId")
    suspend fun getPerson(personId: String): PersonEntity?

    @Query("""
        SELECT p.* FROM people p
        INNER JOIN item_people ip ON p.id = ip.personId
        WHERE ip.itemId = :itemId
        ORDER BY ip.sortOrder ASC
    """)
    suspend fun getPeopleForItem(itemId: UUID): List<PersonEntity>

    @Query("""
        SELECT * FROM item_people
        WHERE itemId = :itemId
        ORDER BY sortOrder ASC
    """)
    suspend fun getItemPeopleCrossRefs(itemId: UUID): List<ItemPeopleCrossRef>

    @Query("DELETE FROM item_people WHERE itemId = :itemId")
    suspend fun deleteItemPeopleLinks(itemId: UUID)

    @Query("DELETE FROM people WHERE id NOT IN (SELECT DISTINCT personId FROM item_people)")
    suspend fun deleteOrphanedPeople()

    @Transaction
    suspend fun insertPeopleForItem(itemId: UUID, people: List<PersonEntity>, crossRefs: List<ItemPeopleCrossRef>) {
        insertPeople(people)
        deleteItemPeopleLinks(itemId)
        insertItemPeopleCrossRefs(crossRefs)
    }
}