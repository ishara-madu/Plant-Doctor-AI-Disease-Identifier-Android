package com.pixeleye.plantdoctor.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.pixeleye.plantdoctor.data.api.PlantScanDto

@Entity(tableName = "history_table")
data class HistoryEntity(
    @PrimaryKey
    val id: String,
    val userId: String,
    val imageUrl: String,
    val diseaseTitle: String,
    val treatmentPlan: String,
    val createdAt: String
) {
    fun toDto(): PlantScanDto {
        return PlantScanDto(
            id = id,
            userId = userId,
            imageUrl = imageUrl,
            diseaseTitle = diseaseTitle,
            treatmentPlan = treatmentPlan,
            createdAt = createdAt
        )
    }
}

fun PlantScanDto.toEntity(): HistoryEntity {
    return HistoryEntity(
        id = id ?: java.util.UUID.randomUUID().toString(),
        userId = userId,
        imageUrl = imageUrl,
        diseaseTitle = diseaseTitle,
        treatmentPlan = treatmentPlan,
        createdAt = createdAt ?: ""
    )
}
