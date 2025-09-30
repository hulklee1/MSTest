package com.example.edgeaiapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts

class GalleryActivity : AppCompatActivity() {
    private val selectedUris = mutableListOf<Uri>()
    private lateinit var selectedImagesTextView: TextView
    
    private val pickImagesLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris != null) {
            selectedUris.clear()
            selectedUris.addAll(uris)
            updateSelectedImagesText()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gallery)

        selectedImagesTextView = findViewById(R.id.selectedImagesTextView)

        findViewById<Button>(R.id.btnPickImages).setOnClickListener {
            pickImagesLauncher.launch("image/*")
        }

        findViewById<Button>(R.id.btnViewImages).setOnClickListener {
            if (selectedUris.isNotEmpty()) {
                // 이미지 뷰어로 이동 (선택된 이미지들 표시)
                val intent = Intent(this, ImageViewerActivity::class.java)
                intent.putParcelableArrayListExtra("images", ArrayList(selectedUris))
                startActivity(intent)
            }
        }

        findViewById<Button>(R.id.btnClearImages).setOnClickListener {
            selectedUris.clear()
            updateSelectedImagesText()
        }
    }

    private fun updateSelectedImagesText() {
        selectedImagesTextView.text = "선택된 이미지: ${selectedUris.size}개"
    }
} 