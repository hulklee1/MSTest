package com.example.edgeaiapp

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.*


interface ApiService {
    // 로그인
    @POST("/api/login")
    suspend fun login(@Body loginRequest: com.example.edgeaiapp.LoginRequest): Response<com.example.edgeaiapp.LoginResponse>

    // Jetson XAVIER 엣지서버 이미지 분석 API
    @Multipart
    @POST("/api/analyze")
    suspend fun analyzeImage(
        @Part image: MultipartBody.Part,
        @Header("Authorization") token: String
    ): Response<com.example.edgeaiapp.ImageAnalysisResponse>

    // 다중 이미지 분석 API (새로 추가)
    @Multipart
    @POST("/api/analyze-multiple")
    suspend fun analyzeMultipleImages(
        @Part images: List<MultipartBody.Part>,
        @Header("Authorization") token: String
    ): Response<MultipleImageAnalysisResponse>

    // 여러 이미지 일괄 분석 (기존 호환성)
    @Multipart
    @POST("/api/batch-analyze")
    suspend fun analyzeMultipleImagesLegacy(
        @Part images: List<MultipartBody.Part>
    ): Response<List<com.example.edgeaiapp.ImageAnalysisResponse>>

    // 분석 결과 수정 및 전송
    @POST("/api/update-results")
    suspend fun updateAnalysisResults(
        @Body results: List<com.example.edgeaiapp.AnalysisResult>
    ): Response<Unit>

    // 기존 메서드들 (호환성 유지)
    @Multipart
    @POST("/analysis")
    suspend fun uploadImages(
        @Part images: List<MultipartBody.Part>
    ): Response<List<com.example.edgeaiapp.AnalysisResult>>

    @Multipart
    @POST("/analyze")
    suspend fun analyzeImages(
        @Part images: List<MultipartBody.Part>,
        @Header("Authorization") token: String?
    ): Response<List<com.example.edgeaiapp.AnalysisResult>>

    @POST("/send-final-results")
    suspend fun sendFinalResults(
        @Body results: List<com.example.edgeaiapp.AnalysisResult>,
        @Header("Authorization") token: String?
    ): Response<Unit>

    @Multipart
    @POST("/batch-upload")
    suspend fun sendBatchImages(
        @Part images: List<MultipartBody.Part>,
        @Query("product_type") productType: String,
        @Header("Authorization") token: String?
    ): Response<Unit>
} 