package eu.europa.ec.verifierfeature.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonObject

@Serializable
data class TransactionEvents(
    @SerialName("transaction_id")
    val transactionId: String,
    @SerialName("last_updated")
    val lastUpdated: Long,
    val events: List<EventTransaction>
)

@Serializable
data class EventTransaction(
    val timestamp: Long,
    val event: String,
    val actor: String,
    val response: JsonObject? = null,
    @SerialName("wallet_response")
    val walletResponse: JsonObject? = null
)