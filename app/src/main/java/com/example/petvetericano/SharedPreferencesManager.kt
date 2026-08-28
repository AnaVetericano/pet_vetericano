package com.example.petvetericano

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("PetVetericanoPrefs", Context.MODE_PRIVATE)

    fun saveUserData(name: String, email: String, phone: String) {
        prefs.edit().apply {
            putString("USER_NAME", name)
            putString("USER_EMAIL", email)
            putString("USER_PHONE", phone)
            apply()
        }
    }

    fun getUserName(): String = prefs.getString("USER_NAME", "Smith Ordoñez") ?: "Smith Ordoñez"
    fun getUserEmail(): String = prefs.getString("USER_EMAIL", "ordonesjun@gmail.com") ?: "ordonesjun@gmail.com"
    fun getUserPhone(): String = prefs.getString("USER_PHONE", "+57 300 000 0000") ?: "+57 300 000 0000"

    fun setLanguage(lang: String) {
        prefs.edit().putString("APP_LANGUAGE", lang).apply()
    }

    fun getLanguage(): String = prefs.getString("APP_LANGUAGE", "es") ?: "es"

}