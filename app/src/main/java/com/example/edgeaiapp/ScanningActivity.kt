package com.example.edgeaiapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class ScanningActivity : AppCompatActivity() {
    private val selectedUris = mutableListOf<Uri>()
    private lateinit var productTypeSpinner: Spinner
    private lateinit var selectedImagesTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanning)
        
        productTypeSpinner = findViewById(R.id.productTypeSpinner)
        selectedImagesTextView = findViewById(R.id.selectedImagesTextView)

        // 상품 종류 스피너 설정
        val productTypes = arrayOf("BANANA", "APPLE", "ORANGE", "GRAPE", "STRAWBERRY")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, productTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        productTypeSpinner.adapter = adapter

        // 이미지 선택 버튼
        findViewById<Button>(R.id.btnSelectImages).setOnClickListener {
            val intent = Intent(this, ImageSelectionActivity::class.java)
            startActivityForResult(intent, REQUEST_SELECT_IMAGES)
        }

        // 일괄 전송 버튼
        findViewById<Button>(R.id.btnSendBatch).setOnClickListener {
            if (selectedUris.isNotEmpty()) {
                val selectedProduct = productTypeSpinner.selectedItem.toString()
                sendBatchImages(selectedProduct)
            } else {
                Toast.makeText(this, "전송할 이미지를 먼저 선택하세요", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sendBatchImages(productType: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl("http://엣지서버주소/") // 실제 주소로 변경
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val api = retrofit.create(ApiService::class.java)
                
                val parts = selectedUris.map { uriToMultipart(it, this@ScanningActivity) }
                val response = api.sendBatchImages(parts, productType, null) // 토큰을 null로 전달
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(this@ScanningActivity, "일괄 전송 완료", Toast.LENGTH_SHORT).show()
                        selectedUris.clear()
                        updateSelectedImagesText()
                        finish()
                    } else {
                        Toast.makeText(this@ScanningActivity, "전송 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ScanningActivity, "네트워크 오류", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun uriToMultipart(uri: Uri, context: Context): MultipartBody.Part {
        val file = File(uri.path!!)
        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("images", file.name, requestFile)
    }

    private fun updateSelectedImagesText() {
        selectedImagesTextView.text = "선택된 이미지: ${selectedUris.size}개"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_SELECT_IMAGES && resultCode == RESULT_OK) {
            val uris = data?.getParcelableArrayListExtra<Uri>("selected_images")
            if (uris != null) {
                selectedUris.clear()
                selectedUris.addAll(uris)
                updateSelectedImagesText()
            }
        }
    }

    companion object {
        private const val REQUEST_SELECT_IMAGES = 1001
    }
} 