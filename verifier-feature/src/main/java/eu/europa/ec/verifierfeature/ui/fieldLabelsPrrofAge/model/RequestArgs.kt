package eu.europa.ec.verifierfeature.ui.fieldLabelsPrrofAge.model

import eu.europa.ec.commonfeature.config.IssuanceFlowUiConfig
import kotlinx.serialization.Serializable
import eu.europa.ec.verifierfeature.model.FieldLabel


@Serializable
data class RequestArgs(
    val detailsType: IssuanceFlowUiConfig,
    val documentId: String,
    val fieldLabels: List<FieldLabel>
)
