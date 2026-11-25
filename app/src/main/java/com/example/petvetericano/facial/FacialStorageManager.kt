package com.example.petvetericano.facial

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Bóveda local cifrada con Android Keystore (AES-256-GCM) para almacenar el embedding facial
 * de 192 dimensiones y el refresh token asociado en el dispositivo.
 *
 * Usa un archivo de SharedPreferences independiente de [com.example.petvetericano.SharedPreferencesManager]
 * para que cerrar sesión normalmente no borre el enrolamiento facial del usuario.
 */
class FacialStorageManager(context: Context) {

    data class PerfilFacialGuardado(
        val userId: Int,
        val email: String,
        val nombre: String,
        val apellido: String,
        val telefono: String,
        val fotoPerfil: String,
        val matriculaProfesional: String,
        val identificacion: String,
        val idRol: Int,
        val refreshToken: String,
        val centroid: FloatArray,
        val samples: List<FloatArray>,
        val enrolledAtMillis: Long,
        val modelVersion: String = MODEL_VERSION
    )

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "FacialStorageManager"
        private const val PREFS_NAME = "PetVetericanoFacialVault"
        private const val KEY_PAYLOAD_ENC = "facial_payload_enc_v1"
        private const val KEY_FAILED_ATTEMPTS = "facial_failed_attempts"
        private const val KEY_LOCKOUT_UNTIL_MS = "facial_lockout_until_ms"

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "pet_vetericano_facial_aes_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128

        const val MODEL_VERSION = "mobilefacenet-112-192-v1"
        const val MAX_FAILED_ATTEMPTS = 5
        const val LOCKOUT_DURATION_MS = 60_000L

        internal fun serializarPerfilAJson(perfil: PerfilFacialGuardado): String {
            val root = JSONObject().apply {
                put("userId", perfil.userId)
                put("email", perfil.email.trim().lowercase(Locale.ROOT))
                put("nombre", perfil.nombre)
                put("apellido", perfil.apellido)
                put("telefono", perfil.telefono)
                put("fotoPerfil", perfil.fotoPerfil)
                put("matriculaProfesional", perfil.matriculaProfesional)
                put("identificacion", perfil.identificacion)
                put("idRol", perfil.idRol)
                put("refreshToken", perfil.refreshToken)
                put("enrolledAtMillis", perfil.enrolledAtMillis)
                put("modelVersion", perfil.modelVersion)
                put("centroid", floatArrayAJson(perfil.centroid))
                val arrSamples = JSONArray()
                for (sample in perfil.samples) {
                    arrSamples.put(floatArrayAJson(sample))
                }
                put("samples", arrSamples)
            }
            return root.toString()
        }

        internal fun deserializarPerfilDesdeJson(rawJson: String): PerfilFacialGuardado? {
            return try {
                val obj = JSONObject(rawJson)
                val userId = obj.optInt("userId", -1)
                val email = obj.optString("email", "").trim().lowercase(Locale.ROOT)
                val idRol = obj.optInt("idRol", -1)
                val refreshToken = obj.optString("refreshToken", "").trim()
                val centroidArr = obj.optJSONArray("centroid") ?: return null
                val centroid = jsonAFloatArray(centroidArr)
                if (userId <= 0 || email.isEmpty() || idRol <= 0 || centroid.size != FacialMath.EMBEDDING_DIM) {
                    return null
                }

                val samples = mutableListOf<FloatArray>()
                val samplesArr = obj.optJSONArray("samples")
                if (samplesArr != null) {
                    for (i in 0 until samplesArr.length()) {
                        val item = samplesArr.optJSONArray(i) ?: continue
                        val vec = jsonAFloatArray(item)
                        if (vec.size == FacialMath.EMBEDDING_DIM) {
                            samples.add(vec)
                        }
                    }
                }

                PerfilFacialGuardado(
                    userId = userId,
                    email = email,
                    nombre = obj.optString("nombre", ""),
                    apellido = obj.optString("apellido", ""),
                    telefono = obj.optString("telefono", ""),
                    fotoPerfil = obj.optString("fotoPerfil", ""),
                    matriculaProfesional = obj.optString("matriculaProfesional", ""),
                    identificacion = obj.optString("identificacion", ""),
                    idRol = idRol,
                    refreshToken = refreshToken,
                    centroid = centroid,
                    samples = samples,
                    enrolledAtMillis = obj.optLong("enrolledAtMillis", 0L),
                    modelVersion = obj.optString("modelVersion", MODEL_VERSION)
                )
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo interpretar el perfil facial almacenado: ${e.javaClass.simpleName}")
                null
            }
        }

        private fun floatArrayAJson(arr: FloatArray): JSONArray {
            val out = JSONArray()
            for (v in arr) {
                out.put(v.toDouble())
            }
            return out
        }

        private fun jsonAFloatArray(arr: JSONArray): FloatArray {
            return FloatArray(arr.length()) { i -> arr.optDouble(i, 0.0).toFloat() }
        }

        /**
         * Enmascara un correo para mostrar pistas seguras en pantalla (ej. "ju***@gmail.com").
         */
        fun enmascararCorreo(email: String): String {
            val limpio = email.trim().lowercase(Locale.ROOT)
            val partes = limpio.split("@")
            if (partes.size != 2 || partes[0].isEmpty() || partes[1].isEmpty()) return limpio
            val local = partes[0]
            val prefijo = when {
                local.length <= 2 -> local.take(1)
                else -> local.take(2)
            }
            return "$prefijo***@${partes[1]}"
        }
    }

    fun guardarPerfilFacial(perfil: PerfilFacialGuardado): Boolean {
        return try {
            val json = serializarPerfilAJson(perfil)
            val encrypted = cifrarConKeystore(json.toByteArray(StandardCharsets.UTF_8))
            prefs.edit()
                .putString(KEY_PAYLOAD_ENC, encrypted)
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
                .apply()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error cifrando perfil facial en Android Keystore: ${e.javaClass.simpleName}", e)
            false
        }
    }

    fun obtenerPerfilFacial(): PerfilFacialGuardado? {
        val encrypted = prefs.getString(KEY_PAYLOAD_ENC, null)?.trim().orEmpty()
        if (encrypted.isEmpty()) return null
        return try {
            val plainBytes = descifrarConKeystore(encrypted)
            val json = String(plainBytes, StandardCharsets.UTF_8)
            val perfil = deserializarPerfilDesdeJson(json)
            if (perfil == null) {
                eliminarPerfilFacial()
            }
            perfil
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo descifrar bóveda facial (clave reiniciada o corrupta); limpiando.")
            eliminarPerfilFacial()
            null
        }
    }

    fun tieneRostroEnrolado(): Boolean {
        val perfil = obtenerPerfilFacial() ?: return false
        return perfil.refreshToken.isNotBlank() && perfil.centroid.size == FacialMath.EMBEDDING_DIM
    }

    fun tieneRostroEnroladoParaUsuario(userId: Int): Boolean {
        if (userId <= 0) return false
        val perfil = obtenerPerfilFacial() ?: return false
        return perfil.userId == userId &&
            perfil.refreshToken.isNotBlank() &&
            perfil.centroid.size == FacialMath.EMBEDDING_DIM
    }

    /**
     * Mantiene sincronizado el refresh token rotado y los datos básicos cuando el mismo usuario
     * inicia sesión o renueva su token en segundo plano.
     */
    fun actualizarRefreshTokenSiCoincide(
        userId: Int,
        nuevoRefreshToken: String,
        nombre: String? = null,
        apellido: String? = null,
        fotoPerfil: String? = null,
        telefono: String? = null,
        matriculaProfesional: String? = null,
        identificacion: String? = null
    ) {
        if (userId <= 0 || nuevoRefreshToken.isBlank()) return
        val actual = obtenerPerfilFacial() ?: return
        if (actual.userId != userId) return

        val actualizado = actual.copy(
            refreshToken = nuevoRefreshToken.trim(),
            nombre = nombre?.takeIf { it.isNotBlank() } ?: actual.nombre,
            apellido = apellido?.takeIf { it.isNotBlank() } ?: actual.apellido,
            fotoPerfil = fotoPerfil ?: actual.fotoPerfil,
            telefono = telefono?.takeIf { it.isNotBlank() } ?: actual.telefono,
            matriculaProfesional = matriculaProfesional?.takeIf { it.isNotBlank() } ?: actual.matriculaProfesional,
            identificacion = identificacion?.takeIf { it.isNotBlank() } ?: actual.identificacion
        )
        guardarPerfilFacial(actualizado)
    }

    fun eliminarPerfilFacial() {
        prefs.edit().clear().apply()
    }

    fun obtenerBloqueoRestanteMs(ahoraMs: Long = System.currentTimeMillis()): Long {
        val until = prefs.getLong(KEY_LOCKOUT_UNTIL_MS, 0L)
        if (until <= ahoraMs) return 0L
        return until - ahoraMs
    }

    fun registrarIntentoFallido(ahoraMs: Long = System.currentTimeMillis()): Long {
        val bloqueado = obtenerBloqueoRestanteMs(ahoraMs)
        if (bloqueado > 0L) return bloqueado

        val nuevosIntentos = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        return if (nuevosIntentos >= MAX_FAILED_ATTEMPTS) {
            val lockoutUntil = ahoraMs + LOCKOUT_DURATION_MS
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, 0)
                .putLong(KEY_LOCKOUT_UNTIL_MS, lockoutUntil)
                .apply()
            LOCKOUT_DURATION_MS
        } else {
            prefs.edit()
                .putInt(KEY_FAILED_ATTEMPTS, nuevosIntentos)
                .apply()
            0L
        }
    }

    fun limpiarIntentosFallidos() {
        prefs.edit()
            .putInt(KEY_FAILED_ATTEMPTS, 0)
            .putLong(KEY_LOCKOUT_UNTIL_MS, 0L)
            .apply()
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
}
