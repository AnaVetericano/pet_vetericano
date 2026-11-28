package com.example.petvetericano

import android.content.Context
import android.content.SharedPreferences
import com.example.petvetericano.notificaciones.FcmTokenManager
import com.example.petvetericano.offline.PeticionesCacheManager
import com.example.petvetericano.realtime.WebSocketConnectionManager

class SharedPreferencesManager(context: Context) {
    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        context.getSharedPreferences("PetVetericanoPrefs", Context.MODE_PRIVATE)
    private val googlePrefs: SharedPreferences =
        context.getSharedPreferences(GOOGLE_LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
    private val googleLinkVaultPrefs: SharedPreferences =
        context.getSharedPreferences(GOOGLE_LINK_VAULT_PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val GOOGLE_LEGACY_PREFS_NAME = "PetVetericanoGoogleAuthPrefs"
        private const val GOOGLE_LINK_VAULT_PREFS_NAME = "PetVetericanoGoogleLinkVault"
        private const val MAX_LENGTH_NOMBRE = 50
        private const val MAX_LENGTH_APELLIDO = 50
        private const val MAX_LENGTH_EMAIL = 100
        private const val MAX_LENGTH_TELEFONO = 15
        private const val MAX_LENGTH_IDENTIFICACION = 20
        private const val MAX_LENGTH_MATRICULA = 15
    }

    fun limpiarDatosGoogleLegacy() {
        if (googlePrefs.all.isNotEmpty()) {
            googlePrefs.edit().clear().apply()
        }
        if (googleLinkVaultPrefs.all.isNotEmpty()) {
            googleLinkVaultPrefs.edit().clear().apply()
        }
    }

    fun saveUserData(
        name: String,
        email: String,
        phone: String
    ) {
        val cleanName = name.trim().replace("\\s+".toRegex(), " ").take(MAX_LENGTH_NOMBRE)
        val cleanEmail = email.trim().take(MAX_LENGTH_EMAIL)
        val cleanPhone = phone.trim().take(MAX_LENGTH_TELEFONO)

        prefs.edit().apply {
            if (cleanName.isNotEmpty()) putString("USER_NAME", cleanName)
            if (cleanEmail.isNotEmpty()) putString("USER_EMAIL", cleanEmail)
            if (cleanPhone.isNotEmpty()) putString("USER_PHONE", cleanPhone)
            apply()
        }
    }

    fun saveUserLastName(lastName: String) {
        val cleanLastName = lastName.trim().replace("\\s+".toRegex(), " ").take(MAX_LENGTH_APELLIDO)
        if (cleanLastName.isNotEmpty()) {
            prefs.edit().putString("USER_LASTNAME", cleanLastName).apply()
        }
    }

    // Guardar Identificación / Cédula
    fun saveUserIdentification(identification: String) {
        val cleanId = identification.trim().take(MAX_LENGTH_IDENTIFICACION)
        if (cleanId.isNotEmpty()) {
            prefs.edit().putString("USER_IDENTIFICATION", cleanId).apply()
        }
    }

    // Guardar Matrícula Profesional (COMVEZCOL)
    fun saveUserMatriculaProfesional(matricula: String) {
        val cleanMatricula = matricula.trim().uppercase().take(MAX_LENGTH_MATRICULA)
        prefs.edit().putString("USER_MATRICULA_PROFESIONAL", cleanMatricula).apply()
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

    // Foto de perfil sincronizada con el Backend (URL de Cloudinary o Data URI)
    fun saveProfileImagePath(path: String?) {
        val clean = path?.trim() ?: ""
        prefs.edit()
            .remove("PROFILE_IMAGE_PATH")
            .putString("PROFILE_IMAGE_URL", clean)
            .apply()
    }

    fun getProfileImagePath(): String {
        if (prefs.contains("PROFILE_IMAGE_PATH")) {
            prefs.edit().remove("PROFILE_IMAGE_PATH").apply()
            try {
                val legacyFile = java.io.File(appContext.filesDir, "profile_picture.jpg")
                if (legacyFile.exists()) legacyFile.delete()
            } catch (_: Exception) { }
        }
        return prefs.getString("PROFILE_IMAGE_URL", "") ?: ""
    }

    // Token JWT
    fun saveAccessToken(token: String) {
        prefs.edit().putString("ACCESS_TOKEN", token).apply()
    }

    fun getAccessToken(): String {
        val token = prefs.getString("ACCESS_TOKEN", "") ?: ""
        if (token.isNotEmpty()) return token
        return prefs.getString("access_token", "") ?: ""
    }

    fun saveRefreshToken(token: String) {
        prefs.edit().putString("REFRESH_TOKEN", token).apply()
    }

    fun getRefreshToken(): String {
        return prefs.getString("REFRESH_TOKEN", "") ?: ""
    }

    fun setLanguage(lang: String) {
        prefs.edit().putString("APP_LANGUAGE", lang).apply()
    }

    fun getLanguage(): String = prefs.getString("APP_LANGUAGE", "es") ?: "es"

    // Limpiar sesión completa
    fun clearSession() {
        // Cierra la conexión WebSocket y desactiva el token FCM en el backend antes de borrar la sesión.
        WebSocketConnectionManager.onLogout()
        FcmTokenManager.desactivarEnLogout(appContext)
        BottomNavHelper.limpiarHistorial()
        val currentUserId = getUserId()
        if (currentUserId > 0) {
            MaletinManager.limpiarCacheUsuario(appContext, currentUserId)
            PeticionesCacheManager.limpiarCacheVeterinario(appContext, currentUserId)
        }
        val idiomaGuardado = getLanguage()
        prefs.edit().clear().putString("APP_LANGUAGE", idiomaGuardado).apply()
        limpiarDatosGoogleLegacy()
        try {
            val legacyFile = java.io.File(appContext.filesDir, "profile_picture.jpg")
            if (legacyFile.exists()) legacyFile.delete()
        } catch (_: Exception) { }
    }
}
