package com.example.edgeaiapp

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var etServerUrl: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        // 액션 바 설정
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "환경설정"
        
        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("EdgeAIApp", MODE_PRIVATE)
        
        // 뷰 초기화
        initViews()
        
        // 현재 설정값 로드
        loadCurrentSettings()
        
        // 버튼 리스너 설정
        setupListeners()
    }
    
    private fun initViews() {
        etServerUrl = findViewById(R.id.etServerUrl)
        btnSave = findViewById(R.id.btnSave)
        btnCancel = findViewById(R.id.btnCancel)
    }
    
    private fun loadCurrentSettings() {
        // 현재 저장된 서버 URL 로드 (기본값: 192.168.0.108:5000)
        val currentServerUrl = sharedPreferences.getString("server_url", "192.168.0.108:5000") ?: "192.168.0.108:5000"
        etServerUrl.setText(currentServerUrl)
        
        // TextInputLayout의 hint를 비워서 중복 표시 방지
        val textInputLayout = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilServerUrl)
        textInputLayout.hint = ""
    }
    
    private fun setupListeners() {
        btnSave.setOnClickListener {
            saveSettings()
        }
        
        btnCancel.setOnClickListener {
            finish()
        }
    }
    
    private fun saveSettings() {
        val serverUrl = etServerUrl.text.toString().trim()
        
        if (serverUrl.isEmpty()) {
            Toast.makeText(this, "서버 URL을 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 간단한 URL 유효성 검사
        if (!isValidServerUrl(serverUrl)) {
            Toast.makeText(this, "올바른 서버 URL 형식을 입력해주세요\n예: 192.168.1.100:5000", Toast.LENGTH_LONG).show()
            return
        }
        
        // SharedPreferences에 저장
        sharedPreferences.edit()
            .putString("server_url", serverUrl)
            .apply()
        
        Toast.makeText(this, "설정이 저장되었습니다", Toast.LENGTH_SHORT).show()
        finish()
    }
    
    private fun isValidServerUrl(url: String): Boolean {
        // 기본적인 IP:PORT 형식 검증
        val pattern = Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{1,5}$")
        return pattern.matches(url)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}
