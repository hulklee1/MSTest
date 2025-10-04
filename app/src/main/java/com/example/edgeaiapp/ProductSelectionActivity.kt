package com.example.edgeaiapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.Gson
import android.util.Base64

class ProductSelectionActivity : AppCompatActivity() {
    private lateinit var titleText: TextView
    private lateinit var tvStep1Title: TextView
    private lateinit var tvStep2Title: TextView
    private lateinit var radioGroupProductTypes: RadioGroup
    private lateinit var radioGroupSpecificProducts: RadioGroup
    private lateinit var scrollViewStep2: View
    private lateinit var layoutSelectedInfo: LinearLayout
    private lateinit var tvSelectedProductType: TextView
    private lateinit var tvSelectedProduct: TextView
    private lateinit var btnSendFinal: Button
    private lateinit var btnCancel: Button
    private lateinit var authToken: String
    
    private var selectedImages: List<Uri>? = null
    private var productInfo: Map<String, List<ProductInfo>>? = null
    private var tagName: String? = null
    private var selectedProductType: String? = null
    private var selectedProduct: SelectedProduct? = null
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    companion object {
        private const val TAG = "ProductSelectionActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_product_selection)
        
        // 액션 바 숨기기
        supportActionBar?.hide()
        
        initViews()
        
        // 인증 토큰 가져오기
        val token = intent.getStringExtra("auth_token") ?: getSharedPreferences("EdgeAIApp", MODE_PRIVATE).getString("auth_token", null)
        if (token.isNullOrEmpty()) {
            Toast.makeText(this, "인증 토큰이 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        authToken = token
        
        // 전달받은 데이터 처리
        loadIntentData()
        
        // UI 설정
        setupUI()
    }
    
    private fun initViews() {
        titleText = findViewById(R.id.titleText)
        tvStep1Title = findViewById(R.id.tvStep1Title)
        tvStep2Title = findViewById(R.id.tvStep2Title)
        radioGroupProductTypes = findViewById(R.id.radioGroupProductTypes)
        radioGroupSpecificProducts = findViewById(R.id.radioGroupSpecificProducts)
        scrollViewStep2 = findViewById(R.id.scrollViewStep2)
        layoutSelectedInfo = findViewById(R.id.layoutSelectedInfo)
        tvSelectedProductType = findViewById(R.id.tvSelectedProductType)
        tvSelectedProduct = findViewById(R.id.tvSelectedProduct)
        btnSendFinal = findViewById(R.id.btnSendFinal)
        btnCancel = findViewById(R.id.btnCancel)
    }
    
    private fun loadIntentData() {
        try {
            // 선택된 이미지들
            selectedImages = intent.getParcelableArrayListExtra<Uri>("selected_images")
            
            // 상품 정보 (JSON 문자열로 전달받음)
            val productInfoJson = intent.getStringExtra("product_info")
            if (!productInfoJson.isNullOrEmpty()) {
                val gson = Gson()
                val type = object : com.google.gson.reflect.TypeToken<Map<String, List<ProductInfo>>>() {}.type
                productInfo = gson.fromJson(productInfoJson, type)
            }
            
            // tagName
            tagName = intent.getStringExtra("tag_name")
            
            Log.d(TAG, "Loaded data: images=${selectedImages?.size}, tagName=$tagName, productInfo=${productInfo?.keys}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error loading intent data: ${e.message}", e)
            Toast.makeText(this, "데이터 로드 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun setupUI() {
        if (productInfo.isNullOrEmpty()) {
            titleText.text = "상품 정보를 불러올 수 없습니다"
            btnSendFinal.isEnabled = false
            return
        }
        
        // 1단계: 상품 종류 선택 UI 설정
        setupStep1ProductTypes()
        
        // 버튼 리스너
        btnCancel.setOnClickListener {
            finish()
        }
        
        btnSendFinal.setOnClickListener {
            sendFinalTransmission()
        }
    }
    
    private fun setupStep1ProductTypes() {
        val productTypes = productInfo!!.keys.toList()
        
        if (productTypes.isEmpty()) {
            titleText.text = "분석된 상품이 없습니다"
            btnSendFinal.isEnabled = false
            return
        }
        
        titleText.text = "상품 종류를 선택하세요 (${productTypes.size}개 분석됨)"
        
        // 1단계 라디오 버튼들 동적 생성
        radioGroupProductTypes.removeAllViews()
        
        productTypes.forEachIndexed { index, productType ->
            val productCount = productInfo!![productType]?.size ?: 0
            val radioButton = RadioButton(this).apply {
                id = index
                text = "$productType ($productCount 개 상품)"
                textSize = 16f
                setTextColor(android.graphics.Color.parseColor("#333333"))
                setPadding(24, 20, 24, 20)
                setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"))
                
                // 마진 설정
                val params = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 8, 0, 8)
                layoutParams = params
            }
            radioGroupProductTypes.addView(radioButton)
        }
        
        // 1단계 선택 리스너
        radioGroupProductTypes.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId >= 0 && checkedId < productTypes.size) {
                selectedProductType = productTypes[checkedId]
                Log.d(TAG, "Selected product type: $selectedProductType")
                
                // 2단계 UI 표시
                setupStep2SpecificProducts(selectedProductType!!)
                
                // 선택 정보 업데이트
                updateSelectedInfo()
            }
        }
    }
    
    private fun setupStep2SpecificProducts(productType: String) {
        val products = productInfo!![productType] ?: emptyList()
        
        if (products.isEmpty()) {
            Toast.makeText(this, "해당 상품 종류에 대한 상품이 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 2단계 UI 표시
        tvStep2Title.visibility = View.VISIBLE
        scrollViewStep2.visibility = View.VISIBLE
        
        // 2단계 라디오 버튼들 동적 생성
        radioGroupSpecificProducts.removeAllViews()
        
        products.forEachIndexed { index, product ->
            val radioButton = RadioButton(this).apply {
                id = index
                text = product.prodNm
                textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#333333"))
                setPadding(20, 16, 20, 16)
                setBackgroundColor(android.graphics.Color.parseColor("#FFFFFF"))
                
                // 마진 설정
                val params = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 4, 0, 4)
                layoutParams = params
            }
            radioGroupSpecificProducts.addView(radioButton)
        }
        
        // 2단계 선택 리스너
        radioGroupSpecificProducts.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId >= 0 && checkedId < products.size) {
                val product = products[checkedId]
                selectedProduct = SelectedProduct(
                    prodSq = product.prodSq,
                    prodNm = product.prodNm,
                    tagName = product.tagName ?: selectedProductType!!
                )
                Log.d(TAG, "Selected specific product: ${selectedProduct?.prodNm} (prodSq: ${selectedProduct?.prodSq})")
                
                // 선택 정보 업데이트
                updateSelectedInfo()
                
                // 전송 버튼 활성화
                btnSendFinal.isEnabled = true
            }
        }
        
        // 첫 번째 상품을 기본 선택
        if (products.isNotEmpty()) {
            radioGroupSpecificProducts.check(0)
        }
    }
    
    private fun updateSelectedInfo() {
        layoutSelectedInfo.visibility = View.VISIBLE
        
        tvSelectedProductType.text = "선택된 상품 종류: ${selectedProductType ?: "-"}"
        tvSelectedProduct.text = "선택된 상품: ${selectedProduct?.prodNm ?: "-"}"
        
        if (selectedProduct != null) {
            tvSelectedProduct.text = "${tvSelectedProduct.text} (prodSq: ${selectedProduct!!.prodSq})"
        }
    }
    
    private fun sendFinalTransmission() {
        val product = selectedProduct
        val images = selectedImages
        
        if (product == null) {
            Toast.makeText(this, "상품을 선택해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (images.isNullOrEmpty()) {
            Toast.makeText(this, "전송할 이미지가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 버튼 비활성화
        btnSendFinal.isEnabled = false
        btnSendFinal.text = "전송 중..."
        
        coroutineScope.launch {
            var attempt = 0
            val maxAttempts = 3
            var lastError: String? = null
            
            while (attempt < maxAttempts) {
                attempt++
                
                try {
                    Log.d(TAG, "전송 시도 $attempt/$maxAttempts")
                    
                    withContext(Dispatchers.Main) {
                        btnSendFinal.text = "전송 중... ($attempt/$maxAttempts)"
                    }
                    
                    // 이미지들을 Base64로 변환 (메모리 효율적으로)
                    val imageDataList = mutableListOf<String>()
                    var totalSize = 0L
                    
                    withContext(Dispatchers.IO) {
                        for ((index, imageUri) in images.withIndex()) {
                            try {
                                Log.d(TAG, "이미지 변환 중: ${index + 1}/${images.size}")
                                
                                val inputStream = contentResolver.openInputStream(imageUri)
                                val bytes = inputStream?.readBytes()
                                inputStream?.close()
                                
                                if (bytes != null) {
                                    totalSize += bytes.size
                                    
                                    // 개별 이미지 크기 제한 (5MB)
                                    if (bytes.size > 5 * 1024 * 1024) {
                                        Log.w(TAG, "이미지 크기가 너무 큽니다: ${bytes.size} bytes, 압축 시도")
                                        // 이미지 압축 로직 추가 가능
                                    }
                                    
                                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                                    imageDataList.add(base64)
                                    Log.d(TAG, "이미지 변환 완료: ${bytes.size} bytes -> ${base64.length} chars")
                                } else {
                                    Log.e(TAG, "이미지 읽기 실패: $imageUri")
                                    throw Exception("이미지 읽기 실패: $imageUri")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "이미지 변환 오류: $imageUri", e)
                                throw Exception("이미지 변환 실패: ${e.message}")
                            }
                        }
                    }
                    
                    Log.d(TAG, "총 이미지 크기: ${totalSize / 1024 / 1024}MB")
                    
                    if (imageDataList.isEmpty()) {
                        throw Exception("변환된 이미지가 없습니다")
                    }
                    
                    // 총 크기 제한 (50MB)
                    if (totalSize > 50 * 1024 * 1024) {
                        throw Exception("총 이미지 크기가 너무 큽니다: ${totalSize / 1024 / 1024}MB")
                    }
                    
                    // 최종 전송 요청 생성
                    val request = FinalTransmissionRequest(
                        selected_product = product,
                        image_data_list = imageDataList,
                        timestamp = System.currentTimeMillis(),
                        device_id = android.provider.Settings.Secure.getString(
                            contentResolver,
                            android.provider.Settings.Secure.ANDROID_ID
                        )
                    )
                    
                    Log.d(TAG, "전송 요청 생성 완료: ${imageDataList.size}개 이미지, prodSq: ${product.prodSq}")
                    
                    // 서버로 전송
                    val success = sendToServer(request, attempt)
                    
                    if (success) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ProductSelectionActivity, "전송이 완료되었습니다.", Toast.LENGTH_LONG).show()
                            
                            // 성공 결과와 함께 액티비티 종료
                            val resultIntent = Intent().apply {
                                putExtra("transmission_success", true)
                                putExtra("selected_product_type", selectedProductType)
                                putExtra("selected_product", product.prodNm)
                                putExtra("selected_prod_sq", product.prodSq)
                                putExtra("image_count", imageDataList.size)
                                putExtra("total_size_mb", totalSize / 1024 / 1024)
                                putExtra("attempts", attempt)
                            }
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        }
                        return@launch // 성공 시 루프 종료
                    } else {
                        lastError = "서버 응답 오류"
                        if (attempt < maxAttempts) {
                            Log.w(TAG, "전송 실패, 재시도 대기 중... ($attempt/$maxAttempts)")
                            delay((2000 * attempt).toLong()) // 지수 백오프
                        }
                    }
                    
                } catch (e: Exception) {
                    lastError = e.message ?: "알 수 없는 오류"
                    Log.e(TAG, "전송 시도 $attempt 실패: ${e.message}", e)
                    
                    if (attempt < maxAttempts) {
                        Log.w(TAG, "재시도 대기 중... ($attempt/$maxAttempts)")
                        delay((2000 * attempt).toLong()) // 지수 백오프: 2초, 4초, 6초
                    }
                }
            }
            
            // 모든 시도 실패
            withContext(Dispatchers.Main) {
                val errorMessage = "전송 실패 (${maxAttempts}회 시도): $lastError"
                Toast.makeText(this@ProductSelectionActivity, errorMessage, Toast.LENGTH_LONG).show()
                Log.e(TAG, errorMessage)
                resetButton()
            }
        }
    }
    
    private suspend fun sendToServer(request: FinalTransmissionRequest, attempt: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "서버 전송 시작 (시도 $attempt)")
                
                val gson = Gson()
                val jsonData = gson.toJson(request)
                
                Log.d(TAG, "JSON 데이터 크기: ${jsonData.length} chars")
                
                // 타임아웃을 시도 횟수에 따라 증가
                val connectTimeout = 30L + (attempt * 10L)
                val readTimeout = 120L + (attempt * 30L)
                
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(connectTimeout, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(readTimeout, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(60L + (attempt * 20L), java.util.concurrent.TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build()
                
                val requestBody = jsonData.toRequestBody("application/json".toMediaTypeOrNull())
                
                val serverUrl = ServerUrlHelper.getFullServerUrl(this@ProductSelectionActivity)
                val httpRequest = okhttp3.Request.Builder()
                    .url("$serverUrl/api/final-transmission")
                    .post(requestBody)
                    .addHeader("Authorization", if (authToken.startsWith("Bearer ")) authToken else "Bearer $authToken")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "EdgeAI-Mobile-App/1.0")
                    .addHeader("X-Request-Attempt", attempt.toString())
                    .build()
                
                Log.d(TAG, "전송 URL: $serverUrl/api/final-transmission")
                Log.d(TAG, "선택된 상품 종류: $selectedProductType")
                Log.d(TAG, "선택된 상품: ${request.selected_product.prodNm} (prodSq: ${request.selected_product.prodSq})")
                Log.d(TAG, "이미지 개수: ${request.image_data_list.size}")
                Log.d(TAG, "타임아웃 설정: connect=${connectTimeout}s, read=${readTimeout}s")
                
                val startTime = System.currentTimeMillis()
                val response = client.newCall(httpRequest).execute()
                val duration = System.currentTimeMillis() - startTime
                
                Log.d(TAG, "서버 응답 시간: ${duration}ms")
                Log.d(TAG, "응답 코드: ${response.code}")
                Log.d(TAG, "응답 메시지: ${response.message}")
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "전송 성공! 응답: $responseBody")
                    
                    // 응답 본문 파싱하여 실제 성공 여부 확인
                    try {
                        if (!responseBody.isNullOrEmpty()) {
                            val responseJson = gson.fromJson(responseBody, Map::class.java)
                            val success = responseJson["success"] as? Boolean ?: true
                            if (!success) {
                                val message = responseJson["message"] as? String ?: "서버에서 처리 실패"
                                Log.e(TAG, "서버 처리 실패: $message")
                                return@withContext false
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "응답 파싱 실패, 성공으로 간주: ${e.message}")
                    }
                    
                    true
                } else {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "전송 실패: ${response.code} ${response.message}")
                    Log.e(TAG, "오류 응답: $errorBody")
                    
                    // 특정 오류 코드에 대한 처리
                    when (response.code) {
                        401 -> {
                            Log.e(TAG, "인증 오류: 토큰이 만료되었거나 유효하지 않음")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ProductSelectionActivity, "인증이 만료되었습니다. 다시 로그인해주세요.", Toast.LENGTH_LONG).show()
                            }
                        }
                        413 -> {
                            Log.e(TAG, "요청 크기 초과: 이미지 크기가 너무 큼")
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@ProductSelectionActivity, "이미지 크기가 너무 큽니다. 이미지 수를 줄여주세요.", Toast.LENGTH_LONG).show()
                            }
                        }
                        500 -> {
                            Log.e(TAG, "서버 내부 오류")
                            if (attempt < 3) {
                                Log.w(TAG, "서버 오류로 인한 재시도 예정")
                            }
                        }
                        503 -> {
                            Log.e(TAG, "서버 일시적 사용 불가")
                            if (attempt < 3) {
                                Log.w(TAG, "서버 과부하로 인한 재시도 예정")
                            }
                        }
                    }
                    
                    false
                }
                
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "네트워크 타임아웃 (시도 $attempt): ${e.message}")
                false
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "서버 연결 실패 (시도 $attempt): ${e.message}")
                false
            } catch (e: java.io.IOException) {
                Log.e(TAG, "네트워크 I/O 오류 (시도 $attempt): ${e.message}")
                false
            } catch (e: Exception) {
                Log.e(TAG, "전송 중 예외 발생 (시도 $attempt): ${e.message}", e)
                false
            }
        }
    }
    
    private fun resetButton() {
        btnSendFinal.isEnabled = selectedProduct != null
        btnSendFinal.text = "전송"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}