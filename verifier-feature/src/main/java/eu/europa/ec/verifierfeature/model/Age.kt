package eu.europa.ec.verifierfeature.model

import kotlinx.serialization.Serializable

@Serializable
data class FieldLabel(
    val key: String,
    val label: String
)

val fieldLabels: List<FieldLabel> = listOf(
    FieldLabel(key = "age_over_18", label = "Age over 18"),
    FieldLabel(key = "age_over_13", label = "Age over 13"),
    FieldLabel(key = "age_over_15", label = "Age over 15"),
    FieldLabel(key = "age_over_16", label = "Age over 16"),
    FieldLabel(key = "age_over_21", label = "Age over 21"),
    FieldLabel(key = "age_over_23", label = "Age over 23"),
    FieldLabel(key = "age_over_25", label = "Age over 25"),
    FieldLabel(key = "age_over_27", label = "Age over 27"),
    FieldLabel(key = "age_over_28", label = "Age over 28"),
    FieldLabel(key = "age_over_40", label = "Age over 40"),
    FieldLabel(key = "age_over_60", label = "Age over 60"),
    FieldLabel(key = "age_over_65", label = "Age over 65"),
    FieldLabel(key = "age_over_67", label = "Age over 67")
)