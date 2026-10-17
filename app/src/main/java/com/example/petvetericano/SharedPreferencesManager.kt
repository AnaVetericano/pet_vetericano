package com.example.petvetericano

import android.content.Context
import android.content.SharedPreferences

class SharedPreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("PetVetericanoPrefs", Context.MODE_PRIVATE)
    private val googlePrefs: SharedPreferences =
        context.getSharedPreferences("PetVetericanoGoogleAuthPrefs", Context.MODE_PRIVATE)

    fun saveGoogleAccountPassword(email: String, password: String) {
        if (email.isNotEmpty() && password.isNotEmpty()) {
            val cleanEmail = email.lowercase().trim()
            googlePrefs.edit().putString("GOOGLE_CRED_$cleanEmail", password).apply()
            saveKnownGoogleAccount(cleanEmail)
        }
    }

    fun getGoogleAccountPassword(email: String): String {
        if (email.isEmpty()) return ""
        return googlePrefs.getString("GOOGLE_CRED_${email.lowercase().trim()}", "") ?: ""
    }

    fun saveKnownGoogleAccount(email: String) {
        val cleanEmail = email.lowercase().trim()
        if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) return
        val actuales = googlePrefs.getStringSet("KNOWN_GOOGLE_ACCOUNTS", emptySet())?.toMutableSet()
            ?: mutableSetOf()
        actuales.add(cleanEmail)
        googlePrefs.edit().putStringSet("KNOWN_GOOGLE_ACCOUNTS", actuales).apply()
    }

    fun getKnownGoogleAccounts(): List<String> {
        val resultado = linkedSetOf<String>()

        // 1. Cuentas registradas explícitamente en el conjunto
        googlePrefs.getStringSet("KNOWN_GOOGLE_ACCOUNTS", emptySet())?.forEach { email ->
            val clean = email.lowercase().trim()
            if (clean.contains("@")) resultado.add(clean)
        }

        // 2. Cuentas que ya tienen credencial vinculada en googlePrefs
        googlePrefs.all.keys.forEach { key ->
            if (key.startsWith("GOOGLE_CRED_")) {
                val clean = key.removePrefix("GOOGLE_CRED_").lowercase().trim()
                if (clean.contains("@")) resultado.add(clean)
            }
        }

        // 3. Último correo activo en sesión si existe
        val currentEmail = getUserEmail().lowercase().trim()
        if (currentEmail.contains("@")) {
            resultado.add(currentEmail)
        }

        return resultado.toList()
    }

    fun saveUserData(name: String, email: String, phone: String) {
        prefs.edit().apply {
            if (name.isNotEmpty())  putString("USER_NAME",  name)
            if (email.isNotEmpty()) putString("USER_EMAIL", email)
            if (phone.isNotEmpty()) putString("USER_PHONE", phone)
            apply()
        }
        if (email.isNotEmpty()) {
            saveKnownGoogleAccount(email)
        }
    }

    fun saveUserLastName(lastName: String) {
        if (lastName.isNotEmpty()) {
            prefs.edit().putString("USER_LASTNAME", lastName).apply()
        }
    }

    // Guardar Identificación / Cédula
    fun saveUserIdentification(identification: String) {
        prefs.edit().putString("USER_IDENTIFICATION", identification).apply()
    }

    // Guardar Matrícula Profesional (COMVEZCOL)
    fun saveUserMatriculaProfesional(matricula: String) {
        prefs.edit().putString("USER_MATRICULA_PROFESIONAL", matricula).apply()
    }

    // Título profesional fijo oficial del médico veterinario
    fun getUserTituloProfesional(): String = "Médico Veterinario Zootecnista"
    fun getUserIdentification(): String = prefs.getString("USER_IDENTIFICATION", "") ?: ""
    fun getUserMatriculaProfesional(): String = prefs.getString("USER_MATRICULA_PROFESIONAL", "") ?: ""
    fun getUserLastName(): String = prefs.getString("USER_LASTNAME", "") ?: ""
    fun getUserName(): String = prefs.getString("USER_NAME", "") ?: ""
    fun getUserEmail(): String = prefs.getString("USER_EMAIL", "") ?: ""
    fun getUserPhone(): String = prefs.getString("USER_PHONE", "") ?: ""

    // ID de Usuario
    fun saveUserId(userId: Int) {
        prefs.edit().putInt("ID_USUARIO", userId).apply()
    }

    fun getUserId(): Int = prefs.getInt("ID_USUARIO", -1)

    // Rol de Usuario (1 = Ciudadano/Peticionario, 3 = Veterinario)
    fun saveUserRole(roleId: Int) {
        prefs.edit().putInt("USER_ROLE", roleId).apply()
    }

    fun getUserRole(): Int = prefs.getInt("USER_ROLE", -1)

    // Foto de perfil
    fun saveProfileImagePath(path: String) {
        prefs.edit().putString("PROFILE_IMAGE_PATH", path).apply()
    }

    fun getProfileImagePath(): String = prefs.getString("PROFILE_IMAGE_PATH", "") ?: ""

    // Token JWT
    fun saveAccessToken(token: String) {
        prefs.edit().putString("ACCESS_TOKEN", token).apply()
    }

    fun getAccessToken(): String {
        val token = prefs.getString("ACCESS_TOKEN", "") ?: ""
        if (token.isNotEmpty()) return token
        return prefs.getString("access_token", "") ?: ""
    }

    fun setLanguage(lang: String) {
        prefs.edit().putString("APP_LANGUAGE", lang).apply()
    }

    fun getLanguage(): String = prefs.getString("APP_LANGUAGE", "es") ?: "es"

    // Limpiar sesión completa
    fun clearSession() {
        prefs.edit().clear().apply()
    }
}