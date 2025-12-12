package eu.europa.ec.storagelogic.model



import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "did_keys",
    foreignKeys = [
        ForeignKey(
            entity = DidEntity::class,
            parentColumns = ["peerDid"],
            childColumns = ["did"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["did"]), Index(value = ["keystoreAlias"], unique = true)]
)
data class DidKeyMapping(
    @PrimaryKey
    val keyId: String,             // ID do DID Doc (ex: did:peer:2...#key-1)
    val did: String,               // Chave Estrangeira para DidEntity
    val keystoreAlias: String,     // O 'ponteiro' para o Android Keystore
    val purpose: KeyPurpose,       // AUTHENTICATION ou KEY_AGREEMENT
    val publicKeyMultibase: String // Chave pública em formato texto (Multibase/Base58)
)