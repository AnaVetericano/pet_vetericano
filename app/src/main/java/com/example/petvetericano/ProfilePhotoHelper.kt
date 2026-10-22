package com.example.petvetericano

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class CambioPerfilItem(
    val campo: String,
    val valorAnterior: String,
    val valorNuevo: String
)

object ProfilePhotoHelper {

    /**
     * Carga la foto de perfil en el ImageView indicado (URL de Cloudinary, data URI o ruta),
     * o muestra el icono por defecto (@drawable/ic_profile_default) si no tiene foto personalizada.
     */
    fun cargarFotoPerfil(context: Context, imageView: ImageView, fotoUrlODataUri: String?) {
        val valor = fotoUrlODataUri?.trim() ?: ""
        if (valor.isEmpty()) {
            try {
                Glide.with(context).clear(imageView)
            } catch (_: Exception) { }
            imageView.setImageResource(R.drawable.ic_profile_default)
            return
        }

        try {
            if (valor.startsWith("data:image", ignoreCase = true)) {
                val base64Part = valor.substringAfter("base64,", "")
                if (base64Part.isNotEmpty()) {
                    val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                    Glide.with(context)
                        .load(bytes)
                        .placeholder(R.drawable.ic_profile_default)
                        .error(R.drawable.ic_profile_default)
                        .centerCrop()
                        .into(imageView)
                    return
                }
            }

            Glide.with(context)
                .load(valor)
                .placeholder(R.drawable.ic_profile_default)
                .error(R.drawable.ic_profile_default)
                .centerCrop()
                .into(imageView)
        } catch (_: Exception) {
            imageView.setImageResource(R.drawable.ic_profile_default)
        }
    }

    /**
     * Previsualiza en pantalla un Uri local seleccionado de la galería antes de guardar cambios.
     */
    fun previsualizarUri(context: Context, imageView: ImageView, uri: Uri) {
        try {
            Glide.with(context)
                .load(uri)
                .placeholder(R.drawable.ic_profile_default)
                .error(R.drawable.ic_profile_default)
                .centerCrop()
                .into(imageView)
        } catch (_: Exception) {
            try {
                imageView.setImageURI(uri)
            } catch (_: Exception) {
                imageView.setImageResource(R.drawable.ic_profile_default)
            }
        }
    }

    /**
     * Convierte un Uri de imagen en una cadena Data URI (data:image/jpeg;base64,...)
     * redimensionando a máximo [maxSidePx] y comprimiendo en JPEG para que el envío al
     * backend y a Cloudinary sea rápido desde cualquier dispositivo.
     */
    fun uriABase64DataUri(
        context: Context,
        uri: Uri,
        maxSidePx: Int = 512,
        quality: Int = 82
    ): String? {
        return try {
            val bytesOriginales = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null

            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytesOriginales, 0, bytesOriginales.size, boundsOptions)

            var sampleSize = 1
            val maxDim = max(boundsOptions.outWidth, boundsOptions.outHeight)
            if (maxDim > maxSidePx * 2) {
                sampleSize = maxDim / maxSidePx
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bitmapDecodificado = BitmapFactory.decodeByteArray(
                bytesOriginales,
                0,
                bytesOriginales.size,
                decodeOptions
            ) ?: return null

            val w = bitmapDecodificado.width
            val h = bitmapDecodificado.height
            val mayorLado = max(w, h)
            val bitmapFinal = if (mayorLado > maxSidePx && mayorLado > 0) {
                val escala = maxSidePx.toFloat() / mayorLado.toFloat()
                val nuevoAncho = (w * escala).roundToInt().coerceAtLeast(1)
                val nuevoAlto = (h * escala).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmapDecodificado, nuevoAncho, nuevoAlto, true)
            } else {
                bitmapDecodificado
            }

            val output = ByteArrayOutputStream()
            bitmapFinal.compress(Bitmap.CompressFormat.JPEG, quality, output)
            val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            "data:image/jpeg;base64,$base64"
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Muestra el diálogo de confirmación "¿Guardar cambios?" con el resumen visual de
     * cada campo modificado (Antes → Después) siguiendo los lineamientos de Material Design 3.
     */
    fun mostrarDialogoGuardarCambiosMD3(
        activity: android.app.Activity,
        cambios: List<CambioPerfilItem>,
        onConfirmarGuardar: () -> Unit
    ) {
        if (activity.isFinishing || cambios.isEmpty()) return

        val dialog = android.app.Dialog(activity)
        dialog.setContentView(R.layout.dialog_guardar_cambios_md3)
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvTitulo = dialog.findViewById<TextView>(R.id.tvTituloGuardarMd3)
        val tvSubtitulo = dialog.findViewById<TextView>(R.id.tvSubtituloGuardarMd3)
        val llListaCambios = dialog.findViewById<LinearLayout>(R.id.llListaCambiosMd3)
        val btnCancelar = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelarGuardarMd3)
        val btnConfirmar = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmarGuardarMd3)

        tvTitulo?.text = "¿Guardar cambios?"
        tvSubtitulo?.text = if (cambios.size == 1) {
            "Se actualizará el siguiente dato en tu perfil:"
        } else {
            "Se actualizarán los siguientes ${cambios.size} datos en tu perfil:"
        }

        llListaCambios?.removeAllViews()
        val inflater = LayoutInflater.from(activity)

        cambios.forEachIndexed { index, item ->
            val itemView = inflater.inflate(R.layout.item_cambio_perfil_md3, llListaCambios, false)
            val tvCampo = itemView.findViewById<TextView>(R.id.tvCampoCambioMd3)
            val tvAnterior = itemView.findViewById<TextView>(R.id.tvValorAnteriorMd3)
            val tvFlecha = itemView.findViewById<TextView>(R.id.tvFlechaCambioMd3)
            val tvNuevo = itemView.findViewById<TextView>(R.id.tvValorNuevoMd3)
            val divider = itemView.findViewById<View>(R.id.dividerCambioMd3)

            tvCampo.text = item.campo

            val anteriorLimpio = item.valorAnterior.trim()
            if (anteriorLimpio.isEmpty()) {
                tvAnterior.text = "Sin registrar"
                tvAnterior.paintFlags = tvAnterior.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            } else {
                tvAnterior.text = anteriorLimpio
                tvAnterior.paintFlags = tvAnterior.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            }

            tvFlecha.text = "→"
            tvNuevo.text = item.valorNuevo.trim().ifEmpty { "Vacío" }

            divider.visibility = if (index == cambios.lastIndex) View.GONE else View.VISIBLE
            llListaCambios?.addView(itemView)
        }

        btnCancelar?.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirmar?.setOnClickListener {
            btnConfirmar.isEnabled = false
            btnCancelar?.isEnabled = false
            btnConfirmar.text = "Guardando…"
            dialog.dismiss()
            onConfirmarGuardar()
        }

        dialog.show()
    }

    /**
     * Muestra el diálogo de confirmación para cerrar sesión siguiendo los lineamientos
     * de Material Design 3 y la identidad visual de Vetericano.
     */
    fun mostrarDialogoCerrarSesionMD3(
        activity: android.app.Activity,
        emailUsuario: String?,
        esVeterinario: Boolean,
        onConfirmarCerrarSesion: () -> Unit
    ) {
        if (activity.isFinishing) return

        val dialog = android.app.Dialog(activity)
        dialog.setContentView(R.layout.dialog_cerrar_sesion_md3)
        dialog.window?.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        )
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvTitulo = dialog.findViewById<android.widget.TextView>(R.id.tvTituloLogoutMd3)
        val cardEmail = dialog.findViewById<android.view.View>(R.id.cardEmailLogoutMd3)
        val tvEmail = dialog.findViewById<android.widget.TextView>(R.id.tvEmailLogoutMd3)
        val tvMensaje = dialog.findViewById<android.widget.TextView>(R.id.tvMensajeLogoutMd3)
        val btnCancelar = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancelarLogoutMd3)
        val btnConfirmar = dialog.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirmarLogoutMd3)

        tvTitulo?.text = "¿Cerrar sesión?"

        val cleanEmail = emailUsuario?.trim().orEmpty()
        if (cleanEmail.isNotEmpty()) {
            cardEmail?.visibility = android.view.View.VISIBLE
            tvEmail?.text = cleanEmail
        } else {
            cardEmail?.visibility = android.view.View.GONE
        }

        tvMensaje?.text = if (esVeterinario) {
            "Saldrás de tu perfil profesional en este dispositivo. Tus actas, reportes y foto de perfil seguirán sincronizados cuando vuelvas a ingresar."
        } else {
            "Saldrás de tu cuenta en este dispositivo. Tus peticiones, postulaciones y foto de perfil seguirán guardadas cuando vuelvas a ingresar."
        }

        btnCancelar?.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirmar?.setOnClickListener {
            btnConfirmar.isEnabled = false
            btnCancelar?.isEnabled = false
            btnConfirmar.text = "Saliendo…"
            dialog.dismiss()
            onConfirmarCerrarSesion()
        }

        dialog.show()
    }
}
