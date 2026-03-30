package com.pixeleye.plantdoctor.data.api

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class PlantScanDto(
    @SerialName("id")
    val id: String? = null,
    @SerialName("user_id")
    val userId: String,
    @SerialName("image_url")
    val imageUrl: String,
    @SerialName("disease_title")
    val diseaseTitle: String,
    @SerialName("treatment_plan")
    val treatmentPlan: String,
    @SerialName("created_at")
    val createdAt: String? = null
)
