package eu.europa.ec.verifierfeature.ui.fieldLabelsPrrofAge.model

import eu.europa.ec.commonfeature.config.IssuanceFlowType
import kotlinx.serialization.Serializable
import eu.europa.ec.verifierfeature.model.FieldLabel


@Serializable
data class RequestArgs(
    val detailsType: String? = null,
    val documentId: String,
    val fieldLabels: List<FieldLabel>
)
