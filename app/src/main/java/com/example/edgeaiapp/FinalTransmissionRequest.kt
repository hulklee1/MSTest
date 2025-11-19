package com.example.edgeaiapp

// 최종 전송 요청 데이터 클래스
data class FinalTransmissionRequest(
    val selected_product: SelectedProduct,
    val image_data_list: List<String>,  // Base64 encoded images
    val timestamp: Long = System.currentTimeMillis(),
    val device_id: String? = null,
    val batch_info: BatchInfo? = null,  // 배치 전송 정보 추가
    val unique_path: String? = null     // 유니크 경로 (6자리 랜덤 숫자, 모든 배치 동일)
)

// 선택된 상품 정보
data class SelectedProduct(
    val prodSq: String,
    val prodNm: String,
    val tagName: String  // 상품서버의 실제 tagName (한글)
)

// 배치 전송 정보
data class BatchInfo(
    val batch_index: Int,      // 현재 배치 인덱스 (0부터 시작)
    val total_batches: Int,    // 전체 배치 수
    val batch_size: Int,       // 현재 배치의 이미지 수
    val total_images: Int      // 전체 이미지 수
)
