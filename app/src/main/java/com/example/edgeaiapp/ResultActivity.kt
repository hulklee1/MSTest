package com.example.edgeaiapp

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class ResultActivity : AppCompatActivity() {
    private val results = mutableListOf<AnalysisResult>()
    private lateinit var adapter: AnalysisResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val images = intent.getParcelableArrayListExtra<Uri>("images") ?: arrayListOf()
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        adapter = AnalysisResultAdapter(results)
        recyclerView.adapter = adapter

        // 서버 전송 및 결과 수신
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val retrofit = Retrofit.Builder()
                    .baseUrl("http://엣지서버주소/") // 실제 주소로 변경
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val api = retrofit.create(ApiService::class.java)
                val parts = images.map { uriToMultipart(it, this@ResultActivity) }
                val response = api.uploadImages(parts)
                withContext(Dispatchers.Main) {
                    results.clear()
                    results.addAll(response.body() ?: emptyList())
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ResultActivity, "분석 실패", Toast.LENGTH_SHORT).show()
                }
            }
        }

        findViewById<Button>(R.id.btnEditResult).setOnClickListener {
            val intent = Intent(this, EditResultActivity::class.java)
            intent.putParcelableArrayListExtra("results", ArrayList(results))
            startActivity(intent)
        }
    }

    // Uri를 MultipartBody.Part로 변환하는 함수
    private fun uriToMultipart(uri: Uri, context: Context): MultipartBody.Part {
        val file = File(uri.path!!)
        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("images", file.name, requestFile)
    }
} 