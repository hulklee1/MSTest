package com.example.edgeaiapp

// Jetson XAVIER 엣지서버 응답 모델
data class ImageAnalysisResponse(
    val success: Boolean,
    val predictions: List<ClassPrediction>,
    val processingTime: Float,
    val modelVersion: String,
    val message: String? = null,
    val productInfo: Map<String, List<ProductInfo>>? = null  // 새로 추가: 상품 정보
)

// 다중 이미지 분석 응답 모델
data class MultipleImageAnalysisResponse(
    val success: Boolean,
    val results: List<ImageAnalysisResult>,
    val productInfo: Map<String, List<ProductInfo>>,  // tagName -> 상품 정보 리스트
    val totalImages: Int,
    val processedImages: Int,
    val modelVersion: String,
    val message: String? = null
)

// 개별 이미지 분석 결과
data class ImageAnalysisResult(
    val imageIndex: Int,
    val filename: String,
    val predictions: List<ClassPrediction>,
    val processingTime: Float,
    val error: String? = null
)

// 상품 정보 데이터 클래스
data class ProductInfo(
    val prodSq: String,
    val prodNm: String,
    val tagName: String? = null  // 상품서버의 실제 tagName (한글)
)
