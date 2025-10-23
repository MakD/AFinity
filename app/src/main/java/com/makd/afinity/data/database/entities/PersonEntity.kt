package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.media.AfinityPerson
import java.util.UUID

@Entity(tableName = "people")
data class PersonEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val role: String?,
    val imageUrl: String?,
    val imageBlurHash: String?
)

@Entity(
    tableName = "item_people",
    primaryKeys = ["itemId", "personId"],
    foreignKeys = [
        ForeignKey(
            entity = PersonEntity::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["personId"])]
)
data class ItemPeopleCrossRef(
    val itemId: UUID,
    val personId: String,
    val role: String?,
    val character: String?,
    val sortOrder: Int
)

data class ItemWithPeople(
    val itemId: UUID,
    val people: List<PersonWithRole>
)

data class PersonWithRole(
    val person: PersonEntity,
    val role: String?,
    val character: String?,
    val sortOrder: Int
)

fun AfinityPerson.toPersonEntity(): PersonEntity {
    return PersonEntity(
        id = this.name,
        name = this.name,
        role = this.role,
        imageUrl = this.image.uri?.toString(),
        imageBlurHash = this.image.blurHash
    )
}

fun List<AfinityPerson>.toItemPeopleCrossRefs(itemId: UUID): List<ItemPeopleCrossRef> {
    return this.mapIndexed { index, person ->
        ItemPeopleCrossRef(
            itemId = itemId,
            personId = person.name,
            role = person.role,
            character = null,
            sortOrder = index
        )
    }
}