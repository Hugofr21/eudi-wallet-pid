package eu.europa.ec.storagelogic.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import eu.europa.ec.storagelogic.dao.type.StorageDao
import eu.europa.ec.storagelogic.model.DidEntity
import eu.europa.ec.storagelogic.model.DidKeyMapping

@Dao
interface ConnectionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDid(did: DidEntity)

    @Query("SELECT * FROM dids WHERE peerDid = :identifier")
    suspend fun getDid(identifier: String): DidEntity?

    @Query("SELECT * FROM dids")
    suspend fun getAllDids(): List<DidEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeys(keys: List<DidKeyMapping>)


    @Query("SELECT keystoreAlias FROM did_keys WHERE keyId = :keyId")
    suspend fun getAliasByKeyId(keyId: String): String?


    @Query("SELECT keystoreAlias FROM did_keys WHERE did = :did AND purpose = 'AUTHENTICATION' LIMIT 1")
    suspend fun getSigningAliasForDid(did: String): String?


    @Query("SELECT * FROM did_keys WHERE did = :did")
    suspend fun getKeysForDid(did: String): List<DidKeyMapping>

    @Transaction
    suspend fun insertIdentity(did: DidEntity, keys: List<DidKeyMapping>) {
        insertDid(did)
        insertKeys(keys)
    }

    @Query("DELETE FROM dids WHERE peerDid = :identifier")
    suspend fun deleteDid(identifier: String)
}