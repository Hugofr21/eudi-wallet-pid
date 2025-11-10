package eu.europa.ec.corelogic.model.did



/**
 * Challenge sent to another wallet.
 */
data class AuthChallenge(
    val challenge: String,          // Random base64 string
    val issuerDid: String,          // Who sent the challenge?
    val recipientDid: String,       // For whom is the challenge?
    val timestamp: Long,
    val expiresAt: Long
)

/**
 * Response to the challenge (with signature)
 */
data class AuthResponse(
    val challenge: String,          // Echo of the original challenge
    val signature: String,          // sign do challenge (base64)
    val responderDid: String,       // who response
    val timestamp: Long
)