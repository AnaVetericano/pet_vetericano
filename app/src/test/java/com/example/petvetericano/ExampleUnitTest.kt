package com.example.petvetericano

import com.example.petvetericano.models.GoogleLoginRequest
import com.example.petvetericano.models.LoginResponse
import com.example.petvetericano.models.MedicamentoMaletin
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

    @Test
    fun fundamentoLegal_constantesDeLeyes_contienenTextoNormativoCorrecto() {
        // Ley 84 de 1989
        assertTrue(ActaAtencionActivity.DESCRIPCION_LEY_84_1989.contains("Ley 84 de 1989"))
        assertTrue(ActaAtencionActivity.DESCRIPCION_LEY_84_1989.contains("Estatuto Nacional de Protección de los Animales"))
        assertTrue(ActaAtencionActivity.DESCRIPCION_LEY_84_1989.contains("prevenir el dolor y el sufrimiento animal"))

        // Ley 1638 de 2013
        assertTrue(ActaAtencionActivity.DESCRIPCION_LEY_1638_2013.contains("Ley 1638 de 2013"))
        assertTrue(ActaAtencionActivity.DESCRIPCION_LEY_1638_2013.contains("espectáculos circenses"))

        // Ley 1774 de 2016
        assertTrue(ActaAtencionActivity.DESCRIPCION_LEY_1774_2016.contains("Ley 1774 de 2016"))
        assertTrue(ActaAtencionActivity.DESCRIPCION_LEY_1774_2016.contains("seres sintientes"))
        assertTrue(ActaAtencionActivity.DESCRIPCION_LEY_1774_2016.contains("Código Penal"))

        // Ley 1801 de 2016
        assertTrue(ActaAtencionActivity.DESCRIPCION_LEY_1801_2016.contains("Ley 1801 de 2016"))
        assertTrue(ActaAtencionActivity.DESCRIPCION_LEY_1801_2016.contains("Código Nacional de Seguridad y Convivencia Ciudadana"))
        assertTrue(ActaAtencionActivity.DESCRIPCION_LEY_1801_2016.contains("Título XIII"))

        // Ley 2054 de 2020
        assertTrue(ActaAtencionActivity.DESCRIPCION_LEY_2054_2020.contains("Ley 2054 de 2020"))
        assertTrue(ActaAtencionActivity.DESCRIPCION_LEY_2054_2020.contains("Centros de Bienestar Animal"))

        // Ley 2385 de 2024
        assertTrue(ActaAtencionActivity.DESCRIPCION_LEY_2385_2024.contains("Ley 2385 de 2024"))
        assertTrue(ActaAtencionActivity.DESCRIPCION_LEY_2385_2024.contains("Prohibición de la tauromaquia"))
        assertTrue(ActaAtencionActivity.DESCRIPCION_LEY_2385_2024.contains("corridas de toros"))
    }

    @Test
    fun actaLocalEntity_switchesFundamentoLegal_inicializacionPorDefecto() {
        val entity = com.example.petvetericano.offline.ActaLocalEntity(idPeticion = 1, idVeterinario = 1)
        assertFalse(entity.swLey84_1989)
        assertFalse(entity.swLey1638_2013)
        assertFalse(entity.swLey1774_2016)
        assertFalse(entity.swLey1801_2016)
        assertFalse(entity.swLey2054_2020)
        assertFalse(entity.swLey2385_2024)

        val entityModificada = entity.copy(
            swLey1774_2016 = true,
            swLey84_1989 = true
        )
        assertTrue(entityModificada.swLey1774_2016)
        assertTrue(entityModificada.swLey84_1989)
        assertFalse(entityModificada.swLey1638_2013)
    }

    @Test
    fun esNombreValido_aceptaNNyNombresValidos() {
        assertTrue(ActaFieldValidators.esNombreValido("NN"))
        assertTrue(ActaFieldValidators.esNombreValido("nn"))
        assertTrue(ActaFieldValidators.esNombreValido("N.N."))
        assertTrue(ActaFieldValidators.esNombreValido("Juan Pérez"))
        assertTrue(ActaFieldValidators.esNombreValido("Carlos"))
        assertFalse(ActaFieldValidators.esNombreValido("A"))
        assertFalse(ActaFieldValidators.esNombreValido(""))
        assertFalse(ActaFieldValidators.esNombreValido("   "))
        assertFalse(ActaFieldValidators.esNombreValido("Juan 2"))
        assertFalse(ActaFieldValidators.esNombreValido("12345"))
    }

    @Test
    fun esObjetoOInsumo_detectaGuantesDeCarnazaYObjetosFisicos() {
        assertTrue(MedicamentoMaletin.esObjetoOInsumo("Guantes de carnaza"))
        assertTrue(MedicamentoMaletin.esObjetoOInsumo("guante de látex"))
        assertTrue(MedicamentoMaletin.esObjetoOInsumo("Jelco 22G"))
        assertTrue(MedicamentoMaletin.esObjetoOInsumo("Catéter periférico"))
        assertTrue(MedicamentoMaletin.esObjetoOInsumo("Jeringa 3 ml"))
        assertTrue(MedicamentoMaletin.esObjetoOInsumo("Aguja hipodérmica"))
        assertTrue(MedicamentoMaletin.esObjetoOInsumo("Venda elástica"))
        assertTrue(MedicamentoMaletin.esObjetoOInsumo("Prueba rápida moquillo"))

        // Medicamentos farmacéuticos no deben considerarse objetos
        assertFalse(MedicamentoMaletin.esObjetoOInsumo("Amoxicilina 500mg"))
        assertFalse(MedicamentoMaletin.esObjetoOInsumo("Ketamina 10%"))
        assertFalse(MedicamentoMaletin.esObjetoOInsumo("Meloxicam gotas"))
    }

    @Test
    fun obtenerUnidadMedida_asignaUdsParaGuantesDeCarnazaYObjetos() {
        // Objeto con cantidad 1 -> "ud"
        assertEquals("ud", MedicamentoMaletin.obtenerUnidadMedida(via = "N/A", nombre = "Guantes de carnaza", cantidad = 1.0))
        // Objeto con cantidad > 1 -> "uds"
        assertEquals("uds", MedicamentoMaletin.obtenerUnidadMedida(via = "N/A", nombre = "Guantes de carnaza", cantidad = 2.0))
        // Objeto sin cantidad especificada -> "uds"
        assertEquals("uds", MedicamentoMaletin.obtenerUnidadMedida(via = "N/A", nombre = "Guantes de carnaza"))

        // Medicamentos normales deben mantener ml o mg según presentación/vía
        assertEquals("mg", MedicamentoMaletin.obtenerUnidadMedida(via = "Oral", presentacion = "Tableta", nombre = "Amoxicilina"))
        assertEquals("ml", MedicamentoMaletin.obtenerUnidadMedida(via = "Intravenosa (IV)", presentacion = "Frasco ampolla", nombre = "Solución Salina"))
    }

    @Test
    fun actaRescate_comentariosSistemasOpcionales_incluyeSoloCuandoNoEstanVacios() {
        val entitySinComentarios = com.example.petvetericano.offline.ActaLocalEntity(
            idPeticion = 10,
            idVeterinario = 5,
            requiereRescate = true
        )
        assertEquals("", entitySinComentarios.comentarioRespiratorio)
        assertEquals("", entitySinComentarios.comentarioDigestivo)
        assertEquals("", entitySinComentarios.comentarioMusculo)
        assertEquals("", entitySinComentarios.comentarioDermato)
        assertEquals("", entitySinComentarios.comentarioGenito)

        val requestSinComentarios = com.example.petvetericano.offline.ActaRequestBuilder.build(entitySinComentarios, emptyList(), emptyList(), false)
        assertFalse(requestSinComentarios.anamnesisDescripcionQueja.contains("Comentario respiratorio:"))
        assertFalse(requestSinComentarios.anamnesisDescripcionQueja.contains("Comentario digestivo:"))
        assertFalse(requestSinComentarios.anamnesisDescripcionQueja.contains("Comentario músculo-esquelético:"))
        assertFalse(requestSinComentarios.anamnesisDescripcionQueja.contains("Comentario dermatológico:"))
        assertFalse(requestSinComentarios.anamnesisDescripcionQueja.contains("Comentario génito-urinario:"))

        val entityConComentarios = entitySinComentarios.copy(
            comentarioRespiratorio = "Estertores leves en pulmón derecho",
            comentarioDigestivo = "Mucosa oral levemente pálida",
            comentarioMusculo = "Claudicación grado II miembro posterior",
            comentarioDermato = "Alopecia focal en dorso",
            comentarioGenito = "Sin secreción activa al momento"
        )
        val requestConComentarios = com.example.petvetericano.offline.ActaRequestBuilder.build(entityConComentarios, emptyList(), emptyList(), false)
        assertTrue(requestConComentarios.anamnesisDescripcionQueja.contains("   - Comentario respiratorio: Estertores leves en pulmón derecho"))
        assertTrue(requestConComentarios.anamnesisDescripcionQueja.contains("   - Comentario digestivo: Mucosa oral levemente pálida"))
        assertTrue(requestConComentarios.anamnesisDescripcionQueja.contains("   - Comentario músculo-esquelético: Claudicación grado II miembro posterior"))
        assertTrue(requestConComentarios.anamnesisDescripcionQueja.contains("   - Comentario dermatológico: Alopecia focal en dorso"))
        assertTrue(requestConComentarios.anamnesisDescripcionQueja.contains("   - Comentario génito-urinario: Sin secreción activa al momento"))
    }
}