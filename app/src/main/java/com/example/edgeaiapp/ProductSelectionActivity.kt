package com.example.edgeaiapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.gson.Gson
import android.util.Base64

class ProductSelectionActivity : AppCompatActivity() {
    private lateinit var titleText: TextView
    private lateinit var productRadioGroup: RadioGroup
    private lateinit var btnSendFinal: Button
    private lateinit var btnCancel: Button
    private lateinit var authToken: String
    
    private var selectedImages: List<Uri>? = null
    private var productInfo: Map<String, List<ProductInfo>>? = null
    private var tagName: String? = null
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
        productRadioGroup = findViewById(R.id.productRadioGroup)
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
        if (tagName.isNullOrEmpty() || productInfo.isNullOrEmpty()) {
            titleText.text = "상품 정보를 불러올 수 없습니다"
            btnSendFinal.isEnabled = false
            return
        }
        
        val products = productInfo!![tagName]
        if (products.isNullOrEmpty()) {
            titleText.text = "해당 분류에 대한 상품이 없습니다"
            btnSendFinal.isEnabled = false
            return
        }
        
        titleText.text = "상품 선택"
        
        // 라디오 버튼들 동적 생성
        productRadioGroup.removeAllViews()
        
        products.forEachIndexed { index, product ->
            val radioButton = RadioButton(this).apply {
                id = index
                text = "${product.prodNm}"
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
            productRadioGroup.addView(radioButton)
        }
        
        // 첫 번째 상품을 기본 선택
        if (products.isNotEmpty()) {
            productRadioGroup.check(0)
            selectedProduct = SelectedProduct(
                prodSq = products[0].prodSq,
                prodNm = products[0].prodNm,
                tagName = products[0].tagName ?: tagName!!  // ProductInfo의 tagName 사용
            )
        }
        
        // 라디오 버튼 선택 리스너
        productRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId >= 0 && checkedId < products.size) {
                val product = products[checkedId]
                selectedProduct = SelectedProduct(
                    prodSq = product.prodSq,
                    prodNm = product.prodNm,
                    tagName = product.tagName ?: tagName!!  // ProductInfo의 tagName 사용
                )
                Log.d(TAG, "Selected product: ${selectedProduct?.prodNm} (tagName: ${selectedProduct?.tagName})")
            }
        }
        
        // 버튼 리스너
        btnSendFinal.setOnClickListener {
            sendFinalTransmission()
        }
        
        btnCancel.setOnClickListener {
            finish()
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
            try {
                // 이미지들을 Base64로 변환
                val imageDataList = mutableListOf<String>()
                
                withContext(Dispatchers.IO) {
                    for (imageUri in images) {
                        try {
                            val inputStream = contentResolver.openInputStream(imageUri)
                            val bytes = inputStream?.readBytes()
                            inputStream?.close()
                            
                            if (bytes != null) {
                                val base64 = Base64.encodeToString(bytes, Base64.DEFAULT)
                                imageDataList.add(base64)
                                Log.d(TAG, "Converted image to base64: ${bytes.size} bytes")
                            } else {
                                Log.e(TAG, "Failed to read image: $imageUri")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error converting image to base64: $imageUri", e)
                        }
                    }
                }
                
                if (imageDataList.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ProductSelectionActivity, "이미지 변환 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                        resetButton()
                    }
                    return@launch
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
                
                // 서버로 전송
                val success = sendToServer(request)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@ProductSelectionActivity, "전송이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                        
                        // 성공 결과와 함께 액티비티 종료
                        val resultIntent = Intent().apply {
                            putExtra("transmission_success", true)
                            putExtra("selected_product", product.prodNm)
                            putExtra("image_count", imageDataList.size)
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    } else {
                        Toast.makeText(this@ProductSelectionActivity, "전송 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                        resetButton()
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in sendFinalTransmission: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ProductSelectionActivity, "전송 중 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                    resetButton()
                }
            }
        }
    }
    
    private suspend fun sendToServer(request: FinalTransmissionRequest): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val gson = Gson()
                val jsonData = gson.toJson(request)
                
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                val requestBody = jsonData.toRequestBody("application/json".toMediaTypeOrNull())
                
                val serverUrl = ServerUrlHelper.getFullServerUrl(this@ProductSelectionActivity)
                val httpRequest = okhttp3.Request.Builder()
                    .url("$serverUrl/api/final-transmission")
                    .post(requestBody)
                    .addHeader("Authorization", if (authToken.startsWith("Bearer ")) authToken else "Bearer $authToken")
                    .addHeader("Content-Type", "application/json")
                    .build()
                
                Log.d(TAG, "Sending final transmission to: $serverUrl/api/final-transmission")
                Log.d(TAG, "Selected product: ${request.selected_product.prodNm}")
                Log.d(TAG, "Images count: ${request.image_data_list.size}")
                
                val response = client.newCall(httpRequest).execute()
                
                Log.d(TAG, "Final transmission response: ${response.code}")
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d(TAG, "Final transmission response body: $responseBody")
                    true
                } else {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "Final transmission failed: ${response.code} - $errorBody")
                    false
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Final transmission exception: ${e.message}", e)
                false
            }
        }
    }
    
    private fun resetButton() {
        btnSendFinal.isEnabled = true
        btnSendFinal.text = "선택한 상품으로 전송"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}
