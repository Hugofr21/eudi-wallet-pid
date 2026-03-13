package eu.europa.ec.corelogic.model

import eu.europa.ec.eudi.wallet.document.NameSpace


sealed interface ClaimType {
    data object SdJwtVc : ClaimType
    data class MsoMdoc(val namespace: NameSpace) : ClaimType

    data object Unknown : ClaimType
}
