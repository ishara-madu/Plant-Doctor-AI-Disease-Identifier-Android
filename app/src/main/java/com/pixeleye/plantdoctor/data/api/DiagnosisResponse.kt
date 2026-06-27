package com.pixeleye.plantdoctor.data.api

import androidx.annotation.Keep

@Keep
data class DiagnosisResponse(
    val summary: String,
    val organicTreatments: List<String> = emptyList(),
    val chemicalTreatments: List<String> = emptyList(),
    val plantName: String? = null,
    val wateringTime: String? = null,
    val fertilizingTime: String? = null,
    val healthStatusPercentage: Int? = null,
    val progressReminderMessage: String? = null,
    val progressReminderDays: Int? = null
) {
    val actionPlan: List<String>
        get() = organicTreatments + chemicalTreatments
}
