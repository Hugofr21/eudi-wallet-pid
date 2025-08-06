package eu.europa.ec.verifierfeature.utils.prepareVc

import eu.europa.ec.eudi.openid4vp.Client
import eu.europa.ec.eudi.openid4vp.TransactionData
import eu.europa.ec.eudi.openid4vp.VerifiablePresentation
import eu.europa.ec.eudi.wallet.document.NameSpace


fun prepareSdJwtVcVerifiablePresentation(
    nonce: String,
    transactionData: List<TransactionData>?,
    vct: String? = null,
    nameSpace: NameSpace? = null,
    audience: Client
): VerifiablePresentation.Generic {
    return VerifiablePresentation.Generic("")
}