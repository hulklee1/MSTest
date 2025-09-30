package com.example.edgeaiapp

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast

class MainActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private var authToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 액션 바 숨기기
        supportActionBar?.hide()
        
        setContentView(R.layout.activity_main)

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("EdgeAIApp", MODE_PRIVATE)
        
        // 인텐트에서 토큰 받기
        authToken = intent.getStringExtra("auth_token")
        
        // 토큰이 없으면 SharedPreferences에서 확인
        if (authToken.isNullOrEmpty()) {
            authToken = sharedPreferences.getString("auth_token", null)
        }
        
        // 토큰이 여전히 없으면 로그인 화면으로 이동
        if (authToken.isNullOrEmpty()) {
            Toast.makeText(this, "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
            startLoginActivity()
            return
        }

        // 사용자명 표시 (토큰에서 추출하거나 기본값 사용)
        val usernameTextView = findViewById<TextView>(R.id.tvUsername)
        usernameTextView?.text = "환영합니다!"

        // 사진찍기 버튼
        findViewById<Button>(R.id.btnTakePhoto).setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            intent.putExtra("auth_token", authToken)
            startActivity(intent)
        }

        // 갤러리 버튼
        findViewById<Button>(R.id.btnGallery).setOnClickListener {
            try {
                val intent = Intent(this, ImageSelectionActivity::class.java)
                intent.putExtra("auth_token", authToken)
                startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "ImageSelectionActivity 시작 중 오류", e)
                android.widget.Toast.makeText(this, "갤러리를 열 수 없습니다: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // 분석하기 버튼
        findViewById<Button>(R.id.btnAnalyze).setOnClickListener {
            try {
                val intent = Intent(this, ImageSelectionActivity::class.java)
                intent.putExtra("auth_token", authToken)
                startActivity(intent)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "ImageSelectionActivity 시작 중 오류", e)
                android.widget.Toast.makeText(this, getString(R.string.select_images_for_analysis), android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // 상품관리 버튼 (웹페이지 열기)
        findViewById<Button>(R.id.btnScanning).setOnClickListener {
            openProductManagementWebsite()
        }
        
        
        // 로그아웃 버튼
        findViewById<Button>(R.id.btnLogout)?.setOnClickListener {
            logout()
        }
    }
    
    private fun logout() {
        // 토큰 제거
        sharedPreferences.edit().remove("auth_token").apply()
        authToken = null
        
        // 로그인 화면으로 이동
        startLoginActivity()
    }
    
    private fun startLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
    
    private fun openProductManagementWebsite() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aimd.esjee.co.kr"))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "웹페이지를 열 수 없습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 토큰이 만료되었는지 확인
        val currentToken = sharedPreferences.getString("auth_token", null)
        if (currentToken.isNullOrEmpty()) {
            startLoginActivity()
        }
    }
} 