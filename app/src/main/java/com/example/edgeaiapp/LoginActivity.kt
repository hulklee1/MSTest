package com.example.edgeaiapp

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class LoginActivity : AppCompatActivity() {
    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var sharedPreferences: SharedPreferences
    

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 액션 바 숨기기
        supportActionBar?.hide()
        
        setContentView(R.layout.activity_login)

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("EdgeAIApp", MODE_PRIVATE)
        
        // 이미 로그인되어 있다면 메인 화면으로 이동
        val savedToken = sharedPreferences.getString("auth_token", null)
        if (!savedToken.isNullOrEmpty()) {
            startMainActivity(savedToken)
            return
        }

        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)

        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()
            
            if (username.isNotEmpty() && password.isNotEmpty()) {
                // 실제 서버 로그인 처리
                performLogin(username, password)
            } else {
                Toast.makeText(this, "사용자명과 비밀번호를 입력하세요", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 엣지서버 설정 버튼
        findViewById<Button>(R.id.btnSettings)?.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun performSimpleLogin(username: String, password: String) {
        // 임시 로그인 처리 (테스트용)
        if (username == "admin" && password == "admin123") {
            Toast.makeText(this, "로그인 성공! (테스트 모드)", Toast.LENGTH_SHORT).show()
            // 테스트용 토큰 생성
            val testToken = "test_token_${System.currentTimeMillis()}"
            sharedPreferences.edit().putString("auth_token", testToken).apply()
            startMainActivity(testToken)
        } else {
            Toast.makeText(this, "잘못된 사용자명 또는 비밀번호", Toast.LENGTH_SHORT).show()
        }
    }


    private fun performLogin(username: String, password: String) {
        // 로딩 표시
        loginButton.isEnabled = false
        loginButton.text = "로그인 중..."
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val serverUrl = ServerUrlHelper.getFullServerUrl(this@LoginActivity)
                val retrofit = Retrofit.Builder()
                    .baseUrl("$serverUrl/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                val api = retrofit.create(ApiService::class.java)

                val loginRequest = LoginRequest(username, password)
                val response = api.login(loginRequest)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val responseBody = response.body()
                        if (responseBody?.success == true && responseBody.token != null) {
                            // 토큰을 SharedPreferences에 저장 (Bearer 접두사 제거)
                            val cleanToken = responseBody.token.removePrefix("Bearer ").removePrefix("bearer ")
                            sharedPreferences.edit().putString("auth_token", cleanToken).apply()
                            
                            // 로그인 성공 시 메인 화면으로 이동
                            startMainActivity(cleanToken)
                        } else {
                            Toast.makeText(this@LoginActivity, responseBody?.message ?: "로그인 실패", Toast.LENGTH_SHORT).show()
                            resetLoginButton()
                        }
                    } else {
                        Toast.makeText(this@LoginActivity, "로그인 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                        resetLoginButton()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@LoginActivity, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                    resetLoginButton()
                }
            }
        }
    }
    
    private fun startMainActivity(token: String) {
        val intent = Intent(this@LoginActivity, MainActivity::class.java)
        intent.putExtra("auth_token", token)
        startActivity(intent)
        finish()
    }
    
    private fun resetLoginButton() {
        loginButton.isEnabled = true
        loginButton.text = "로그인"
    }
} 