package com.pixeleye.plantdoctor.data.api

import androidx.annotation.Keep

@Keep
data class DiagnosisResponse(
    val summary: String,
    val actionPlan: List<String>
)
