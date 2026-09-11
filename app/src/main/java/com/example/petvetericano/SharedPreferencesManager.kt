package com.example.petvetericano

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("PetVetericanoPrefs", Context.MODE_PRIVATE)

    fun saveUserData(name: String, email: String, phone: String) {
        prefs.edit().apply {
            if (name.isNotEmpty())  putString("USER_NAME",  name)
            if (email.isNotEmpty()) putString("USER_EMAIL", email)
            if (phone.isNotEmpty()) putString("USER_PHONE", phone)
            apply()
        }
    }

    fun getUserName():  String = prefs.getString("USER_NAME",  "") ?: ""
    fun getUserEmail(): String = prefs.getString("USER_EMAIL", "") ?: ""
    fun getUserPhone(): String = prefs.getString("USER_PHONE", "") ?: ""

    // Foto de perfil
    fun saveProfileImagePath(path: String) {
        prefs.edit().putString("PROFILE_IMAGE_PATH", path).apply()
    }

    fun getProfileImagePath(): String = prefs.getString("PROFILE_IMAGE_PATH", "") ?: ""

    // Token JWT
    fun saveAccessToken(token: String) {
        prefs.edit().putString("ACCESS_TOKEN", token).apply()
    }

    fun getAccessToken(): String = prefs.getString("ACCESS_TOKEN", "") ?: ""

    fun setLanguage(lang: String) {
        prefs.edit().putString("APP_LANGUAGE", lang).apply()
    }

    fun getLanguage(): String = prefs.getString("APP_LANGUAGE", "es") ?: "es"

}