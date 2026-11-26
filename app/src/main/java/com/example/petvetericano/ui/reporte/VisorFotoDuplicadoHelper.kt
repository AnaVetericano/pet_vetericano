package com.example.petvetericano.ui.reporte

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.example.petvetericano.databinding.DialogVisorFotoDuplicadoBinding

/**
 * Visor en pantalla completa superpuesto sobre la alerta de posible duplicado,
 * reutilizando la URL autorizada para mostrar la fotografia con sus proporciones
 * originales, zoom con dos dedos, desplazamiento y cierre seguro sin perder estado.
 */
internal object VisorFotoDuplicadoHelper {

    fun mostrar(
        context: Context,
        fotoUrl: String,
        onDismiss: () -> Unit = {}
    ): Dialog {
        val binding = DialogVisorFotoDuplicadoBinding.inflate(LayoutInflater.from(context))

        val visorDialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        visorDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        visorDialog.setContentView(binding.root)
        visorDialog.setCancelable(true)
        visorDialog.setCanceledOnTouchOutside(false)
        visorDialog.window?.let { window ->
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            window.setBackgroundDrawable(ColorDrawable(Color.BLACK))
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, binding.root).isAppearanceLightStatusBars = false
        }

        val closeLp = binding.btnCerrarVisor.layoutParams as ViewGroup.MarginLayoutParams
        val baseTopMargin = closeLp.topMargin
        val baseEndMargin = closeLp.marginEnd
        ViewCompat.setOnApplyWindowInsetsListener(binding.btnCerrarVisor) { button, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val lp = button.layoutParams as ViewGroup.MarginLayoutParams
            val endInset = if (button.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                bars.left
            } else {
                bars.right
            }
            lp.topMargin = baseTopMargin + bars.top
            lp.marginEnd = baseEndMargin + endInset
            button.layoutParams = lp
            insets
        }

        binding.pbCargandoFoto.visibility = View.VISIBLE
        binding.layoutErrorCarga.visibility = View.GONE

        Glide.with(context)
            .load(fotoUrl)
            .centerInside()
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.pbCargandoFoto.visibility = View.GONE
                    binding.layoutErrorCarga.visibility = View.VISIBLE
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable,
                    model: Any,
                    target: Target<Drawable>?,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    binding.pbCargandoFoto.visibility = View.GONE
                    binding.layoutErrorCarga.visibility = View.GONE
                    return false
                }
            })
            .into(binding.ivFotoVisor)

        binding.btnCerrarVisor.setOnClickListener {
            visorDialog.dismiss()
        }

        visorDialog.setOnDismissListener {
            Glide.with(context).clear(binding.ivFotoVisor)
            onDismiss()
        }

        visorDialog.show()
        return visorDialog
    }
}
