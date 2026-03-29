package com.pixeleye.plantdoctor.data.api

import androidx.annotation.Keep

@Keep
data class DiagnosisResponse(
    val summary: String,
    val organicTreatments: List<String> = emptyList(),
    val chemicalTreatments: List<String> = emptyList()
) {
    val actionPlan: List<String>
        get() = organicTreatments + chemicalTreatments
}
