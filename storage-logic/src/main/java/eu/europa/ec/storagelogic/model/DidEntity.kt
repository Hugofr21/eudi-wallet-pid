package eu.europa.ec.storagelogic.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dids")
data class DidEntity(
    @PrimaryKey
    val peerDid: String,             // O identificador único (did:peer:2...)
    val displayName: String,         // Nome legível (ex: "Minha Carteira Pessoal")
    val peerDidDocumentJson: String, // O documento DID completo em JSON (cache)
    val isDefault: Boolean = false,  // Se é a identidade principal
    val createdAt: Long = System.currentTimeMillis()
)