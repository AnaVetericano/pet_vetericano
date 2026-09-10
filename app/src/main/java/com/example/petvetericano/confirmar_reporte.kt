package com.example.petvetericano

import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.petvetericano.databinding.ActivityConfirmarReporteBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class confirmar_reporte : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmarReporteBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityConfirmarReporteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        obtenerCantidadFotos()

        val descripcionRecibida = intent.getStringExtra("DESCRIPCION")
        binding.descri.text = descripcionRecibida ?: "Sin descripción"

        val tipoReporte = intent.getStringExtra("TIPO_REPORTE")
        binding.tvTipoReporte.text = tipoReporte

        val latitud = intent.getDoubleExtra("LATITUD", 0.0)
        val longitud = intent.getDoubleExtra("LONGITUD", 0.0)

        if (latitud != 0.0 && longitud != 0.0) {
            try {
                val geocoder = Geocoder(this, Locale.getDefault())
                val direcciones = geocoder.getFromLocation(latitud, longitud, 1)

                if (!direcciones.isNullOrEmpty()) {
                    val direccionReal = direcciones[0].getAddressLine(0)
                    binding.ubicacionedit.text = direccionReal
                } else {
                    binding.ubicacionedit.text = "$latitud, $longitud"
                }
            } catch (e: Exception) {
                binding.ubicacionedit.text = "$latitud, $longitud"
            }
        } else {
            binding.ubicacionedit.text = "Ubicación no seleccionada"
        }

        binding.btnenvR.setOnClickListener {
            val tipo = binding.tvTipoReporte.text.toString()
            val lugar = binding.ubicacionedit.text.toString()
            val descripcion = binding.descri.text.toString()

            enviarCorreoSilenciosoYContinuar(tipo, lugar, descripcion, latitud, longitud)
        }

        binding.ubicacionedit.setOnClickListener {
            val intent = Intent(this, reportar_peticionn::class.java)
            startActivity(intent)
        }

        binding.tipoPeticion.setOnClickListener {
            val intent = Intent(this, reportar_peticion::class.java)
            startActivity(intent)
        }

        binding.descripedit.setOnClickListener {
            val intent = Intent(this, reportar_peticionnn::class.java)
            startActivity(intent)
        }

        binding.archivosedi.setOnClickListener {
            val intent = Intent(this, reportar_peticionnn::class.java)
            startActivity(intent)
        }
    }

    private fun enviarCorreoSilenciosoYContinuar(tipo: String?, lugar: String, descripcion: String, latitud: Double, longitud: Double) {
        binding.btnenvR.isEnabled = false
        Toast.makeText(this, "Enviando reporte, por favor espere...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val correoRemitente = "TU_CORREO_DE_GMAIL@gmail.com"
                val passwordRemitente = "TU_CONTRASEÑA_DE_APLICACION"
                val correoDestinatario = "jhormanquina17@gmail.com"

                val props = Properties()
                props.put("mail.smtp.auth", "true")
                props.put("mail.smtp.starttls.enable", "true")
                props.put("mail.smtp.host", "smtp.gmail.com")
                props.put("mail.smtp.port", "587")

                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(correoRemitente, passwordRemitente)
                    }
                })

                val message = MimeMessage(session)
                message.setFrom(InternetAddress(correoRemitente))
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(correoDestinatario))
                message.subject = "Nuevo Reporte en Vetericano: $tipo"

                message.setText("Hola,\n\nSe ha generado un nuevo reporte en la plataforma:\n\n" +
                        "Tipo de petición: $tipo\n" +
                        "Ubicación: $lugar\n" +
                        "Descripción: $descripcion\n\n" +
                        "Coordenadas GPS: $latitud, $longitud\n\n" +
                        "Atentamente,\nApp Vetericano.")

                Transport.send(message)

                withContext(Dispatchers.Main) {
                    val nuevoIntent = Intent(this@confirmar_reporte, reporte_enviado::class.java).apply {
                        putExtra("TIPO_REPORTE", tipo)
                        putExtra("LATITUD", latitud)
                        putExtra("LONGITUD", longitud)
                    }
                    startActivity(nuevoIntent)
                    finish()
                }

            }catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    // Esto te dirá exactamente si es un error de autenticación (Auth failed) o de red
                    Toast.makeText(this@confirmar_reporte, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    binding.btnenvR.isEnabled = true
                }
            }
        }
    }

    private fun obtenerCantidadFotos() {
        val archivos: ArrayList<Uri>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra("ARCHIVOS", Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra("ARCHIVOS")
        }

        val cantidad = archivos?.size ?: 0

        binding.numfoto.text = if (cantidad == 1) {
            "1 foto"
        } else {
            "$cantidad fotos"
            //jjj
        }
    }
}