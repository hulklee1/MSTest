package com.example.edgeaiapp

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ImageViewerActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ImageViewerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 2) // 2열 그리드

        val images = intent.getParcelableArrayListExtra<Uri>("images") ?: arrayListOf()
        adapter = ImageViewerAdapter(images)
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }
}

class ImageViewerAdapter(private val images: List<Uri>) : 
    RecyclerView.Adapter<ImageViewerAdapter.ViewHolder>() {

    class ViewHolder(val imageView: android.widget.ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val imageView = android.widget.ImageView(parent.context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                300
            )
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        }
        return ViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val imageUri = images[position]
        Glide.with(holder.imageView.context)
            .load(imageUri)
            .into(holder.imageView)
    }

    override fun getItemCount() = images.size
} 