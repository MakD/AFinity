package com.makd.afinity.data.database.dao

import androidx.room.*
import com.makd.afinity.data.models.user.User
import java.util.UUID

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>)

    @Update
    suspend fun updateUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUserById(userId: UUID)

    @Query("DELETE FROM users WHERE serverId = :serverId")
    suspend fun deleteUsersByServerId(serverId: String)

    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUser(userId: UUID): User?

    @Query("SELECT * FROM users WHERE serverId = :serverId")
    suspend fun getUsersForServer(serverId: String): List<User>

    @Query("SELECT * FROM users WHERE name = :username AND serverId = :serverId")
    suspend fun getUserByName(username: String, serverId: String): User?

    @Query("SELECT * FROM users WHERE accessToken IS NOT NULL LIMIT 1")
    suspend fun getCurrentUser(): User?

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User>
}