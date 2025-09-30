package com.example.edgeaiapp

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import androidx.appcompat.view.ContextThemeWrapper

class ImageSelectionActivity : AppCompatActivity() {
    private val selectedUris = mutableListOf<Uri>()
    private val selectedIndices = mutableSetOf<Int>()
    private val deletedUris = mutableSetOf<String>() // 삭제된 URI 기록
    private var imageContainer: LinearLayout? = null
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var gson: Gson
    
    companion object {
        private const val TAG = "ImageSelectionActivity"
        private const val PREFS_NAME = "ImageSelectionPrefs"
        private const val KEY_IMAGE_URIS = "image_uris"
        private const val KEY_SELECTED_INDICES = "selected_indices"
        private const val KEY_DELETED_URIS = "deleted_uris"
    }
    
    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        try {
            if (uris != null) {
                Log.d(TAG, "선택된 이미지 개수: ${uris.size}")
                selectedUris.addAll(uris)
                saveImageUris()
                updateImageDisplay()
                Toast.makeText(this, "${uris.size}개의 이미지가 추가되었습니다.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "이미지 선택 중 오류 발생", e)
            Toast.makeText(this, "이미지 선택 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            
            // 액션 바 숨기기
            supportActionBar?.hide()
            
            setContentView(R.layout.activity_image_selection)
            
            Log.d(TAG, "ImageSelectionActivity 시작")
            
            // SharedPreferences 및 Gson 초기화
            sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            gson = Gson()
            
            // findViewById 결과 확인
            imageContainer = findViewById(R.id.imageContainer)
            if (imageContainer == null) {
                Log.e(TAG, "imageContainer를 찾을 수 없습니다.")
                Toast.makeText(this, "레이아웃 오류가 발생했습니다.", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            // 저장된 이미지들 로드
            loadSavedImages()
            
            // 기존 촬영된 이미지들 로드 (저장되지 않은 것들만)
            loadExistingImages()
            
            // 이미지 표시 업데이트
            updateImageDisplay()

            // 돌아가기 버튼
            val btnBack = findViewById<ImageButton>(R.id.btnBack)
            if (btnBack != null) {
                btnBack.setOnClickListener {
                    finish()
                }
            } else {
                Log.e(TAG, "btnBack을 찾을 수 없습니다.")
            }

            // 팝업메뉴 버튼
            val btnMenu = findViewById<ImageButton>(R.id.btnMenu)
            if (btnMenu != null) {
                btnMenu.setOnClickListener { view ->
                    showPopupMenu(view)
                }
            } else {
                Log.e(TAG, "btnMenu를 찾을 수 없습니다.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "onCreate 중 오류 발생", e)
            Toast.makeText(this, "앱 초기화 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        // 앱이 백그라운드로 갈 때 상태 저장
        saveImageUris()
        Log.d(TAG, "onPause: 상태 저장 완료")
    }

    override fun onResume() {
        super.onResume()
        try {
            // 앱이 포그라운드로 돌아올 때 상태 복원
            Log.d(TAG, "onResume: 앱 재시작됨")
            
            // imageContainer가 null인 경우 다시 초기화
            if (imageContainer == null) {
                Log.d(TAG, "onResume: imageContainer 재초기화")
                imageContainer = findViewById(R.id.imageContainer)
                if (imageContainer != null) {
                    updateImageDisplay()
                } else {
                    Log.e(TAG, "onResume: imageContainer를 찾을 수 없습니다.")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onResume 중 오류 발생", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 앱이 완전히 종료될 때 최종 상태 저장
        saveImageUris()
        Log.d(TAG, "onDestroy: 최종 상태 저장 완료")
    }

    private fun loadSavedImages() {
        try {
            Log.d(TAG, "저장된 이미지 로드 시작")
            
            val savedUrisJson = sharedPreferences.getString(KEY_IMAGE_URIS, null)
            Log.d(TAG, "저장된 URI JSON: $savedUrisJson")
            
            if (savedUrisJson != null && savedUrisJson.isNotEmpty()) {
                val type = object : TypeToken<List<String>>() {}.type
                val savedUris = gson.fromJson<List<String>>(savedUrisJson, type)
                Log.d(TAG, "파싱된 URI 개수: ${savedUris.size}")
                
                selectedUris.clear() // 기존 목록 초기화
                selectedUris.addAll(savedUris.map { Uri.parse(it) })
                Log.d(TAG, "저장된 이미지 로드 완료: ${selectedUris.size}개")
            } else {
                Log.d(TAG, "저장된 이미지가 없습니다.")
            }
            
            // 선택된 인덱스도 로드
            val savedIndicesJson = sharedPreferences.getString(KEY_SELECTED_INDICES, null)
            Log.d(TAG, "저장된 인덱스 JSON: $savedIndicesJson")
            
            if (savedIndicesJson != null && savedIndicesJson.isNotEmpty()) {
                val type = object : TypeToken<Set<Int>>() {}.type
                val savedIndices = gson.fromJson<Set<Int>>(savedIndicesJson, type)
                selectedIndices.clear() // 기존 선택 초기화
                selectedIndices.addAll(savedIndices)
                Log.d(TAG, "저장된 선택 인덱스 로드 완료: ${selectedIndices.size}개")
            } else {
                Log.d(TAG, "저장된 선택 인덱스가 없습니다.")
            }
            
            // 삭제된 URI들도 로드
            val deletedUrisJson = sharedPreferences.getString(KEY_DELETED_URIS, null)
            Log.d(TAG, "저장된 삭제된 URI JSON: $deletedUrisJson")
            
            if (deletedUrisJson != null && deletedUrisJson.isNotEmpty()) {
                val type = object : TypeToken<Set<String>>() {}.type
                val savedDeletedUris = gson.fromJson<Set<String>>(deletedUrisJson, type)
                deletedUris.clear() // 기존 삭제 기록 초기화
                deletedUris.addAll(savedDeletedUris)
                Log.d(TAG, "저장된 삭제된 URI 로드 완료: ${deletedUris.size}개")
            } else {
                Log.d(TAG, "저장된 삭제된 URI가 없습니다.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "저장된 데이터 로드 중 오류", e)
            // 오류 발생 시 기존 데이터 초기화
            selectedUris.clear()
            selectedIndices.clear()
            deletedUris.clear()
        }
    }

    private fun saveImageUris() {
        try {
            val urisJson = gson.toJson(selectedUris.map { it.toString() })
            val indicesJson = gson.toJson(selectedIndices)
            val deletedUrisJson = gson.toJson(deletedUris)
            
            Log.d(TAG, "저장할 데이터 - URI 개수: ${selectedUris.size}, 선택된 개수: ${selectedIndices.size}, 삭제된 개수: ${deletedUris.size}")
            
            sharedPreferences.edit()
                .putString(KEY_IMAGE_URIS, urisJson)
                .putString(KEY_SELECTED_INDICES, indicesJson)
                .putString(KEY_DELETED_URIS, deletedUrisJson)
                .apply()
            
            Log.d(TAG, "이미지 URI, 선택 상태, 삭제 기록 저장 완료")
        } catch (e: Exception) {
            Log.e(TAG, "데이터 저장 중 오류", e)
            Toast.makeText(this, "데이터 저장 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadExistingImages() {
        try {
            Log.d(TAG, "기존 이미지 로드 시작")
            
            // 앱의 외부 저장소에서 이미지 파일들 찾기
            val appDir = File(externalMediaDirs.first(), "Pictures")
            Log.d(TAG, "검색할 디렉토리: ${appDir.absolutePath}")
            Log.d(TAG, "디렉토리 존재 여부: ${appDir.exists()}")
            
            if (appDir.exists()) {
                val imageFiles = appDir.listFiles { file ->
                    file.isFile && (file.extension.lowercase() in listOf("jpg", "jpeg", "png", "gif"))
                }
                
                Log.d(TAG, "발견된 이미지 파일 개수: ${imageFiles?.size ?: 0}")
                
                var newImagesCount = 0
                imageFiles?.forEach { file ->
                    Log.d(TAG, "이미지 파일: ${file.absolutePath}")
                    val uri = Uri.fromFile(file)
                    val uriString = uri.toString()
                    
                    // 삭제된 이미지인지 확인
                    if (deletedUris.contains(uriString)) {
                        Log.d(TAG, "삭제된 이미지 건너뜀: $uriString")
                        return@forEach
                    }
                    
                    // 중복 체크
                    val isDuplicate = selectedUris.any { it.toString() == uriString }
                    
                    if (!isDuplicate) {
                        selectedUris.add(uri)
                        newImagesCount++
                        Log.d(TAG, "새 이미지 추가: $uriString")
                    } else {
                        Log.d(TAG, "중복 이미지 건너뜀: $uriString")
                    }
                }
                
                if (newImagesCount > 0) {
                    saveImageUris()
                    Log.d(TAG, "${newImagesCount}개의 새 이미지를 로드했습니다.")
                } else {
                    Log.d(TAG, "새로 추가된 이미지가 없습니다.")
                }
            } else {
                Log.d(TAG, "Pictures 디렉토리가 존재하지 않습니다.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "기존 이미지 로드 중 오류", e)
        }
    }

    private fun updateImageDisplay() {
        try {
            Log.d(TAG, "이미지 표시 업데이트 시작. 총 이미지 개수: ${selectedUris.size}")
            
            // imageContainer null 체크
            if (imageContainer == null) {
                Log.e(TAG, "imageContainer가 null입니다.")
                return
            }
            
            // 기존 이미지들 제거
            imageContainer?.removeAllViews()
            
            // 간단한 텍스트로 이미지 개수 표시 (디버깅용)
            val textView = TextView(this).apply {
                text = "총 ${selectedUris.size}개의 이미지가 있습니다."
                textSize = 18f
                setPadding(16, 16, 16, 16)
            }
            
            imageContainer?.addView(textView)
            
            // 각 이미지 URI 출력 (디버깅용)
            selectedUris.forEachIndexed { index, uri ->
                Log.d(TAG, "이미지 $index: $uri")
            }
            
            // 안전한 3열 그리드 구현 (LinearLayout 기반)
            var currentRow: LinearLayout? = null
            var imageCount = 0
            
            selectedUris.forEachIndexed { index, uri ->
                Log.d(TAG, "이미지 $index 처리 시작: $uri")
                
                if (imageCount % 3 == 0) {
                    // 새로운 행 생성
                    currentRow = LinearLayout(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                        orientation = LinearLayout.HORIZONTAL
                        weightSum = 3f
                    }
                    imageContainer?.addView(currentRow)
                    Log.d(TAG, "새 행 생성: $imageCount")
                }
                
                // 이미지 컨테이너 (고정된 크기로 설정)
                val itemContainer = android.widget.RelativeLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1.0f
                    ).apply {
                        marginEnd = 8
                        topMargin = 8
                    }
                }
                
                // 이미지 뷰 생성 (고정된 크기)
                val imageView = ImageView(this).apply {
                    id = android.view.View.generateViewId()
                    layoutParams = android.widget.RelativeLayout.LayoutParams(
                        android.widget.RelativeLayout.LayoutParams.MATCH_PARENT,
                        200
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = android.graphics.drawable.ColorDrawable(android.graphics.Color.LTGRAY)
                    
                    // 선택 상태에 따른 배경 설정
                    setOnClickListener {
                        toggleImageSelection(index, this)
                    }
                }
                
                // 선택 표시 오버레이 (작은 체크박스)
                val selectionOverlay = ImageView(this).apply {
                    id = android.view.View.generateViewId()
                    layoutParams = android.widget.RelativeLayout.LayoutParams(
                        40, // 작은 크기
                        40
                    ).apply {
                        addRule(android.widget.RelativeLayout.ALIGN_PARENT_END)
                        addRule(android.widget.RelativeLayout.ALIGN_PARENT_TOP)
                        topMargin = 8
                        rightMargin = 8
                    }
                    setImageResource(R.drawable.ic_check_selected)
                    background = android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#4CAF50"))
                    visibility = View.GONE
                }
                
                // 이미지 로드 (간단한 방식)
                try {
                    Log.d(TAG, "Glide로 이미지 로드 시작: $uri")
                    Glide.with(this)
                        .load(uri)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                        .centerCrop()
                        .override(200, 200)
                        .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                            override fun onLoadFailed(
                                e: com.bumptech.glide.load.engine.GlideException?,
                                model: Any?,
                                target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                                isFirstResource: Boolean
                            ): Boolean {
                                Log.e(TAG, "이미지 로드 실패: $uri", e)
                                imageView.setBackgroundColor(android.graphics.Color.RED)
                                return false
                            }

                            override fun onResourceReady(
                                resource: android.graphics.drawable.Drawable?,
                                model: Any?,
                                target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                                dataSource: com.bumptech.glide.load.DataSource?,
                                isFirstResource: Boolean
                            ): Boolean {
                                Log.d(TAG, "이미지 로드 성공: $uri")
                                imageView.background = null
                                return false
                            }
                        })
                        .into(imageView)
                } catch (e: Exception) {
                    Log.e(TAG, "이미지 로드 실패: $uri", e)
                    imageView.setBackgroundColor(android.graphics.Color.RED)
                }
                
                itemContainer.addView(imageView)
                itemContainer.addView(selectionOverlay)
                currentRow?.addView(itemContainer)
                
                // 선택 상태 복원
                if (selectedIndices.contains(index)) {
                    selectionOverlay.visibility = View.VISIBLE
                    imageView.alpha = 0.8f
                }
                
                imageCount++
                Log.d(TAG, "이미지 $index 처리 완료")
            }
            
            Log.d(TAG, "이미지 표시 업데이트 완료")
        } catch (e: Exception) {
            Log.e(TAG, "이미지 표시 업데이트 중 오류", e)
            Toast.makeText(this, "이미지 표시 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleImageSelection(index: Int, imageView: ImageView) {
        try {
            if (selectedIndices.contains(index)) {
                selectedIndices.remove(index)
                imageView.alpha = 1.0f
                // 선택 표시 제거
                (imageView.parent as? android.widget.RelativeLayout)?.getChildAt(1)?.visibility = View.GONE
            } else {
                selectedIndices.add(index)
                imageView.alpha = 0.8f
                // 선택 표시 추가
                (imageView.parent as? android.widget.RelativeLayout)?.getChildAt(1)?.visibility = View.VISIBLE
            }
            
            // 선택 상태 변경 시 저장
            saveImageUris()
            
            Log.d(TAG, "이미지 선택 토글: index=$index, 선택된 개수: ${selectedIndices.size}")
        } catch (e: Exception) {
            Log.e(TAG, "이미지 선택 토글 중 오류", e)
            Toast.makeText(this, "이미지 선택 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPopupMenu(view: View) {
        // 커스텀 테마를 적용하여 팝업메뉴 생성
        val popup = PopupMenu(ContextThemeWrapper(this, R.style.WhitePopupMenuOverlay), view)
        popup.menuInflater.inflate(R.menu.image_selection_popup_menu, popup.menu)
        
        // 팝업메뉴 배경색을 흰색으로 설정
        try {
            val fieldMPopup = PopupMenu::class.java.getDeclaredField("mPopup")
            fieldMPopup.isAccessible = true
            val mPopup = fieldMPopup.get(popup)
            mPopup.javaClass
                .getDeclaredMethod("setForceShowIcon", Boolean::class.java)
                .invoke(mPopup, true)
            
            // 팝업메뉴 배경색 설정
            val popupWindow = mPopup.javaClass
                .getDeclaredField("mPopup")
                .get(mPopup) as android.widget.PopupWindow
            
            // 흰색 배경 설정
            popupWindow.setBackgroundDrawable(resources.getDrawable(android.R.color.white, theme))
            
            // 팝업메뉴의 ListView 배경색도 설정
            val listView = popupWindow.contentView as? android.widget.ListView
            listView?.let { list ->
                list.setBackgroundColor(resources.getColor(android.R.color.white, theme))
                list.divider = null
                list.dividerHeight = 0
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "팝업메뉴 스타일 설정 실패", e)
        }
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_add_images -> {
                    pickImagesLauncher.launch("image/*")
                    true
                }
                R.id.action_clear_selected -> {
                    if (selectedIndices.isNotEmpty()) {
                        // 선택된 이미지들을 역순으로 삭제 (인덱스 변화 방지)
                        val sortedIndices = selectedIndices.sortedDescending()
                        sortedIndices.forEach { index ->
                            val uri = selectedUris[index]
                            val uriString = uri.toString()
                            
                            // 삭제 기록에 추가
                            deletedUris.add(uriString)
                            
                            // 실제 파일 삭제 시도
                            try {
                                val file = File(uri.path ?: "")
                                if (file.exists()) {
                                    val deleted = file.delete()
                                    Log.d(TAG, "파일 삭제 시도: ${file.absolutePath}, 성공: $deleted")
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "파일 삭제 실패: $uriString", e)
                            }
                            
                            selectedUris.removeAt(index)
                        }
                        selectedIndices.clear()
                        saveImageUris()
                        updateImageDisplay()
                        Toast.makeText(this, "선택된 이미지들이 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "삭제할 이미지를 먼저 선택하세요.", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.action_clear_all -> {
                    // 모든 이미지를 삭제 기록에 추가
                    selectedUris.forEach { uri ->
                        val uriString = uri.toString()
                        deletedUris.add(uriString)
                        
                        // 실제 파일 삭제 시도
                        try {
                            val file = File(uri.path ?: "")
                            if (file.exists()) {
                                val deleted = file.delete()
                                Log.d(TAG, "파일 삭제 시도: ${file.absolutePath}, 성공: $deleted")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "파일 삭제 실패: $uriString", e)
                        }
                    }
                    
                    selectedUris.clear()
                    selectedIndices.clear()
                    saveImageUris()
                    updateImageDisplay()
                    Toast.makeText(this, "모든 이미지가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_analyze -> {
                    if (selectedIndices.isNotEmpty()) {
                        val selectedImages = selectedIndices.map { selectedUris[it] }
                        val intent = Intent(this, AnalyzeActivity::class.java)
                        intent.putParcelableArrayListExtra("selected_images", ArrayList(selectedImages))
                        
                        // 인증 토큰 전달
                        val authToken = intent.getStringExtra("auth_token") ?: sharedPreferences.getString("auth_token", null)
                        if (!authToken.isNullOrEmpty()) {
                            intent.putExtra("auth_token", authToken)
                        }
                        
                        startActivity(intent)
                    } else {
                        Toast.makeText(this, "분석할 이미지를 먼저 선택하세요.", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
} 