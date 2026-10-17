package com.example.petvetericano

import com.example.petvetericano.models.GoogleLoginRequest
import com.example.petvetericano.models.LoginResponse
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    private val gson = Gson()

    @Test
    fun googleLoginRequest_serializaIdTokenCorrectamente() {
        val request = GoogleLoginRequest(
            idToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjEifQ...",
            nombre = "Juan",
            apellido = "Pérez"
        )
        val json = gson.toJson(request)
        assertTrue(json.contains("\"id_token\":\"eyJhbGciOiJSUzI1NiIsImtpZCI6IjEifQ...\""))
        assertTrue(json.contains("\"nombre\":\"Juan\""))
        assertTrue(json.contains("\"apellido\":\"Pérez\""))
    }

    @Test
    fun loginResponse_deserializaRespuestaDeGoogleCorrectamente() {
        val rawJson = """
            {
                "mensaje": "¡Registro exitoso con Google! Bienvenido, Juan Pérez",
                "email": "juan.perez@gmail.com",
                "id_usuario": 42,
                "nombre": "Juan",
                "apellido": "Pérez",
                "id_rol": 2,
                "es_nuevo_usuario": true,
                "foto": "https://lh3.googleusercontent.com/a/default-user",
                "tokens": {
                    "refresh": "refresh-jwt-token",
                    "access": "access-jwt-token"
                }
            }
        """.trimIndent()

        val response = gson.fromJson(rawJson, LoginResponse::class.java)
        assertEquals("juan.perez@gmail.com", response.email)
        assertEquals(42, response.idUsuario)
        assertEquals(2, response.idRol)
        assertEquals("Juan", response.nombre)
        assertEquals("Pérez", response.apellido)
        assertEquals(true, response.esNuevoUsuario)
        assertEquals("access-jwt-token", response.tokens.access)
        assertEquals("refresh-jwt-token", response.tokens.refresh)
    }

    @Test
    fun passwordDeterministicaGoogle_cumpleReglasDeSeguridad() {
        val pass1 = GoogleAuthHelper.generarPasswordGoogleDeterministica("usuario@gmail.com")
        val pass2 = GoogleAuthHelper.generarPasswordGoogleDeterministica("USUARIO@gmail.com")
        assertEquals(pass1, pass2)
        assertTrue(pass1.length >= 8)
        assertTrue(Regex("[A-Z]").containsMatchIn(pass1))
        assertTrue(Regex("[0-9]").containsMatchIn(pass1))
    }
}