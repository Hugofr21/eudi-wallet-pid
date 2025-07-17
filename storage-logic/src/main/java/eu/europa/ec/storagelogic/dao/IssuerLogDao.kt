package eu.europa.ec.storagelogic.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import eu.europa.ec.storagelogic.dao.type.StorageDao
import eu.europa.ec.storagelogic.model.IssuerLog

@Dao
interface IssuerLogDao : StorageDao<IssuerLog> {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    override suspend fun store(value: IssuerLog)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    override suspend fun storeAll(values: List<IssuerLog>)

    @Query("SELECT * FROM issuerLogs WHERE identifier = :identifier")
    override suspend fun retrieve(identifier: String): IssuerLog?

    @Query("SELECT * FROM issuerLogs")
    override suspend fun retrieveAll(): List<IssuerLog>

    @Update
    override suspend fun update(value: IssuerLog)

    @Query("DELETE FROM issuerLogs WHERE identifier = :identifier")
    override suspend fun delete(identifier: String)

    @Query("DELETE FROM issuerLogs")
    override suspend fun deleteAll()
}