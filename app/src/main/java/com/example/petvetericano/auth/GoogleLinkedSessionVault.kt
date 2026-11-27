package com.example.petvetericano.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Perfil mínimo y refresh token cifrado para una cuenta de Google ya verificada en este dispositivo.
 * NUNCA almacena contraseñas; únicamente guarda el refresh token JWT emitido por el backend.
 */
data class CuentaGoogleVinculada(
    val userId: Int,
    val email: String,
    val nombre: String,
    val apellido: String,
    val telefono: String,
    val fotoPerfil: String,
    val matriculaProfesional: String,
    val idRol: Int,
    val refreshToken: String,
    val vinculadoEnMs: Long = System.currentTimeMillis()
)

interface AlmacenVinculoGoogle {
    fun guardarCuentaVinculada(cuenta: CuentaGoogleVinculada): Boolean
    fun obtenerCuentaVinculada(email: String): CuentaGoogleVinculada?
    fun tieneCuentaVinculada(email: String): Boolean = obtenerCuentaVinculada(email) != null
    fun actualizarRefreshTokenSiCoincide(
        userId: Int,
        nuevoRefreshToken: String,
        nombre: String? = null,
        apellido: String? = null,
        fotoPerfil: String? = null,
        telefono: String? = null,
        matriculaProfesional: String? = null
    )
    fun eliminarCuentaVinculada(email: String)
    fun limpiarTodo()
}

/**
 * Bóveda local cifrada con Android Keystore (AES-256-GCM) para persistir la vinculación de cuentas
 * de Google verificadas a través de cierres de sesión normales.
 *
 * Usa un archivo de SharedPreferences independiente de [com.example.petvetericano.SharedPreferencesManager]
 * para que `clearSession()` cierre la sesión activa sin exigir nuevamente la contraseña de una cuenta
 * Google ya verificada y vinculada en este dispositivo.
 */
class GoogleLinkedSessionVault(context: Context) : AlmacenVinculoGoogle {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "GoogleLinkedVault"
        internal const val PREFS_NAME = "PetVetericanoGoogleLinkVault"
        private const val KEY_MAP_ENC = "google_linked_accounts_enc_v1"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "pet_vetericano_google_link_aes_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128

        private val gson = Gson()
        private val mapType = object : TypeToken<MutableMap<String, CuentaGoogleVinculada>>() {}.type

        internal fun normalizarEmail(email: String): String = email.trim().lowercase(Locale.ROOT)

        internal fun serializarMapaAJson(mapa: Map<String, CuentaGoogleVinculada>): String {
            return gson.toJson(mapa)
        }

        internal fun deserializarMapaDesdeJson(rawJson: String): MutableMap<String, CuentaGoogleVinculada> {
            return try {
                val parsed: MutableMap<String, CuentaGoogleVinculada>? = gson.fromJson(rawJson, mapType)
                parsed ?: mutableMapOf()
            } catch (_: Exception) {
                mutableMapOf()
            }
        }
    }

    override fun guardarCuentaVinculada(cuenta: CuentaGoogleVinculada): Boolean {
        val emailNorm = normalizarEmail(cuenta.email)
        val refreshLimpio = cuenta.refreshToken.trim()
        if (emailNorm.isEmpty() || cuenta.userId <= 0 || cuenta.idRol <= 0 || refreshLimpio.isEmpty()) {
            return false
        }
        return try {
            val mapa = leerMapaSeguro()
            mapa[emailNorm] = cuenta.copy(
                email = emailNorm,
                refreshToken = refreshLimpio
            )
            escribirMapaSeguro(mapa)
        } catch (e: Exception) {
            logWarn("Error guardando cuenta Google vinculada: ${e.javaClass.simpleName}")
            false
        }
    }

    override fun obtenerCuentaVinculada(email: String): CuentaGoogleVinculada? {
        val emailNorm = normalizarEmail(email)
        if (emailNorm.isEmpty()) return null
        val cuenta = leerMapaSeguro()[emailNorm] ?: return null
        if (cuenta.userId <= 0 || cuenta.idRol <= 0 || cuenta.refreshToken.isBlank()) {
            return null
        }
        return cuenta
    }

    override fun actualizarRefreshTokenSiCoincide(
        userId: Int,
        nuevoRefreshToken: String,
        nombre: String?,
        apellido: String?,
        fotoPerfil: String?,
        telefono: String?,
        matriculaProfesional: String?
    ) {
        val refreshLimpio = nuevoRefreshToken.trim()
        if (userId <= 0 || refreshLimpio.isEmpty()) return
        try {
            val mapa = leerMapaSeguro()
            var modificado = false
            for ((clave, cuenta) in mapa.entries.toList()) {
                if (cuenta.userId == userId) {
                    mapa[clave] = cuenta.copy(
                        refreshToken = refreshLimpio,
                        nombre = nombre?.takeIf { it.isNotBlank() } ?: cuenta.nombre,
                        apellido = apellido?.takeIf { it.isNotBlank() } ?: cuenta.apellido,
                        fotoPerfil = fotoPerfil ?: cuenta.fotoPerfil,
                        telefono = telefono?.takeIf { it.isNotBlank() } ?: cuenta.telefono,
                        matriculaProfesional = matriculaProfesional?.takeIf { it.isNotBlank() }
                            ?: cuenta.matriculaProfesional
                    )
                    modificado = true
                }
            }
            if (modificado) {
                escribirMapaSeguro(mapa)
            }
        } catch (e: Exception) {
            logWarn("No se pudo actualizar refresh token en bóveda Google: ${e.javaClass.simpleName}")
        }
    }

    override fun eliminarCuentaVinculada(email: String) {
        val emailNorm = normalizarEmail(email)
        if (emailNorm.isEmpty()) return
        try {
            val mapa = leerMapaSeguro()
            if (mapa.remove(emailNorm) != null) {
                if (mapa.isEmpty()) {
                    limpiarTodo()
                } else {
                    escribirMapaSeguro(mapa)
                }
            }
        } catch (e: Exception) {
            logWarn("No se pudo eliminar cuenta vinculada: ${e.javaClass.simpleName}")
        }
    }

    override fun limpiarTodo() {
        prefs.edit().clear().apply()
    }

    private fun leerMapaSeguro(): MutableMap<String, CuentaGoogleVinculada> {
        val encoded = prefs.getString(KEY_MAP_ENC, null)?.trim().orEmpty()
        if (encoded.isEmpty()) return mutableMapOf()
        return try {
            val plainBytes = descifrarConKeystore(encoded)
            val json = String(plainBytes, StandardCharsets.UTF_8)
            deserializarMapaDesdeJson(json)
        } catch (_: Exception) {
            logWarn("No se pudo descifrar bóveda de cuentas Google (clave reiniciada); limpiando.")
            limpiarTodo()
            mutableMapOf()
        }
    }

    private fun escribirMapaSeguro(mapa: Map<String, CuentaGoogleVinculada>): Boolean {
        val json = serializarMapaAJson(mapa)
        val encrypted = cifrarConKeystore(json.toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString(KEY_MAP_ENC, encrypted).apply()
        return true
    }

    private fun obtenerOCrearClaveAes(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existente = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existente != null) return existente

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGen.init(spec)
        return keyGen.generateKey()
    }

    private fun cifrarConKeystore(plainBytes: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, obtenerOCrearClaveAes())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plainBytes)
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun descifrarConKeystore(encodedBase64: String): ByteArray {
        val combined = Base64.decode(encodedBase64, Base64.NO_WRAP)
        require(combined.size > GCM_IV_LENGTH_BYTES) { "Payload cifrado inválido." }
        val iv = combined.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val cipherText = combined.copyOfRange(GCM_IV_LENGTH_BYTES, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            obtenerOCrearClaveAes(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        )
        return cipher.doFinal(cipherText)
    }

    private fun logWarn(msg: String) {
        try {
            Log.w(TAG, msg)
        } catch (_: RuntimeException) {
        }
    }
}
