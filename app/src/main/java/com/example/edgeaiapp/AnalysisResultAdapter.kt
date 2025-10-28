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

class AnalysisResultAdapter(
    private val results: MutableList<AnalysisResult>,
    private val onClassSelected: ((Int, String) -> Unit)? = null,
    private val onImageDeleted: ((Int) -> Unit)? = null
) : RecyclerView.Adapter<AnalysisResultAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        val selectedClassText: TextView = itemView.findViewById(R.id.selectedClassText)
        val confidenceText: TextView = itemView.findViewById(R.id.confidenceText)
        val classSpinner: Spinner = itemView.findViewById(R.id.classSpinner)
        val statusText: TextView = itemView.findViewById(R.id.statusText)
        val deleteButton: ImageView? = itemView.findViewById(R.id.deleteButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_analysis_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = results[position]
        
        // 상태 디버깅 로그
        Log.d("AnalysisAdapter", "Binding position $position: isAnalyzed=${result.isAnalyzed}, selectedClass='${result.selectedClass}', confidence=${result.confidence}")
        
        // 이미지 로드
        Glide.with(holder.imageView.context)
            .load(result.imageUri)
            .into(holder.imageView)
        
        // 삭제 버튼 설정
        holder.deleteButton?.let { deleteBtn ->
            deleteBtn.visibility = View.VISIBLE
            deleteBtn.setOnClickListener {
                onImageDeleted?.invoke(position)
            }
        }
        
        // 분석 상태에 따른 UI 업데이트
        Log.d("AnalysisAdapter", "=== UI UPDATE for position $position ===")
        Log.d("AnalysisAdapter", "result.isAnalyzed = ${result.isAnalyzed}")
        Log.d("AnalysisAdapter", "result.selectedClass = '${result.selectedClass}'")
        Log.d("AnalysisAdapter", "result.allClasses.size = ${result.allClasses.size}")
        
        if (result.isAnalyzed) {
            // 분석 완료된 경우 (자동 분석 또는 수동 편집)
            Log.d("AnalysisAdapter", "Setting ANALYZED state for position $position")
            val displayClass = result.selectedClass.ifEmpty { 
                holder.itemView.context.getString(R.string.no_class_selected) 
            }
            holder.selectedClassText.text = displayClass
            holder.confidenceText.text = "${(result.confidence * 100).toInt()}%"
            
            // 상태 텍스트 설정
            holder.statusText.text = if (result.allClasses.isNotEmpty()) {
                holder.itemView.context.getString(R.string.analysis_complete)
            } else {
                "수동 분류 완료"
            }
            holder.statusText.setTextColor(holder.itemView.context.getColor(android.R.color.holo_green_dark))
            
            Log.d("AnalysisAdapter", "UI elements set - selectedClass: '$displayClass', confidence: ${(result.confidence * 100).toInt()}%")
            
            // 클래스 선택 스피너 설정
            if (result.allClasses.isNotEmpty()) {
                // 자동 분석 결과가 있는 경우
                val classNames = result.allClasses.map { it.className }
                val spinnerAdapter = ArrayAdapter(
                    holder.itemView.context,
                    android.R.layout.simple_spinner_item,
                    classNames
                )
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                holder.classSpinner.adapter = spinnerAdapter

                // 현재 선택된 클래스의 인덱스 찾기
                val selectedIndex = result.allClasses.indexOfFirst { it.className == result.selectedClass }
                if (selectedIndex >= 0) {
                    holder.classSpinner.setSelection(selectedIndex)
                }
            } else {
                // 수동 편집된 경우 (allClasses가 비어있음)
                Log.d("AnalysisAdapter", "Manual edit result - disabling spinner")
                holder.classSpinner.adapter = null
                holder.classSpinner.isEnabled = false
            }
            
            // RecyclerView 터치 충돌 해결을 위한 강화된 설정
            holder.classSpinner.setOnTouchListener { v, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        // 모든 상위 뷰의 터치 이벤트 가로채기 방지
                        var parent = v.parent
                        while (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true)
                            parent = parent.parent
                        }
                    }
                    android.view.MotionEvent.ACTION_UP, 
                    android.view.MotionEvent.ACTION_CANCEL,
                    android.view.MotionEvent.ACTION_OUTSIDE -> {
                        // 터치 이벤트 정상화
                        var parent = v.parent
                        while (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(false)
                            parent = parent.parent
                        }
                    }
                }
                false
            }
            
            // 스피너가 활성화된 경우에만 리스너 설정
            if (result.allClasses.isNotEmpty()) {
                // 기존 리스너 제거 후 새로 설정 (중복 방지)
                holder.classSpinner.onItemSelectedListener = null
                
                // 클래스 선택 리스너
                holder.classSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    private var isUserSelection = false
                    
                    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                        // 초기 설정이 아닌 사용자 선택인 경우만 처리
                        if (isUserSelection && position < result.allClasses.size) {
                            val selectedClass = result.allClasses[position].className
                            val selectedConfidence = result.allClasses[position].confidence

                            result.selectedClass = selectedClass
                            result.confidence = selectedConfidence

                            holder.selectedClassText.text = selectedClass
                            holder.confidenceText.text = "${(selectedConfidence * 100).toInt()}%"

                            onClassSelected?.invoke(holder.adapterPosition, selectedClass)
                        }
                        isUserSelection = true
                    }

                    override fun onNothingSelected(parent: AdapterView<*>?) {
                        // 아무것도 선택되지 않았을 때의 처리
                    }
                }
            }
        } else if (result.selectedClass.isNotEmpty() && 
                   (result.selectedClass.contains("오류") || 
                    result.selectedClass.contains("실패") || 
                    result.selectedClass.contains("파싱") ||
                    result.selectedClass.contains("프로토콜") ||
                    result.selectedClass.contains("네트워크") ||
                    result.selectedClass.contains("처리") ||
                    result.selectedClass.contains("응답") ||
                    result.selectedClass.contains("연결") ||
                    result.selectedClass.contains("HTTP") ||
                    !result.isAnalyzed)) {
            // 분석 실패/오류 상태 (더 포괄적 판단)
            Log.d("AnalysisAdapter", "Setting ERROR state for position $position")
            holder.selectedClassText.text = result.selectedClass
            holder.confidenceText.text = "0%"
            holder.statusText.text = "분석 실패"
            holder.statusText.setTextColor(holder.itemView.context.getColor(android.R.color.holo_red_dark))

            // 스피너 비활성화 및 초기화
            holder.classSpinner.isEnabled = false
            holder.classSpinner.adapter = null
            holder.classSpinner.onItemSelectedListener = null
            
            Log.d("AnalysisAdapter", "ERROR state UI elements set for position $position")
        } else {
            // 진짜 분석 대기중인 경우 (selectedClass가 비어있고 isAnalyzed가 false)
            if (result.selectedClass.isEmpty() && !result.isAnalyzed) {
                Log.d("AnalysisAdapter", "Setting WAITING state for position $position")
                holder.selectedClassText.text = holder.itemView.context.getString(R.string.analysis_waiting)
                holder.confidenceText.text = "0.0%"
                holder.statusText.text = holder.itemView.context.getString(R.string.waiting_for_analysis)
                holder.statusText.setTextColor(holder.itemView.context.getColor(android.R.color.holo_orange_dark))
            } else {
                // 알 수 없는 상태 - 디버깅용
                Log.w("AnalysisAdapter", "Unknown state for position $position - treating as error")
                Log.w("AnalysisAdapter", "Details: isAnalyzed=${result.isAnalyzed}, selectedClass='${result.selectedClass}', allClasses.size=${result.allClasses.size}")
                holder.selectedClassText.text = "알 수 없는 상태: ${result.selectedClass}"
                holder.confidenceText.text = "0%"
                holder.statusText.text = "상태 확인 필요"
                holder.statusText.setTextColor(holder.itemView.context.getColor(android.R.color.holo_red_dark))
            }

            // 스피너 비활성화 및 초기화
            holder.classSpinner.isEnabled = false
            holder.classSpinner.adapter = null
            holder.classSpinner.onItemSelectedListener = null
            
            Log.d("AnalysisAdapter", "Non-success state UI elements set for position $position")
        }
    }

    override fun getItemCount() = results.size
} 