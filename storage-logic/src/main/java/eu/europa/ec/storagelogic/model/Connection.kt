package eu.europa.ec.storagelogic.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connections")
data class Connection(
    @PrimaryKey
    val peerDid: String,
    val displayName: String,
    val peerDidDocumentJson: String,
    val createdAt: Long = System.currentTimeMillis()
)