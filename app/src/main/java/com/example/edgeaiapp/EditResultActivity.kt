package com.example.edgeaiapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class EditResultActivity : AppCompatActivity() {
    private lateinit var results: ArrayList<com.example.edgeaiapp.AnalysisResult>
    private lateinit var adapter: com.example.edgeaiapp.EditResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_result)

        // 전달받은 결과들 로드 (깊은 복사로 처리)
        val originalResults = intent.getParcelableArrayListExtra<com.example.edgeaiapp.AnalysisResult>("results") ?: arrayListOf()
        results = ArrayList()
        
        // 각 결과를 깊은 복사하여 새로운 리스트 생성
        originalResults.forEach { originalResult ->
            val copiedResult = com.example.edgeaiapp.AnalysisResult(
                imageUri = originalResult.imageUri,
                selectedClass = originalResult.selectedClass,
                confidence = originalResult.confidence,
                allClasses = originalResult.allClasses.toList(),
                isAnalyzed = originalResult.isAnalyzed
            )
            results.add(copiedResult)
        }
        
        setupRecyclerView()
        setupButtons()
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // RecyclerView 터치 이벤트 최적화
        recyclerView.isNestedScrollingEnabled = false
        recyclerView.setHasFixedSize(true)
        
        adapter = EditResultAdapter(results) { position, selectedClass ->
            // 클래스 선택 시 호출되는 콜백
            val result = results[position]
            result.selectedClass = selectedClass
            
            // confidence 설정: 원래 분석 결과에서 찾거나 기본값 사용
            result.confidence = if (result.allClasses.isNotEmpty()) {
                result.allClasses.find { it.className == selectedClass }?.confidence ?: 0.8f
            } else {
                0.8f // 수동 설정 시 기본 신뢰도
            }
            
            result.isAnalyzed = true // 수동 편집도 완료로 처리
            
            // EditResultAdapter에서 이미 UI 업데이트가 처리됨
        }
        
        recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        // 저장 버튼
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveResults()
        }

        // 취소 버튼
        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            finish()
        }
    }

    private fun saveResults() {
        // 편집된 결과를 AnalyzeActivity로 반환
        android.util.Log.d("EditResultActivity", "=== SAVING EDITED RESULTS ===")
        android.util.Log.d("EditResultActivity", "Results count: ${results.size}")
        
        // 각 결과의 상태 로그
        results.forEachIndexed { index, result ->
            android.util.Log.d("EditResultActivity", "Result[$index]: isAnalyzed=${result.isAnalyzed}, selectedClass='${result.selectedClass}', confidence=${result.confidence}")
        }
        
        val intent = Intent()
        intent.putParcelableArrayListExtra("edited_results", results)
        setResult(RESULT_OK, intent)

        Toast.makeText(this, getString(R.string.results_saved), Toast.LENGTH_SHORT).show()
        android.util.Log.d("EditResultActivity", "Results saved and activity finishing")
        finish()
    }
} 