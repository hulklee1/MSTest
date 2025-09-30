package com.example.edgeaiapp

import android.content.Context
import android.content.SharedPreferences

object ServerUrlHelper {
    private const val PREF_NAME = "EdgeAIApp"
    private const val KEY_SERVER_URL = "server_url"
    private const val DEFAULT_SERVER_URL = "192.168.0.108:5000"
    
    /**
     * SharedPreferences에서 서버 URL을 가져옵니다
     */
    fun getServerUrl(context: Context): String {
        val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
    }
    
    /**
     * HTTP 프로토콜을 포함한 완전한 서버 URL을 반환합니다
     */
    fun getFullServerUrl(context: Context): String {
        val serverUrl = getServerUrl(context)
        return "http://$serverUrl"
    }
    
    /**
     * 서버 URL을 SharedPreferences에 저장합니다
     */
    fun setServerUrl(context: Context, url: String) {
        val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putString(KEY_SERVER_URL, url).apply()
    }
    
    /**
     * 기본 서버 URL을 반환합니다
     */
    fun getDefaultServerUrl(): String {
        return DEFAULT_SERVER_URL
    }
    
    /**
     * 서버 연결을 테스트하는 URL을 반환합니다
     */
    fun getTestConnectionUrl(context: Context): String {
        return "${getFullServerUrl(context)}/api/test"
    }
}
