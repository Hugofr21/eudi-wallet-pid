package eu.europa.ec.storagelogic.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "issuerLogs")
data class IssuerLog(
    @PrimaryKey
    val identifier: String,
    val issuer: String,
    val url: String,
    val createdAt: Long
)
