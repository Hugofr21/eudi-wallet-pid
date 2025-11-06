package eu.europa.ec.storagelogic.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import eu.europa.ec.storagelogic.dao.type.StorageDao
import eu.europa.ec.storagelogic.model.Connection

@Dao
interface ConnectionDao : StorageDao<Connection> {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    override suspend fun store(value: Connection)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    override suspend fun storeAll(values: List<Connection>)

    @Query("SELECT * FROM connections WHERE peerDid = :identifier")
    override suspend fun retrieve(identifier: String): Connection?

    @Query("SELECT * FROM connections")
    override suspend fun retrieveAll(): List<Connection>

    @Update
    override suspend fun update(value: Connection)

    @Query("DELETE FROM connections WHERE peerDid = :identifier")
    override suspend fun delete(identifier: String)

    @Query("DELETE FROM connections")
    override suspend fun deleteAll()
}