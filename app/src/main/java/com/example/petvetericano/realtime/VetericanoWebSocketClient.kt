package com.example.petvetericano.realtime

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Cliente WebSocket delgado sobre OkHttp.
 * - Envía el identificador nativo explícito en `X-Vetericano-Client` (sin `Origin`).
 * - Envía el JWT exclusivamente en el primer frame JSON (`{"type":"auth","token":"..."}`), nunca en URL ni logs.
 * - OkHttp responde automáticamente a los PING RFC 6455 de uvicorn; además permite enviar `{"type":"ping"}`.
 */
open class VetericanoWebSocketClient(
    private val httpClient: OkHttpClient = crearClientePorDefecto()
) {

    interface Listener {
        fun alAbrir()
        fun alRecibirMensaje(mensaje: MensajeServidorWs)
        fun alRecibirPayloadInvalido()
        fun alCerrar(codigo: Int, motivo: String)
        fun alFallar(codigoHttp: Int?, error: Throwable)
    }

    @Volatile
    private var webSocketActual: WebSocket? = null

    @Synchronized
    open fun conectar(
        wsUrl: String,
        tokenJwt: String,
        clientId: String,
        permitirCleartext: Boolean,
        listener: Listener
    ): Boolean {
        val urlValida = WebSocketProtocolo.validarUrl(wsUrl, permitirCleartext) ?: return false
        val tokenLimpio = tokenJwt.trim()
        val clienteLimpio = clientId.trim()
        if (tokenLimpio.isEmpty() || clienteLimpio.isEmpty()) return false

        // Cierra cualquier instancia previa antes de abrir una nueva.
        cerrarInterno(WebSocketProtocolo.CIERRE_NORMAL, "reemplazada")

        val request = Request.Builder()
            .url(urlValida)
            .header(WebSocketProtocolo.HEADER_CLIENTE_NATIVO, clienteLimpio)
            .build()

        val wsListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== webSocketActual) {
                    webSocket.close(WebSocketProtocolo.CIERRE_NORMAL, "obsoleta")
                    return
                }
                listener.alAbrir()
                webSocket.send(WebSocketEventParser.construirFrameAuth(tokenLimpio))
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== webSocketActual) return
                val mensaje = WebSocketEventParser.parsearMensaje(text)
                if (mensaje != null) {
                    listener.alRecibirMensaje(mensaje)
                } else {
                    listener.alRecibirPayloadInvalido()
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== webSocketActual) return
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== webSocketActual) return
                synchronized(this@VetericanoWebSocketClient) {
                    if (webSocketActual === webSocket) webSocketActual = null
                }
                listener.alCerrar(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket !== webSocketActual) return
                synchronized(this@VetericanoWebSocketClient) {
                    if (webSocketActual === webSocket) webSocketActual = null
                }
                listener.alFallar(response?.code, t)
            }
        }

        webSocketActual = httpClient.newWebSocket(request, wsListener)
        return true
    }

    open fun enviarPing(): Boolean {
        val ws = webSocketActual ?: return false
        return ws.send(WebSocketEventParser.construirFramePing())
    }

    @Synchronized
    open fun cerrar(codigo: Int = WebSocketProtocolo.CIERRE_NORMAL, motivo: String = "normal") {
        cerrarInterno(codigo, motivo)
    }

    open fun tieneSocketActivo(): Boolean = webSocketActual != null

    private fun cerrarInterno(codigo: Int, motivo: String) {
        val ws = webSocketActual ?: return
        webSocketActual = null
        try {
            ws.close(codigo, motivo)
        } catch (_: Exception) {
            try {
                ws.cancel()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        fun crearClientePorDefecto(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // requerido para sockets de larga duración
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
