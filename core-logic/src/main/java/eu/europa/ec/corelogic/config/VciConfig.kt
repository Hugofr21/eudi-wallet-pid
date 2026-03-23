package eu.europa.ec.corelogic.config

import eu.europa.ec.corelogic.model.FormatType
import eu.europa.ec.eudi.wallet.issue.openid4vci.OpenId4VciManager

/**
 * Configuration class that associates an [OpenId4VciManager.Config] with a specific display order in the ``AddDocument`` Screen.
 *
 * This class facilitates the management of multiple Verifiable Credential Issuance (VCI)
 * configurations by assigning an order, ensuring they are displayed
 * in a predetermined priority.
 *
 * @property config The [OpenId4VciManager.Config] instance containing the Issuer configuration.
 * @property order An integer defining the priority of this configuration.
 * @property type The [FormatType] associated with this configuration.
 */
data class VciConfig(
    val config: OpenId4VciManager.Config,
    val order: Int,
    val type: FormatType,
)