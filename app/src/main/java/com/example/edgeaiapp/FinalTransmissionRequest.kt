package com.example.edgeaiapp

// 최종 전송 요청 데이터 클래스
data class FinalTransmissionRequest(
    val selected_product: SelectedProduct,
    val image_data_list: List<String>,  // Base64 encoded images
    val timestamp: Long = System.currentTimeMillis(),
    val device_id: String? = null
)

// 선택된 상품 정보
data class SelectedProduct(
    val prodSq: String,
    val prodNm: String,
    val tagName: String  // 상품서버의 실제 tagName (한글)
)
