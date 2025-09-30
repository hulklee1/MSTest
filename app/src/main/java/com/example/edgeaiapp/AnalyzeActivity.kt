package com.example.edgeaiapp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.FileOutputStream
import com.example.edgeaiapp.ImageAnalysisResponse
import com.google.gson.Gson


class AnalyzeActivity : AppCompatActivity() {
    private val results = mutableListOf<com.example.edgeaiapp.AnalysisResult>()
    private lateinit var adapter: com.example.edgeaiapp.AnalysisResultAdapter
    private lateinit var sharedPreferences: SharedPreferences
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private lateinit var authToken: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analyze)

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("EdgeAIApp", MODE_PRIVATE)
        
        // 인증 토큰 가져오기
        val token = intent.getStringExtra("auth_token") ?: sharedPreferences.getString("auth_token", null)
        
        if (token.isNullOrEmpty()) {
            Toast.makeText(this, "인증 토큰이 없습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        authToken = token

        setupRecyclerView()
        setupButtons()
        loadSelectedImages()
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // RecyclerView 터치 이벤트 최적화
        recyclerView.isNestedScrollingEnabled = false
        recyclerView.setHasFixedSize(true)
        
        adapter = com.example.edgeaiapp.AnalysisResultAdapter(results) { position, selectedClass ->
            // 클래스 선택 시 호출되는 콜백
            val result = results[position]
            result.selectedClass = selectedClass
            result.confidence = result.allClasses.find { it.className == selectedClass }?.confidence ?: 0.0f
            adapter.notifyItemChanged(position)
        }
        
        recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        // 이미지 선택 버튼
        findViewById<Button>(R.id.btnSelectImages).setOnClickListener {
            val intent = Intent(this, com.example.edgeaiapp.ImageSelectionActivity::class.java)
            startActivityForResult(intent, REQUEST_SELECT_IMAGES)
        }

        // 분석 요청 버튼
        findViewById<Button>(R.id.btnAnalyze).setOnClickListener {
            if (results.isNotEmpty()) {
                requestAnalysis()
            } else {
                Toast.makeText(this, getString(R.string.select_images_first), Toast.LENGTH_SHORT).show()
            }
        }

        // 결과 수정 버튼
        findViewById<Button>(R.id.btnEditResults).setOnClickListener {
            if (results.isNotEmpty()) {
                val intent = Intent(this, com.example.edgeaiapp.EditResultActivity::class.java)
                intent.putParcelableArrayListExtra("results", ArrayList<com.example.edgeaiapp.AnalysisResult>(results))
                startActivityForResult(intent, REQUEST_EDIT_RESULTS)
            } else {
                Toast.makeText(this, getString(R.string.no_analyzed_results), Toast.LENGTH_SHORT).show()
            }
        }

        // 최종 결과 전송 버튼
        findViewById<Button>(R.id.btnSendFinalResults).setOnClickListener {
            if (results.isNotEmpty()) {
                openProductSelectionActivity()
            } else {
                Toast.makeText(this, getString(R.string.no_analyzed_results), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadSelectedImages() {
        // ImageSelectionActivity에서 전달받은 이미지들 처리
        val selectedImages = intent.getParcelableArrayListExtra<Uri>("selected_images")
        if (selectedImages != null) {
            results.clear()
            selectedImages.forEach { uri ->
                results.add(com.example.edgeaiapp.AnalysisResult(imageUri = uri))
            }
            adapter.notifyDataSetChanged()
        }
    }

    private fun requestAnalysis() {
        if (results.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_images_to_analyze), Toast.LENGTH_SHORT).show()
            return
        }

        // 분석 요청 버튼 비활성화 및 색상 변경
        val analyzeButton = findViewById<Button>(R.id.btnAnalyze)
        analyzeButton.isEnabled = false
        analyzeButton.text = "서버 연결 확인 중..."
        
        // 먼저 서버 연결 상태 확인
        coroutineScope.launch {
            try {
                val serverReachable = checkServerConnection()
                if (!serverReachable) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AnalyzeActivity, "서버에 연결할 수 없습니다. 네트워크 상태를 확인해주세요.", Toast.LENGTH_LONG).show()
                        analyzeButton.isEnabled = true
                        analyzeButton.text = "분석 요청"
                        analyzeButton.setBackgroundColor(resources.getColor(android.R.color.holo_green_dark, theme))
                        analyzeButton.setTextColor(resources.getColor(android.R.color.white, theme))
                    }
                    return@launch
                }
                
                withContext(Dispatchers.Main) {
                    analyzeButton.text = "분석 중..."
                }
                
                performMultipleAnalysis(analyzeButton)  // 새로운 다중 분석 메서드 호출
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AnalyzeActivity, "연결 확인 중 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                    analyzeButton.isEnabled = true
                    analyzeButton.text = "분석 요청"
                    analyzeButton.setBackgroundColor(resources.getColor(android.R.color.holo_green_dark, theme))
                    analyzeButton.setTextColor(resources.getColor(android.R.color.white, theme))
                }
            }
        }
    }
    
    /**
     * 서버 연결 상태를 확인하는 메서드
     */
    private suspend fun checkServerConnection(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val okHttpClient = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                    
                val serverUrl = ServerUrlHelper.getFullServerUrl(this@AnalyzeActivity)
                val request = okhttp3.Request.Builder()
                    .url("$serverUrl/")
                    .head()  // HEAD 요청으로 간단히 연결 확인
                    .build()
                    
                val response = okHttpClient.newCall(request).execute()
                Log.d("AnalyzeActivity", "Server ping response: ${response.code}")
                response.close()
                
                response.isSuccessful || response.code in 400..499  // 4xx도 서버가 응답하는 것으로 간주
            } catch (e: Exception) {
                Log.e("AnalyzeActivity", "Server ping failed: ${e.message}")
                false
            }
        }
    }
    
    private suspend fun performMultipleAnalysis(analyzeButton: Button) {
        // 분석중일 때 버튼 색상을 짙은 회색으로, 글자색을 검은색으로 변경
        withContext(Dispatchers.Main) {
            analyzeButton.setBackgroundColor(resources.getColor(android.R.color.darker_gray, theme))
            analyzeButton.setTextColor(resources.getColor(android.R.color.black, theme))
        }

        try {
            // 다중 이미지 분석 요청
            val analysisResponse = analyzeMultipleImagesWithJetson(results, authToken)
            
            if (analysisResponse != null && analysisResponse.success) {
                // 분석 결과를 기존 형식으로 변환
                val convertedResults = convertToAnalysisResults(analysisResponse, results)
                
                // 메인 스레드에서 UI 업데이트
                withContext(Dispatchers.Main) {
                    Log.d("AnalyzeActivity", "=== Multiple Analysis Completed ===")
                    Log.d("AnalyzeActivity", "Processed ${analysisResponse.processedImages}/${analysisResponse.totalImages} images")
                    
                    // 결과 업데이트
                    results.clear()
                    results.addAll(convertedResults)
                    adapter.notifyDataSetChanged()
                    
                    // 상품 정보가 있으면 상품 선택 화면으로 이동 준비
                    if (analysisResponse.productInfo.isNotEmpty()) {
                        val firstTagName = analysisResponse.productInfo.keys.firstOrNull()
                        if (firstTagName != null) {
                            // 분석 완료 메시지와 함께 상품 선택 안내
                            Toast.makeText(this@AnalyzeActivity, 
                                "이미지 분석이 완료되었습니다.\n상품을 선택하여 전송하세요.", 
                                Toast.LENGTH_LONG).show()
                            
                            // 전송 버튼 활성화 및 텍스트 변경
                            val sendButton = findViewById<Button>(R.id.btnSendFinalResults)
                            sendButton.text = "상품 선택 후 전송"
                            sendButton.isEnabled = true
                            
                            // 상품 정보를 임시 저장 (전송 시 사용)
                            saveProductInfoForTransmission(analysisResponse.productInfo, firstTagName)
                        }
                    } else {
                        Toast.makeText(this@AnalyzeActivity, "이미지 분석이 완료되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AnalyzeActivity, 
                        "다중 이미지 분석에 실패했습니다: ${analysisResponse?.message ?: "알 수 없는 오류"}", 
                        Toast.LENGTH_SHORT).show()
                }
            }
                
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@AnalyzeActivity, "분석 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } finally {
            // 분석 요청 버튼 다시 활성화 및 원래 색상으로 복원
            withContext(Dispatchers.Main) {
                analyzeButton.isEnabled = true
                analyzeButton.text = "분석 요청"
                
                // 원래 색상으로 복원 (녹색 배경, 흰색 글자)
                analyzeButton.setBackgroundColor(resources.getColor(android.R.color.holo_green_dark, theme))
                analyzeButton.setTextColor(resources.getColor(android.R.color.white, theme))
            }
        }
    }
    
    private suspend fun performAnalysis(analyzeButton: Button) {
        // 기존 단일 분석 메서드 (호환성 유지)
        withContext(Dispatchers.Main) {
            analyzeButton.setBackgroundColor(resources.getColor(android.R.color.darker_gray, theme))
            analyzeButton.setTextColor(resources.getColor(android.R.color.black, theme))
        }

        try {
            // Jetson Xavier 서버로 이미지 분석 요청
            val analysisResults = analyzeImagesWithJetson(results, authToken)
            
            // 메인 스레드에서 UI 업데이트 수행
            withContext(Dispatchers.Main) {
                Log.d("AnalyzeActivity", "=== Starting UI Update Process ===")
                Log.d("AnalyzeActivity", "Original results count: ${results.size}, Analyzed results count: ${analysisResults.size}")
                
                // 결과 상태 검증 로그 (더 상세하게)
                analysisResults.forEachIndexed { i, result ->
                    Log.d("AnalyzeActivity", "=== AnalysisResult[$i] DETAILS ===")
                    Log.d("AnalyzeActivity", "  isAnalyzed: ${result.isAnalyzed}")
                    Log.d("AnalyzeActivity", "  selectedClass: '${result.selectedClass}'")
                    Log.d("AnalyzeActivity", "  confidence: ${result.confidence}")
                    Log.d("AnalyzeActivity", "  allClasses.size: ${result.allClasses.size}")
                    Log.d("AnalyzeActivity", "  allClasses: ${result.allClasses.map { "${it.className}(${it.confidence})" }}")
                    
                    // 어댑터 로직과 동일한 판단 로직으로 상태 예측
                    val predictedState = when {
                        result.isAnalyzed && result.allClasses.isNotEmpty() -> "ANALYZED"
                        result.selectedClass.isNotEmpty() && 
                        (result.selectedClass.contains("오류") || 
                         result.selectedClass.contains("실패") || 
                         result.selectedClass.contains("파싱") ||
                         result.selectedClass.contains("프로토콜") ||
                         result.selectedClass.contains("네트워크") ||
                         result.selectedClass.contains("처리") ||
                         result.selectedClass.contains("응답") ||
                         result.selectedClass.contains("연결") ||
                         result.selectedClass.contains("HTTP") ||
                         !result.isAnalyzed) -> "ERROR"
                        result.selectedClass.isEmpty() && !result.isAnalyzed -> "WAITING"
                        else -> "UNKNOWN"
                    }
                    Log.d("AnalyzeActivity", "  Predicted adapter state: $predictedState")
                }
                
                // CRITICAL FIX: 전체 리스트를 완전히 교체하는 방식으로 변경
                results.clear()
                results.addAll(analysisResults)
                
                // RecyclerView 강제 새로고침
                adapter.notifyDataSetChanged()
                
                // 추가 검증: UI 업데이트 후 실제 상태 확인
                Log.d("AnalyzeActivity", "=== After UI Update ===")
                results.forEachIndexed { i, result ->
                    Log.d("AnalyzeActivity", "Results[$i]: isAnalyzed=${result.isAnalyzed}, selectedClass='${result.selectedClass}'")
                }
                
                // 강제 UI 새로고침을 위한 추가 처리
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d("AnalyzeActivity", "=== Delayed UI Refresh ===")
                    adapter.notifyDataSetChanged()
                    
                    // RecyclerView의 각 ViewHolder를 강제로 업데이트
                    val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
                    for (i in 0 until adapter.itemCount) {
                        val viewHolder = recyclerView.findViewHolderForAdapterPosition(i)
                        if (viewHolder != null) {
                            adapter.onBindViewHolder(viewHolder as com.example.edgeaiapp.AnalysisResultAdapter.ViewHolder, i)
                            Log.d("AnalyzeActivity", "Force updated ViewHolder at position $i")
                        }
                    }
                }, 100)
                
                // 추가 안전장치: 강제 UI 업데이트
                Handler(Looper.getMainLooper()).postDelayed({
                    forceUIUpdate()
                }, 300)
                
                Toast.makeText(this@AnalyzeActivity, "이미지 분석이 완료되었습니다.", Toast.LENGTH_SHORT).show()
            }
                
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@AnalyzeActivity, "분석 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } finally {
            // 분석 요청 버튼 다시 활성화 및 원래 색상으로 복원
            withContext(Dispatchers.Main) {
                analyzeButton.isEnabled = true
                analyzeButton.text = "분석 요청"
                
                // 원래 색상으로 복원 (녹색 배경, 흰색 글자)
                analyzeButton.setBackgroundColor(resources.getColor(android.R.color.holo_green_dark, theme))
                analyzeButton.setTextColor(resources.getColor(android.R.color.white, theme))
            }
        }
    }

    private suspend fun analyzeMultipleImagesWithJetson(images: List<com.example.edgeaiapp.AnalysisResult>, authToken: String): MultipleImageAnalysisResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val okHttpClient = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val serverUrl = ServerUrlHelper.getFullServerUrl(this@AnalyzeActivity)
                val retrofit = Retrofit.Builder()
                    .baseUrl("$serverUrl/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(okHttpClient)
                    .build()

                val api = retrofit.create(ApiService::class.java)

                // MultipartBody.Part 리스트 생성
                val imageParts = mutableListOf<MultipartBody.Part>()
                
                images.forEachIndexed { index, result ->
                    try {
                        val inputStream = contentResolver.openInputStream(result.imageUri)
                        val bytes = inputStream?.readBytes()
                        inputStream?.close()

                        if (bytes != null) {
                            val requestBody = okhttp3.RequestBody.create("image/*".toMediaTypeOrNull(), bytes)
                            val part = MultipartBody.Part.createFormData("image$index", "image_$index.jpg", requestBody)
                            imageParts.add(part)
                            Log.d("AnalyzeActivity", "Added image part $index: ${bytes.size} bytes")
                        } else {
                            Log.e("AnalyzeActivity", "Failed to read image $index: ${result.imageUri}")
                        }
                    } catch (e: Exception) {
                        Log.e("AnalyzeActivity", "Error processing image $index: ${e.message}", e)
                    }
                }

                if (imageParts.isEmpty()) {
                    Log.e("AnalyzeActivity", "No valid image parts created")
                    return@withContext null
                }

                Log.d("AnalyzeActivity", "Sending ${imageParts.size} images for multiple analysis")

                val response = api.analyzeMultipleImages(
                    images = imageParts,
                    token = if (authToken.startsWith("Bearer ")) authToken else "Bearer $authToken"
                )

                if (response.isSuccessful) {
                    val responseBody = response.body()
                    Log.d("AnalyzeActivity", "Multiple analysis response: $responseBody")
                    responseBody
                } else {
                    Log.e("AnalyzeActivity", "Multiple analysis failed: ${response.code()} - ${response.errorBody()?.string()}")
                    null
                }

            } catch (e: Exception) {
                Log.e("AnalyzeActivity", "Multiple analysis exception: ${e.message}", e)
                null
            }
        }
    }
    
    private fun convertToAnalysisResults(response: MultipleImageAnalysisResponse, originalResults: List<com.example.edgeaiapp.AnalysisResult>): List<com.example.edgeaiapp.AnalysisResult> {
        val convertedResults = mutableListOf<com.example.edgeaiapp.AnalysisResult>()
        
        response.results.forEachIndexed { index, analysisResult ->
            try {
                val originalResult = if (index < originalResults.size) {
                    originalResults[index]
                } else {
                    com.example.edgeaiapp.AnalysisResult(imageUri = Uri.EMPTY)
                }
                
                // 분석 결과 변환
                val allClasses = analysisResult.predictions.map { prediction ->
                    com.example.edgeaiapp.AnalysisResult.ClassInfo(
                        className = prediction.className,
                        confidence = prediction.confidence
                    )
                }
                
                val selectedClass = if (allClasses.isNotEmpty()) {
                    allClasses.first().className
                } else {
                    "Unknown"
                }
                
                val confidence = if (allClasses.isNotEmpty()) {
                    allClasses.first().confidence
                } else {
                    0.0f
                }
                
                val convertedResult = com.example.edgeaiapp.AnalysisResult(
                    imageUri = originalResult.imageUri,
                    isAnalyzed = true,
                    selectedClass = selectedClass,
                    confidence = confidence,
                    allClasses = allClasses
                )
                
                convertedResults.add(convertedResult)
                Log.d("AnalyzeActivity", "Converted result $index: $selectedClass (${confidence})")
                
            } catch (e: Exception) {
                Log.e("AnalyzeActivity", "Error converting result $index: ${e.message}", e)
                
                // 오류 발생 시 기본 결과 추가
                val errorResult = if (index < originalResults.size) {
                    originalResults[index].copy(
                        isAnalyzed = false,
                        selectedClass = "분석 오류: ${e.message}",
                        confidence = 0.0f,
                        allClasses = emptyList()
                    )
                } else {
                    com.example.edgeaiapp.AnalysisResult(
                        imageUri = Uri.EMPTY,
                        isAnalyzed = false,
                        selectedClass = "분석 오류: ${e.message}",
                        confidence = 0.0f,
                        allClasses = emptyList()
                    )
                }
                convertedResults.add(errorResult)
            }
        }
        
        return convertedResults
    }
    
    private fun saveProductInfoForTransmission(productInfo: Map<String, List<ProductInfo>>, primaryTagName: String) {
        // SharedPreferences에 상품 정보 저장
        val sharedPrefs = getSharedPreferences("EdgeAIApp", MODE_PRIVATE)
        val gson = Gson()
        
        sharedPrefs.edit()
            .putString("product_info", gson.toJson(productInfo))
            .putString("primary_tag_name", primaryTagName)
            .apply()
        
        Log.d("AnalyzeActivity", "Saved product info for transmission: $primaryTagName")
    }
    
    private fun openProductSelectionActivity() {
        try {
            // SharedPreferences에서 상품 정보 읽기
            val sharedPrefs = getSharedPreferences("EdgeAIApp", MODE_PRIVATE)
            val productInfoJson = sharedPrefs.getString("product_info", null)
            val primaryTagName = sharedPrefs.getString("primary_tag_name", null)
            
            if (productInfoJson.isNullOrEmpty() || primaryTagName.isNullOrEmpty()) {
                Toast.makeText(this, "상품 정보가 없습니다. 먼저 이미지 분석을 완료해주세요.", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 선택된 이미지들 수집
            val selectedImages = results.map { it.imageUri }
            
            if (selectedImages.isEmpty()) {
                Toast.makeText(this, "전송할 이미지가 없습니다.", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 상품 선택 화면으로 이동
            val intent = Intent(this, ProductSelectionActivity::class.java).apply {
                putExtra("auth_token", authToken)
                putParcelableArrayListExtra("selected_images", ArrayList(selectedImages))
                putExtra("product_info", productInfoJson)
                putExtra("tag_name", primaryTagName)
            }
            
            startActivityForResult(intent, REQUEST_PRODUCT_SELECTION)
            
        } catch (e: Exception) {
            Log.e("AnalyzeActivity", "Error opening product selection: ${e.message}", e)
            Toast.makeText(this, "상품 선택 화면을 열 수 없습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    

    private suspend fun analyzeImagesWithJetson(images: List<com.example.edgeaiapp.AnalysisResult>, authToken: String): List<com.example.edgeaiapp.AnalysisResult> {
        return withContext(Dispatchers.IO) {
            // 더 견고한 HTTP 클라이언트 설정
            val okHttpClient = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
                
        val serverUrl = ServerUrlHelper.getFullServerUrl(this@AnalyzeActivity)
                val retrofit = Retrofit.Builder()
            .baseUrl("$serverUrl/")
            .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                
            val api = retrofit.create(com.example.edgeaiapp.ApiService::class.java)
            val analyzedResults = mutableListOf<com.example.edgeaiapp.AnalysisResult>()

            images.forEachIndexed { index, result ->
                try {
                    Log.d("AnalyzeActivity", "Processing image ${index + 1}/${images.size}")
                    
                    // Uri를 File로 변환
                    val file = uriToFile(result.imageUri!!)
                    Log.d("AnalyzeActivity", "File created: ${file.name}, size: ${file.length()} bytes")
                    
                    // MultipartBody 생성
                    val requestBody = file.asRequestBody("image/*".toMediaTypeOrNull())
                    val multipartBody = MultipartBody.Part.createFormData("image", file.name, requestBody)
                    
                    Log.d("AnalyzeActivity", "Sending request to Jetson Xavier server...")
                    
                    // Jetson Xavier API 호출
                    val token = if (authToken.startsWith("Bearer ")) authToken else "Bearer $authToken"
                    Log.d("AnalyzeActivity", "Using token: $token")
                    val response = api.analyzeImage(multipartBody, token)
                    
                    Log.d("AnalyzeActivity", "Response received: ${response.code()}")
                    Log.d("AnalyzeActivity", "Response headers: ${response.headers()}")
                    Log.d("AnalyzeActivity", "Response message: ${response.message()}")
                        
                        if (response.isSuccessful) {
                            try {
                            val analysisResponse = response.body()
                                Log.d("AnalyzeActivity", "=== DETAILED API RESPONSE ANALYSIS ===")
                                Log.d("AnalyzeActivity", "HTTP Status Code: ${response.code()}")
                                Log.d("AnalyzeActivity", "Content-Type: ${response.headers()["Content-Type"]}")
                                Log.d("AnalyzeActivity", "Response body is null: ${analysisResponse == null}")
                                
                                if (analysisResponse == null) {
                                    // 응답이 null인 경우 원시 응답에서 수동 파싱 시도
                                    Log.w("AnalyzeActivity", "Response body is null, attempting manual parsing...")
                                    val manualParseResult = tryManualResponseParsing(response, result)
                                    analyzedResults.add(manualParseResult)
                                    return@forEachIndexed
                                }
                                
                                Log.d("AnalyzeActivity", "Raw response body: $analysisResponse")
                                Log.d("AnalyzeActivity", "Success field: ${analysisResponse.success}")
                                Log.d("AnalyzeActivity", "Predictions count: ${analysisResponse.predictions.size}")
                                Log.d("AnalyzeActivity", "Processing time: ${analysisResponse.processingTime}")
                                Log.d("AnalyzeActivity", "Model version: ${analysisResponse.modelVersion}")
                                Log.d("AnalyzeActivity", "Message: ${analysisResponse.message}")
                                
                                // 각 예측 결과 로그
                                analysisResponse.predictions.forEachIndexed { idx, pred ->
                                    Log.d("AnalyzeActivity", "Prediction[$idx]: className='${pred.className}', confidence=${pred.confidence}")
                                }
                                
                                if (analysisResponse.success == true) {
                            // 분석 결과를 AnalysisResult에 매핑
                            Log.d("AnalyzeActivity", "Before mapping - Original result isAnalyzed: ${result.isAnalyzed}")
                            val mappedResult = mapJetsonResponseToAnalysisResult(result, analysisResponse)
                            Log.d("AnalyzeActivity", "After mapping - Mapped result isAnalyzed: ${mappedResult.isAnalyzed}, selectedClass: '${mappedResult.selectedClass}', predictions count: ${analysisResponse.predictions.size}")
                            analyzedResults.add(mappedResult)
                            Log.d("AnalyzeActivity", "Image ${index + 1} analyzed successfully")
                        } else {
                            // 분석 실패 시 원본 결과 유지하지만 실패 상태로 표시
                            Log.w("AnalyzeActivity", "=== ANALYSIS MARKED AS FAILED ===")
                            Log.w("AnalyzeActivity", "Image ${index + 1} analysis failed")
                            Log.w("AnalyzeActivity", "analysisResponse is null: ${analysisResponse == null}")
                            Log.w("AnalyzeActivity", "analysisResponse?.success: ${analysisResponse?.success}")
                            Log.w("AnalyzeActivity", "Error message: ${analysisResponse?.message ?: "Unknown error"}")
                            
                            val failedResult = AnalysisResult(
                                imageUri = result.imageUri,
                                isAnalyzed = false,  // 분석 실패로 명시적 표시
                                selectedClass = "분석 실패: ${analysisResponse?.message ?: "응답 없음"}",
                                confidence = 0.0f,
                                allClasses = emptyList()
                            )
                            analyzedResults.add(failedResult)
                        }
                            } catch (parseException: Exception) {
                                Log.e("AnalyzeActivity", "Error parsing successful response: ${parseException.message}")
                                val parseErrorResult = AnalysisResult(
                                    imageUri = result.imageUri,
                                    isAnalyzed = false,
                                    selectedClass = "응답 처리 오류",
                                    confidence = 0.0f,
                                    allClasses = emptyList()
                                )
                                analyzedResults.add(parseErrorResult)
                        }
                    } else {
                        // API 호출 실패 시 실패 상태로 표시
                        val errorBody = response.errorBody()?.string()
                        Log.e("AnalyzeActivity", "API call failed for image ${index + 1}: ${response.code()} - $errorBody")
                        val failedResult = AnalysisResult(
                            imageUri = result.imageUri,
                            isAnalyzed = false,
                            selectedClass = "네트워크 오류",
                            confidence = 0.0f,
                            allClasses = emptyList()
                        )
                        analyzedResults.add(failedResult)
                    }
                    
                    } catch (e: Exception) {
                    // 오류 발생 시 예외 상태로 표시
                    Log.e("AnalyzeActivity", "=== EXCEPTION DURING IMAGE PROCESSING ===")
                    Log.e("AnalyzeActivity", "Image ${index + 1} exception: ${e.javaClass.simpleName}")
                    Log.e("AnalyzeActivity", "Exception message: ${e.message}")
                    Log.e("AnalyzeActivity", "Exception details:", e)
                    
                    // 프로토콜 오류의 경우 특별 처리 - 부분 응답이라도 복구 시도
                    val recoveredResult = if (e is java.net.ProtocolException) {
                        Log.w("AnalyzeActivity", "Protocol exception detected, attempting response recovery...")
                        tryRecoverFromProtocolError(e, result, index)
                    } else {
                        // 다른 오류 타입 처리
                        val errorType = when (e) {
                            is java.net.SocketTimeoutException -> "연결 시간 초과"
                            is java.net.ConnectException -> "서버 연결 실패"
                            is com.google.gson.JsonSyntaxException -> "응답 파싱 오류"
                            is retrofit2.HttpException -> "HTTP 오류: ${e.code()}"
                            else -> "처리 오류: ${e.javaClass.simpleName}"
                        }
                        
                        AnalysisResult(
                            imageUri = result.imageUri,
                            isAnalyzed = false,
                            selectedClass = errorType,
                            confidence = 0.0f,
                            allClasses = emptyList()
                        )
                    }
                    
                    analyzedResults.add(recoveredResult)
                }
            }
            
            Log.d("AnalyzeActivity", "Analysis completed. Total results: ${analyzedResults.size}")
            
            // 결과 상태 확인 로그
            analyzedResults.forEachIndexed { index, result ->
                Log.d("AnalyzeActivity", "Result $index: isAnalyzed=${result.isAnalyzed}, selectedClass='${result.selectedClass}', confidence=${result.confidence}")
            }
            
            analyzedResults
        }
    }

    private fun mapJetsonResponseToAnalysisResult(originalResult: com.example.edgeaiapp.AnalysisResult, jetsonResponse: com.example.edgeaiapp.ImageAnalysisResponse): com.example.edgeaiapp.AnalysisResult {
        // Jetson Xavier 응답을 AnalysisResult로 변환
        
        // 예측 결과를 ClassInfo 리스트로 변환
        val classInfos = jetsonResponse.predictions.map { prediction: com.example.edgeaiapp.ClassPrediction ->
            com.example.edgeaiapp.AnalysisResult.ClassInfo(
                className = prediction.className,
                confidence = prediction.confidence
            )
        }
        
        // 가장 높은 신뢰도를 가진 클래스를 기본 선택
        var selectedClass = ""
        var selectedConfidence = 0.0f
        
        if (classInfos.isNotEmpty()) {
            var bestClass: com.example.edgeaiapp.AnalysisResult.ClassInfo? = null
            var maxConfidence = 0.0f
            
            for (classInfo in classInfos) {
                if (classInfo.confidence > maxConfidence) {
                    maxConfidence = classInfo.confidence
                    bestClass = classInfo
                }
            }
            
            selectedClass = bestClass?.className ?: ""
            selectedConfidence = bestClass?.confidence ?: 0.0f
        }
        
        // 새로운 AnalysisResult 객체를 생성하여 불변성 보장
        val newResult = AnalysisResult(
            imageUri = originalResult.imageUri,
            selectedClass = selectedClass,
            confidence = selectedConfidence,
            allClasses = classInfos.toList(),
            isAnalyzed = true
        )
        
        Log.d("AnalyzeActivity", "Created new result - isAnalyzed: ${newResult.isAnalyzed}, selectedClass: '${newResult.selectedClass}', allClasses: ${newResult.allClasses.size}")
        return newResult
    }

    private fun uriToFile(uri: Uri): File {
        val inputStream = contentResolver.openInputStream(uri)
        val file = File(cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(file)
        
        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        
        return file
    }

    private fun sendFinalResults() {
        // 분석 완료된 결과만 필터링
        val completedResults = results.filter { it.isAnalyzed && it.selectedClass.isNotEmpty() && !isErrorMessage(it.selectedClass) }
        
        if (completedResults.isEmpty()) {
            Toast.makeText(this, "전송할 완료된 분석 결과가 없습니다.", Toast.LENGTH_LONG).show()
            return
        }
        
        Log.d("AnalyzeActivity", "=== FINAL RESULTS TRANSMISSION ===")
        Log.d("AnalyzeActivity", "Total results: ${results.size}, Completed results: ${completedResults.size}")
        
        // 사용자에게 확인 받기
        val dialogMessage = "총 ${completedResults.size}개의 분석 결과를 상품서버로 전송하시겠습니까?\n\n" +
                completedResults.take(3).joinToString("\n") { "• ${it.selectedClass} (${(it.confidence * 100).toInt()}%)" } +
                if (completedResults.size > 3) "\n• ... 및 ${completedResults.size - 3}개 더" else ""
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("최종 결과 전송")
            .setMessage(dialogMessage)
            .setPositiveButton("전송") { _, _ ->
                performFinalTransmissionToEdgeServer(completedResults)
            }
            .setNegativeButton("취소", null)
            .show()
    }
    
    /**
     * 엣지서버로 최종 결과를 전송하는 메서드 (엣지서버에서 상품서버 연동 처리)
     */
    private fun performFinalTransmissionToEdgeServer(completedResults: List<com.example.edgeaiapp.AnalysisResult>) {
        coroutineScope.launch {
            try {
                Log.d("AnalyzeActivity", "Starting final transmission to edge server...")
                
                // 전송 중 UI 업데이트
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AnalyzeActivity, "엣지서버로 최종 결과 전송 중...", Toast.LENGTH_SHORT).show()
                }
                
                // 엣지서버로 최종 결과 전송
                val transmissionData = prepareFinalTransmissionData(completedResults)
                val success = transmitFinalResultsToEdgeServer(transmissionData)
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@AnalyzeActivity, "최종 결과가 성공적으로 전송되었습니다!\n엣지서버에서 상품서버 연동을 처리합니다.", Toast.LENGTH_LONG).show()
                        Log.d("AnalyzeActivity", "Final transmission to edge server completed successfully")
                    } else {
                        Toast.makeText(this@AnalyzeActivity, "전송 중 오류가 발생했습니다. 다시 시도해주세요.", Toast.LENGTH_LONG).show()
                        Log.e("AnalyzeActivity", "Final transmission to edge server failed")
                    }
                }
                
            } catch (e: Exception) {
                Log.e("AnalyzeActivity", "Exception during product server transmission: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AnalyzeActivity, "전송 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    


    
    /**
     * 오류 메시지인지 확인하는 헬퍼 함수
     */
    private fun isErrorMessage(selectedClass: String): Boolean {
        val errorKeywords = listOf("오류", "실패", "파싱", "프로토콜", "네트워크", "처리", "응답", "연결", "HTTP")
        return errorKeywords.any { selectedClass.contains(it) }
    }
    
    /**
     * 원시 응답에서 수동으로 JSON 파싱을 시도하는 메서드
     * Retrofit 파싱이 실패했을 때 폴백으로 사용
     */
    private fun tryManualResponseParsing(response: retrofit2.Response<ImageAnalysisResponse>, originalResult: AnalysisResult): AnalysisResult {
        return try {
            // 원시 응답 텍스트 추출
            val rawResponseString = response.errorBody()?.string() ?: response.raw().body?.string()
            Log.d("AnalyzeActivity", "=== MANUAL PARSING ATTEMPT ===")
            Log.d("AnalyzeActivity", "Raw response: $rawResponseString")
            
            if (rawResponseString.isNullOrEmpty()) {
                Log.w("AnalyzeActivity", "Empty response body")
                return createFailureResult(originalResult, "빈 응답")
            }
            
            // JSON 파싱 시도
            val jsonObject = org.json.JSONObject(rawResponseString)
            Log.d("AnalyzeActivity", "JSON parsing successful")
            
            // success 필드 확인
            val success = jsonObject.optBoolean("success", false)
            Log.d("AnalyzeActivity", "Manual parsed success: $success")
            
            if (success) {
                // predictions 배열 파싱
                val predictionsArray = jsonObject.optJSONArray("predictions")
                val predictions = mutableListOf<com.example.edgeaiapp.AnalysisResult.ClassInfo>()
                
                if (predictionsArray != null) {
                    for (i in 0 until predictionsArray.length()) {
                        val predObj = predictionsArray.getJSONObject(i)
                        val className = predObj.optString("className", "Unknown")
                        val confidence = predObj.optDouble("confidence", 0.0).toFloat()
                        predictions.add(com.example.edgeaiapp.AnalysisResult.ClassInfo(className, confidence))
                        Log.d("AnalyzeActivity", "Manual parsed prediction: $className ($confidence)")
                    }
                }
                
                // 성공적으로 파싱된 경우 결과 생성
                return if (predictions.isNotEmpty()) {
                    val classInfos = predictions.map { 
                        AnalysisResult.ClassInfo(it.className, it.confidence) 
                    }
                    val bestPrediction = predictions.maxByOrNull { it.confidence } ?: predictions.first()
                    
                    AnalysisResult(
                        imageUri = originalResult.imageUri,
                        selectedClass = bestPrediction.className,
                        confidence = bestPrediction.confidence,
                        allClasses = classInfos,
                        isAnalyzed = true
                    )
                } else {
                    createFailureResult(originalResult, "예측 결과 없음")
                }
            } else {
                // 서버에서 실패 응답
                val message = jsonObject.optString("message", "분석 실패")
                createFailureResult(originalResult, "서버 응답: $message")
            }
            
        } catch (e: Exception) {
            Log.e("AnalyzeActivity", "Manual parsing failed: ${e.message}")
            createFailureResult(originalResult, "수동 파싱 실패: ${e.javaClass.simpleName}")
        }
    }
    
    /**
     * 프로토콜 오류에서 응답 복구를 시도하는 메서드
     */
    private fun tryRecoverFromProtocolError(protocolException: java.net.ProtocolException, originalResult: AnalysisResult, imageIndex: Int): AnalysisResult {
        Log.d("AnalyzeActivity", "=== PROTOCOL ERROR RECOVERY ATTEMPT ===")
        Log.d("AnalyzeActivity", "Attempting to recover from protocol error for image ${imageIndex + 1}")
        
        // 프로토콜 오류 메시지에서 유용한 정보 추출 시도
        val errorMessage = protocolException.message ?: ""
        Log.d("AnalyzeActivity", "Protocol error message: $errorMessage")
        
        // "unexpected end of stream" 오류의 경우 더 상세한 정보 제공
        return if (errorMessage.contains("unexpected end of stream")) {
            Log.w("AnalyzeActivity", "Detected 'unexpected end of stream' - server sent partial response")
            createFailureResult(originalResult, "부분 응답 수신 (서버 과부하 가능성)")
        } else {
            // 다른 프로토콜 오류의 경우
            createFailureResult(originalResult, "프로토콜 오류: $errorMessage")
        }
    }

    /**
     * 실패 결과 생성 헬퍼 메서드
     */
    private fun createFailureResult(originalResult: AnalysisResult, reason: String): AnalysisResult {
        return AnalysisResult(
            imageUri = originalResult.imageUri,
            isAnalyzed = false,
            selectedClass = reason,
            confidence = 0.0f,
            allClasses = emptyList()
        )
    }

    /**
     * UI 업데이트를 강제로 수행하는 메서드
     * 분석 완료 후 UI가 제대로 반영되지 않는 문제를 해결하기 위함
     */
    private fun forceUIUpdate() {
        runOnUiThread {
            Log.d("AnalyzeActivity", "=== Force UI Update Started ===")
            
            // 어댑터 데이터 새로고침
            adapter.notifyDataSetChanged()
            
            // RecyclerView 자체 새로고침
            val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
            recyclerView.invalidate()
            
            // 각 항목의 뷰를 개별적으로 새로고침
            Handler(Looper.getMainLooper()).postDelayed({
                for (i in 0 until results.size) {
                    adapter.notifyItemChanged(i)
                }
                Log.d("AnalyzeActivity", "Force UI Update completed for ${results.size} items")
            }, 50)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        when (requestCode) {
            REQUEST_SELECT_IMAGES -> {
                if (resultCode == RESULT_OK && data != null) {
                    // ImageSelectionActivity에서 선택된 이미지들 처리
                    val selectedImages = data.getParcelableArrayListExtra<Uri>("selected_images")
                    if (selectedImages != null) {
                        results.clear()
                        selectedImages.forEach { uri ->
                            results.add(com.example.edgeaiapp.AnalysisResult(imageUri = uri))
                        }
                        adapter.notifyDataSetChanged()
                    }
                }
            }
            REQUEST_EDIT_RESULTS -> {
                if (resultCode == RESULT_OK && data != null) {
                    // EditResultActivity에서 수정된 결과들 처리
                    val editedResults = data.getParcelableArrayListExtra<AnalysisResult>("edited_results")
                    if (editedResults != null) {
                        Log.d("AnalyzeActivity", "=== PROCESSING EDITED RESULTS ===")
                        Log.d("AnalyzeActivity", "Original results count: ${results.size}")
                        Log.d("AnalyzeActivity", "Edited results count: ${editedResults.size}")
                        
                        // 수정된 결과로 현재 결과 목록 업데이트
                        results.clear()
                        results.addAll(editedResults)
                        
                        // 각 수정된 결과 로그
                        editedResults.forEachIndexed { index, result ->
                            Log.d("AnalyzeActivity", "Edited[$index]: isAnalyzed=${result.isAnalyzed}, selectedClass='${result.selectedClass}', confidence=${result.confidence}")
                        }
                        
                        // 강력한 UI 업데이트
                        runOnUiThread {
                            Log.d("AnalyzeActivity", "Triggering UI updates...")
                            
                            // 1. 개별 아이템 업데이트
                            for (i in results.indices) {
                                adapter.notifyItemChanged(i)
                            }
                            
                            // 2. 전체 데이터셋 변경 알림 (지연)
                            Handler(Looper.getMainLooper()).postDelayed({
                        adapter.notifyDataSetChanged()
                                Log.d("AnalyzeActivity", "Full adapter refresh completed")
                            }, 100)
                            
                            // 3. RecyclerView 강제 업데이트
                            Handler(Looper.getMainLooper()).postDelayed({
                                forceUIUpdate()
                            }, 200)
                        }
                        
                        Toast.makeText(this, "결과가 업데이트되었습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        Log.w("AnalyzeActivity", "No edited results received")
                    }
                } else {
                    Log.w("AnalyzeActivity", "Edit result cancelled or no data")
                }
            }
            REQUEST_PRODUCT_SELECTION -> {
                // 상품 선택 및 전송 결과 처리
                if (resultCode == RESULT_OK && data != null) {
                    val transmissionSuccess = data.getBooleanExtra("transmission_success", false)
                    val selectedProduct = data.getStringExtra("selected_product")
                    val imageCount = data.getIntExtra("image_count", 0)
                    
                    if (transmissionSuccess) {
                        Toast.makeText(this, 
                            "전송 완료!\n상품: $selectedProduct\n이미지: ${imageCount}개", 
                            Toast.LENGTH_LONG).show()
                        
                        // 전송 완료 후 결과 초기화 (선택 사항)
                        // results.clear()
                        // adapter.notifyDataSetChanged()
                    } else {
                        Toast.makeText(this, "전송이 취소되었습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
    
    /**
     * 최종 전송할 데이터를 준비하는 메서드
     */
    private fun prepareFinalTransmissionData(completedResults: List<com.example.edgeaiapp.AnalysisResult>): Map<String, Any> {
        val transmissionData = mutableMapOf<String, Any>()
        
        // 기본 정보
        transmissionData["timestamp"] = System.currentTimeMillis()
        transmissionData["total_count"] = completedResults.size
        transmissionData["device_id"] = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        
        // 분석 결과 목록 (이미지 데이터 포함)
        val resultsList = completedResults.mapIndexed { index, result ->
            val imageData = convertImageToBase64(result.imageUri!!)
            mapOf(
                "id" to index,
                "selected_class" to result.selectedClass,
                "confidence" to result.confidence,
                "is_manual_edit" to (result.allClasses.isEmpty()), // 수동 편집 여부
                "image_uri" to result.imageUri.toString(),
                "image_data" to imageData // Base64 인코딩된 이미지 데이터
            )
        }
        transmissionData["analysis_results"] = resultsList
        
        // 통계 정보
        val classCount = completedResults.groupBy { it.selectedClass }.mapValues { it.value.size }
        transmissionData["class_statistics"] = classCount
        
        Log.d("AnalyzeActivity", "Prepared final transmission data: ${transmissionData.keys}")
        Log.d("AnalyzeActivity", "Class statistics: $classCount")
        
        return transmissionData
    }
    
    /**
     * 엣지서버로 최종 결과를 전송하는 메서드
     */
    private suspend fun transmitFinalResultsToEdgeServer(transmissionData: Map<String, Any>): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // HTTP 클라이언트 설정
                val transmissionClient = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                
                // JSON 데이터 준비
                val gson = com.google.gson.Gson()
                val jsonData = gson.toJson(transmissionData)
                
                val requestBody = okhttp3.RequestBody.create(
                    "application/json; charset=utf-8".toMediaTypeOrNull(),
                    jsonData
                )
                
                // 요청 생성 (엣지서버의 새로운 최종 전송 엔드포인트)
                val serverUrl = ServerUrlHelper.getFullServerUrl(this@AnalyzeActivity)
                val request = okhttp3.Request.Builder()
                    .url("$serverUrl/api/final-transmission")
                    .post(requestBody)
                    .addHeader("Authorization", if (authToken.startsWith("Bearer ")) authToken else "Bearer $authToken")
                    .addHeader("Content-Type", "application/json")
                    .build()
                
                Log.d("AnalyzeActivity", "Sending final results to edge server: $serverUrl/api/final-transmission")
                Log.d("AnalyzeActivity", "Data size: ${jsonData.length} bytes")
                
                // 요청 실행
                val response = transmissionClient.newCall(request).execute()
                
                Log.d("AnalyzeActivity", "Final transmission response: ${response.code}")
                
                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    Log.d("AnalyzeActivity", "Final transmission response body: $responseBody")
                    true
                } else {
                    val errorBody = response.body?.string()
                    Log.e("AnalyzeActivity", "Final transmission failed: ${response.code} - $errorBody")
                    false
                }
                
            } catch (e: Exception) {
                Log.e("AnalyzeActivity", "Final transmission exception: ${e.message}", e)
                false
            }
        }
    }
    
    /**
     * 이미지 URI를 Base64 문자열로 변환
     */
    private fun convertImageToBase64(imageUri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(imageUri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            
            if (bytes != null) {
                android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)
            } else {
                Log.e("AnalyzeActivity", "Failed to read image data from URI: $imageUri")
                null
            }
        } catch (e: Exception) {
            Log.e("AnalyzeActivity", "Error converting image to base64: ${e.message}", e)
            null
        }
    }

    companion object {
        private const val REQUEST_SELECT_IMAGES = 1001
        private const val REQUEST_EDIT_RESULTS = 1002
        private const val REQUEST_PRODUCT_SELECTION = 1003
    }
} 