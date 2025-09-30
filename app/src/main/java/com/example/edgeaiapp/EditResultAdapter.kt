package com.example.edgeaiapp

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.AdapterView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

/**
 * 분석결과 수정 화면 전용 어댑터
 * 실패한 항목도 수동으로 편집할 수 있도록 지원
 */
class EditResultAdapter(
    private val results: MutableList<AnalysisResult>,
    private val onClassSelected: ((Int, String) -> Unit)? = null
) : RecyclerView.Adapter<EditResultAdapter.ViewHolder>() {

    // 고정된 클래스 목록 (10개 과일/채소 - 한글)
    private val fixedClasses = listOf(
        "apple",
        "banana",
        "korean melon",
        "korean radish",
        "melon",
        "orange",
        "peach",
        "pear",
        "plum",
        "zucchini"
    )
    


    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        val selectedClassText: TextView = itemView.findViewById(R.id.selectedClassText)
        val confidenceText: TextView = itemView.findViewById(R.id.confidenceText)
        val statusText: TextView = itemView.findViewById(R.id.statusText)
        val classSpinner: Spinner = itemView.findViewById(R.id.classSpinner)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_analysis_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = results[position]
        
        Log.d("EditResultAdapter", "=== EDIT MODE BINDING for position $position ===")
        Log.d("EditResultAdapter", "isAnalyzed = ${result.isAnalyzed}")
        Log.d("EditResultAdapter", "selectedClass = '${result.selectedClass}'")
        Log.d("EditResultAdapter", "allClasses.size = ${result.allClasses.size}")
        
        // 이미지 로드
        Glide.with(holder.imageView.context)
            .load(result.imageUri)
            .into(holder.imageView)
        
        // 편집 모드에서는 모든 항목을 편집 가능하게 처리
        setupEditableItem(holder, result, position)
    }
    
    private fun setupEditableItem(holder: ViewHolder, result: AnalysisResult, position: Int) {
        // 편집 화면에서는 항상 고정된 클래스 목록 사용
        val availableClasses = fixedClasses
        Log.d("EditResultAdapter", "Using fixed classes for editing: $availableClasses")
        
        // 현재 선택된 클래스 설정 (한글)
        val currentSelectedClass = when {
            result.selectedClass.isNotEmpty() && !isErrorMessage(result.selectedClass) -> {
                // 기존 선택된 클래스가 있으면 그대로 사용 (이미 한글)
                if (fixedClasses.contains(result.selectedClass)) {
                    result.selectedClass
                } else {
                    // 기본값 사용
                    fixedClasses.first()
                }
            }
            result.allClasses.isNotEmpty() -> {
                // 분석 결과가 있는 경우, 첫 번째 결과 사용 (이미 한글)
                result.allClasses.first().className
            }
            else -> {
                // 기본값: 첫 번째 클래스 (한글)
                fixedClasses.first()
            }
        }
        
        // UI 업데이트
        holder.selectedClassText.text = currentSelectedClass
        holder.confidenceText.text = if (result.isAnalyzed) {
            "${(result.confidence * 100).toInt()}%"
        } else {
            "수동 설정"
        }
        
        // 상태 표시
        holder.statusText.text = if (result.isAnalyzed) {
            "분석 완료 (편집 가능)"
        } else {
            "수동 분류 (편집 가능)"
        }
        holder.statusText.setTextColor(holder.itemView.context.getColor(android.R.color.holo_blue_dark))
        
        // 스피너 설정 (모든 항목에 대해 활성화)
        setupSpinner(holder, result, availableClasses, currentSelectedClass, position)
    }
    
    private fun setupSpinner(
        holder: ViewHolder, 
        result: AnalysisResult, 
        availableClasses: List<String>, 
        currentSelectedClass: String, 
        position: Int
    ) {
        Log.d("EditResultAdapter", "Setting up spinner for position $position with classes: $availableClasses")
        
        val spinnerAdapter = ArrayAdapter(
            holder.itemView.context,
            android.R.layout.simple_spinner_item,
            availableClasses
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        holder.classSpinner.adapter = spinnerAdapter
        
        // 현재 선택된 클래스의 인덱스 찾기
        val selectedIndex = availableClasses.indexOf(currentSelectedClass)
        if (selectedIndex >= 0) {
            holder.classSpinner.setSelection(selectedIndex)
        }
        
        // 스피너 활성화
        holder.classSpinner.isEnabled = true
        
        // 스피너 터치 이벤트 설정
        holder.classSpinner.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    var parent = v.parent
                    while (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true)
                        parent = parent.parent
                    }
                }
                android.view.MotionEvent.ACTION_UP, 
                android.view.MotionEvent.ACTION_CANCEL,
                android.view.MotionEvent.ACTION_OUTSIDE -> {
                    var parent = v.parent
                    while (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(false)
                        parent = parent.parent
                    }
                }
            }
            false
        }
        
        // 스피너 선택 리스너
        holder.classSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var isUserSelection = false
            
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isUserSelection && position < availableClasses.size) {
                    val selectedClassName = availableClasses[position] // 한글 클래스명
                    
                    Log.d("EditResultAdapter", "User selected class: $selectedClassName for item ${holder.adapterPosition}")
                    
                    // 결과 업데이트 (한글로 저장)
                    result.selectedClass = selectedClassName
                    
                    // confidence 설정
                    result.confidence = if (result.allClasses.isNotEmpty()) {
                        // 원래 분석 결과에서 해당 클래스의 신뢰도 찾기
                        result.allClasses.find { it.className == selectedClassName }?.confidence ?: 0.8f
                    } else {
                        // 수동 설정 시 기본 신뢰도
                        0.8f
                    }
                    
                    // isAnalyzed 상태 업데이트 (수동 편집도 완료된 것으로 처리)
                    result.isAnalyzed = true
                    
                    // UI 업데이트
                    holder.selectedClassText.text = selectedClassName
                    holder.confidenceText.text = "${(result.confidence * 100).toInt()}%"
                    holder.statusText.text = "편집 완료"
                    
                    // 콜백 호출
                    onClassSelected?.invoke(holder.adapterPosition, selectedClassName)
                    
                    Log.d("EditResultAdapter", "Updated result: isAnalyzed=${result.isAnalyzed}, selectedClass='${result.selectedClass}', confidence=${result.confidence}")
                }
                
                // 사용자 선택 플래그 활성화 (초기 설정 후)
                isUserSelection = true
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {
                // 처리 없음
            }
        }
        
        Log.d("EditResultAdapter", "Spinner setup completed for position $position")
    }
    
    /**
     * 오류 메시지인지 확인하는 헬퍼 함수
     */
    private fun isErrorMessage(selectedClass: String): Boolean {
        val errorKeywords = listOf("오류", "실패", "파싱", "프로토콜", "네트워크", "처리", "응답", "연결", "HTTP")
        return errorKeywords.any { selectedClass.contains(it) }
    }

    override fun getItemCount() = results.size
}
