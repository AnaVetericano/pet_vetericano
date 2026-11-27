package com.example.petvetericano

import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialProviderConfigurationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.petvetericano.models.LoginResponse
import com.example.petvetericano.network.AuthInterceptor
import com.example.petvetericano.network.RetrofitClient
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class LoginAndNetworkTest {

    @Test
    fun debugBuildConfig_apuntaARailwayPorDefectoYRetrofitLoUsa() {
        val esperada = "https://backendvetericano-production.up.railway.app/api/"
        assertEquals(esperada, RetrofitClient.PRODUCTION_BASE_URL)
        assertEquals(esperada, BuildConfig.API_BASE_URL)
        assertEquals(BuildConfig.API_BASE_URL, RetrofitClient.BASE_URL)
    }

    @Test
    fun authInterceptor_omiteAuthorizationEnRutasPublicasDeAuth() {
        val interceptor = AuthInterceptor { "token-viejo-expirado" }
        val rutasPublicas = listOf(
            "https://backendvetericano-production.up.railway.app/api/usuarios/login/",
            "https://backendvetericano-production.up.railway.app/api/usuarios/register/",
            "https://backendvetericano-production.up.railway.app/api/usuarios/register/solicitar-codigo/",
            "https://backendvetericano-production.up.railway.app/api/usuarios/register/confirmar-codigo/",
            "https://backendvetericano-production.up.railway.app/api/usuarios/google-login/",
            "https://backendvetericano-production.up.railway.app/api/usuarios/recuperar-password/",
            "https://backendvetericano-production.up.railway.app/api/usuarios/confirmar-password/",
            "https://backendvetericano-production.up.railway.app/api/token/refresh/"
        )

        for (url in rutasPublicas) {
            val chain = FakeChain(Request.Builder().url(url).build())
            interceptor.intercept(chain)
            assertNull(
                "No debe inyectar Authorization en ruta publica $url",
                chain.capturedRequest?.header("Authorization")
            )
        }
    }

    @Test
    fun authInterceptor_inyectaBearerEnRutasProtegidas() {
        val interceptor = AuthInterceptor { "access-jwt-activo" }
        val chain = FakeChain(
            Request.Builder()
                .url("https://backendvetericano-production.up.railway.app/api/peticiones/listar/")
                .build()
        )
        interceptor.intercept(chain)
        assertEquals("Bearer access-jwt-activo", chain.capturedRequest?.header("Authorization"))
    }

    @Test
    fun resolverMensajeErrorHttp_diferenciaCredenciales4xxDeErrores5xxSinExponerDatos() {
        val msgCredenciales = inicio_sesion.resolverMensajeErrorHttp(
            400,
            """{"non_field_errors":["Credenciales incorrectas."]}"""
        )
        assertEquals("Credenciales incorrectas.", msgCredenciales)

        val msgDesactivada = inicio_sesion.resolverMensajeErrorHttp(
            400,
            """{"non_field_errors":["Esta cuenta ha sido desactivada."]}"""
        )
        assertEquals("Esta cuenta ha sido desactivada.", msgDesactivada)

        val msg401Fallback = inicio_sesion.resolverMensajeErrorHttp(401, "")
        assertEquals("Correo o contraseña incorrectos", msg401Fallback)

        val msg404 = inicio_sesion.resolverMensajeErrorHttp(404, "<html>Not Found</html>")
        assertEquals("Servicio de inicio de sesión no disponible (404).", msg404)

        val msg500Html = inicio_sesion.resolverMensajeErrorHttp(
            500,
            "<html><body><h1>Traceback secret DB password</h1></body></html>"
        )
        assertEquals("Error del servidor (500). Intenta nuevamente más tarde.", msg500Html)
        assertFalse(msg500Html.contains("Traceback", ignoreCase = true))
        assertFalse(msg500Html.contains("<html>", ignoreCase = true))

        val msg503 = inicio_sesion.resolverMensajeErrorHttp(503, """{"detail":"Service Unavailable"}""")
        assertEquals("Error del servidor (503). Intenta nuevamente más tarde.", msg503)
    }

    @Test
    fun resolverMensajeErrorExcepcion_diferenciaConexionTimeoutYRespuestaInvalida() {
        val msgTimeout = inicio_sesion.resolverMensajeErrorExcepcion(SocketTimeoutException("timeout"))
        assertTrue(msgTimeout.contains("Tiempo de espera agotado", ignoreCase = true))

        val msgConnect = inicio_sesion.resolverMensajeErrorExcepcion(
            ConnectException("Failed to connect to /127.0.0.1:8000")
        )
        assertTrue(msgConnect.contains("Error de conexión con el servidor", ignoreCase = true))
        assertFalse(msgConnect.contains("127.0.0.1"))

        val msgHost = inicio_sesion.resolverMensajeErrorExcepcion(
            UnknownHostException("backendvetericano-production.up.railway.app")
        )
        assertTrue(msgHost.contains("Error de conexión con el servidor", ignoreCase = true))
        assertFalse(msgHost.contains("railway.app"))

        val msgIo = inicio_sesion.resolverMensajeErrorExcepcion(IOException("reset"))
        assertTrue(msgIo.contains("Error de conexión con el servidor", ignoreCase = true))

        val msgParse = inicio_sesion.resolverMensajeErrorExcepcion(
            JsonSyntaxException("Expected BEGIN_OBJECT at line 1")
        )
        assertEquals("No se pudo procesar la respuesta del servidor. Intenta nuevamente.", msgParse)
    }

    @Test
    fun googleWebClientId_validaFormatoYPriorizaFuentesSinInventarSecretos() {
        assertFalse(GoogleAuthHelper.esWebClientIdValido(null))
        assertFalse(GoogleAuthHelper.esWebClientIdValido(""))
        assertFalse(GoogleAuthHelper.esWebClientIdValido(".apps.googleusercontent.com"))
        assertFalse(GoogleAuthHelper.esWebClientIdValido("12345.otrodominio.com"))
        assertTrue(GoogleAuthHelper.esWebClientIdValido("414064158617-abc123.apps.googleusercontent.com"))

        assertEquals(
            "414064158617-custom.apps.googleusercontent.com",
            GoogleAuthHelper.seleccionarWebClientId(
                customId = " 414064158617-custom.apps.googleusercontent.com ",
                defaultWebClientId = "414064158617-default.apps.googleusercontent.com",
                buildConfigWebClientId = "414064158617-build.apps.googleusercontent.com"
            )
        )
        assertEquals(
            "414064158617-default.apps.googleusercontent.com",
            GoogleAuthHelper.seleccionarWebClientId(
                customId = "",
                defaultWebClientId = "414064158617-default.apps.googleusercontent.com",
                buildConfigWebClientId = "414064158617-build.apps.googleusercontent.com"
            )
        )
        assertEquals(
            "414064158617-build.apps.googleusercontent.com",
            GoogleAuthHelper.seleccionarWebClientId(
                customId = "",
                defaultWebClientId = null,
                buildConfigWebClientId = "414064158617-build.apps.googleusercontent.com"
            )
        )
        assertEquals(
            "",
            GoogleAuthHelper.seleccionarWebClientId(
                customId = "",
                defaultWebClientId = "",
                buildConfigWebClientId = ""
            )
        )
    }

    @Test
    fun googleIdTokenRequest_rechazaTokensFalsosYAceptaJwtAutentico() {
        assertFalse(GoogleAuthHelper.esIdTokenJwtValido("android_account:persona@gmail.com"))
        assertFalse(GoogleAuthHelper.esIdTokenJwtValido("header.payload"))
        assertFalse(GoogleAuthHelper.esIdTokenJwtValido("header.payload with space.sig"))

        val jwt = "eyJhbGciOiJSUzI1NiIsImtpZCI6ImFiYyJ9.eyJzdWIiOiI5OTkiLCJlbWFpbCI6InVzZXJAZ21haWwuY29tIn0.sig123"
        val req = GoogleAuthHelper.construirGoogleLoginRequest(
            GoogleAuthHelper.CredencialGoogleExtraida(
                idToken = " $jwt ",
                email = " User@Gmail.com ",
                nombre = " Ana ",
                apellido = " Perez "
            )
        )
        assertEquals(jwt, req.idToken)
        assertEquals("user@gmail.com", req.email)
        assertEquals("Ana", req.nombre)
        assertEquals("Perez", req.apellido)
    }

    @Test
    fun extraerNombreYApellidoDeGoogle_usaClaimsGoogleYRespaldoLimpio() {
        assertEquals(
            Pair("María", "Gómez"),
            GoogleAuthHelper.extraerNombreYApellidoDeGoogle(
                givenName = "María",
                familyName = "Gómez",
                displayName = "María Ignorada",
                email = "maria.gomez@gmail.com"
            )
        )
        assertEquals(
            Pair("Juan", "Carlos"),
            GoogleAuthHelper.extraerNombreYApellidoDeGoogle(
                givenName = null,
                familyName = null,
                displayName = "Juan   Carlos",
                email = "jcarlos@gmail.com"
            )
        )
        assertEquals(
            Pair("Ana", "Rios"),
            GoogleAuthHelper.extraerNombreYApellidoDeGoogle(
                givenName = null,
                familyName = null,
                displayName = null,
                email = "ana.rios24@gmail.com"
            )
        )
    }

    @Test
    fun resolverMensajeErrorGoogleHttp_yCompatibilidadRolesJwt() {
        assertEquals(
            "El token de Google no es válido o ha expirado.",
            GoogleAuthHelper.resolverMensajeErrorGoogleHttp(
                401,
                """{"detail":"El token de Google no es válido o ha expirado."}"""
            )
        )
        assertEquals(
            "Por política de seguridad, este rol debe iniciar sesión con correo y contraseña.",
            GoogleAuthHelper.resolverMensajeErrorGoogleHttp(
                403,
                """{"detail":"Por política de seguridad, este rol debe iniciar sesión con correo y contraseña."}"""
            )
        )
        assertEquals(
            "El inicio de sesión con Google no está configurado en el servidor.",
            GoogleAuthHelper.resolverMensajeErrorGoogleHttp(
                503,
                """{"detail":"El inicio de sesión con Google no está configurado en el servidor."}"""
            )
        )
        val msg500 = GoogleAuthHelper.resolverMensajeErrorGoogleHttp(500, "<html>Traceback secret</html>")
        assertEquals("Error del servidor (500). Intenta nuevamente más tarde.", msg500)
        assertFalse(msg500.contains("Traceback"))

        val gson = Gson()
        val vetJson = """
            {
                "mensaje": "Bienvenido, Laura Rios",
                "email": "laura@gmail.com",
                "id_usuario": 18,
                "nombre": "Laura",
                "apellido": "Rios",
                "telefono": "3001112233",
                "matricula_profesional": "MV-555",
                "foto_perfil": "https://lh3.googleusercontent.com/a/vet",
                "foto": "https://lh3.googleusercontent.com/a/vet",
                "id_rol": 3,
                "es_nuevo_usuario": false,
                "tokens": {"refresh": "ref-jwt", "access": "acc-jwt"}
            }
        """.trimIndent()
        val parsed = gson.fromJson(vetJson, LoginResponse::class.java)
        assertEquals(inicio_sesion.ROL_VETERINARIO, parsed.idRol)
        assertEquals(false, parsed.esNuevoUsuario)
        assertEquals("3001112233", parsed.telefono)
        assertEquals("MV-555", parsed.matriculaProfesional)
        assertEquals("acc-jwt", parsed.tokens.access)
        assertEquals("ref-jwt", parsed.tokens.refresh)
    }

    @Test
    fun mapearExcepcionCredentialManager_noTragaFallosOAuthDisfrazadosDeCancelacion() {
        val sha1Local = "69:61:DF:AD:0F:93:CD:01:2D:3D:A5:63:3E:F3:C2:D6:E2:F9:D8:ED"

        // 1. Cancelación dentro del fallback GetSignInWithGoogleOption tras NoCredentialException ([28444]/[16])
        val resFallbackCancel = GoogleAuthHelper.mapearExcepcionCredentialManager(
            e = GetCredentialCancellationException("activity is cancelled by the user."),
            vinoDeFallbackNoCredential = true,
            duracionMs = 3500L,
            sha1Actual = sha1Local
        )
        assertTrue(resFallbackCancel is GoogleAuthHelper.ResultadoObtenerToken.Error)
        val msgFallback = (resFallbackCancel as GoogleAuthHelper.ResultadoObtenerToken.Error).mensajeUsuario
        assertTrue(msgFallback.contains("SHA-1", ignoreCase = true))
        assertTrue(msgFallback.contains(sha1Local))

        // 2. Cancelación legítima del usuario en el selector primario (> 1800ms y sin fallback)
        val resUserCancel = GoogleAuthHelper.mapearExcepcionCredentialManager(
            e = GetCredentialCancellationException("user cancelled"),
            vinoDeFallbackNoCredential = false,
            duracionMs = 4200L,
            sha1Actual = sha1Local
        )
        assertTrue(resUserCancel is GoogleAuthHelper.ResultadoObtenerToken.Cancelado)

        // 3. Error explícito [28444] / DeveloperError / UNREGISTERED_ON_API_CONSOLE
        val resDevError = GoogleAuthHelper.mapearExcepcionCredentialManager(
            e = GetCredentialCustomException("TYPE_DEVELOPER_ERROR", "[28444] Developer console is not set up correctly."),
            vinoDeFallbackNoCredential = false,
            duracionMs = 500L,
            sha1Actual = sha1Local
        )
        assertTrue(resDevError is GoogleAuthHelper.ResultadoObtenerToken.Error)
        val msgDev = (resDevError as GoogleAuthHelper.ResultadoObtenerToken.Error).mensajeUsuario
        assertTrue(msgDev.contains("OAuth", ignoreCase = true))
        assertTrue(msgDev.contains(sha1Local))

        // 4. Falta proveedor / Play Services
        val resProvider = GoogleAuthHelper.mapearExcepcionCredentialManager(
            e = GetCredentialProviderConfigurationException("no provider"),
            vinoDeFallbackNoCredential = false,
            duracionMs = 100L,
            sha1Actual = sha1Local
        )
        assertTrue(resProvider is GoogleAuthHelper.ResultadoObtenerToken.Error)

        // 5. NoCredentialException incluye el SHA-1 actual para diagnóstico
        val resNoCred = GoogleAuthHelper.mapearExcepcionCredentialManager(
            e = NoCredentialException("No credentials available"),
            vinoDeFallbackNoCredential = true,
            duracionMs = 900L,
            sha1Actual = sha1Local
        )
        assertTrue(resNoCred is GoogleAuthHelper.ResultadoObtenerToken.Error)
        assertTrue((resNoCred as GoogleAuthHelper.ResultadoObtenerToken.Error).mensajeUsuario.contains(sha1Local))
    }

    @Test
    fun googleAuthHelper_noExponePasswordDeterministicaSinVerificacion() {
        val metodos = GoogleAuthHelper::class.java.declaredMethods.map { it.name }
        assertFalse(
            "GoogleAuthHelper no debe generar contraseñas determinísticas para autenticar o registrar sin verificación",
            metodos.contains("generarPasswordGoogleDeterministica")
        )
    }

    @Test
    fun resolverMensajeErrorHttp_manejaBloqueo429PorIntentosFallidos() {
        val msg429Json = inicio_sesion.resolverMensajeErrorHttp(
            429,
            """{"detail":"Has alcanzado el límite de 6 intentos fallidos. Por seguridad, el acceso a esta cuenta se ha bloqueado por 15 minutos."}"""
        )
        assertTrue(msg429Json.contains("6 intentos fallidos"))

        val msg429Fallback = inicio_sesion.resolverMensajeErrorHttp(429, "")
        assertTrue(msg429Fallback.contains("15 minutos"))
    }

    private class FakeChain(private val initialRequest: Request) : Interceptor.Chain {
        var capturedRequest: Request? = null

        override fun request(): Request = initialRequest

        override fun proceed(request: Request): Response {
            capturedRequest = request
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("{}".toResponseBody("application/json".toMediaType()))
                .build()
        }

        override fun connection(): Connection? = null
        override fun call(): Call = throw UnsupportedOperationException()
        override fun connectTimeoutMillis(): Int = 1000
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 1000
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 1000
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }
}
