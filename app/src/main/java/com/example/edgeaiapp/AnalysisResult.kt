package com.example.edgeaiapp

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AnalysisResult(
    val imageUri: Uri,
    var selectedClass: String = "",
    var confidence: Float = 0.0f,
    var allClasses: List<ClassInfo> = emptyList(),
    var isAnalyzed: Boolean = false
) : Parcelable {
    
    // Uri를 String으로 변환하는 함수
    fun getImageUriString(): String = imageUri.toString()
    
    @Parcelize
    data class ClassInfo(
        val className: String,
        val confidence: Float
    ) : Parcelable
}

@Parcelize
data class ClassPrediction(
    val className: String,
    val confidence: Float
) : Parcelable 