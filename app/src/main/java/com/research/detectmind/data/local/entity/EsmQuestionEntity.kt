package com.research.detectmind.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "esm_questions")
data class EsmQuestionEntity(
    @PrimaryKey val id: String,
    val scheduleId: String,
    val questionOrder: Int,
    val questionType: String,
    val questionText: String,
    val required: Boolean,
    val options: String?,   // JSON array (single_choice/multi_choice only)
    val config: String?,    // JSON object (likert/slider/number only)
    val synced: Boolean = false
)
