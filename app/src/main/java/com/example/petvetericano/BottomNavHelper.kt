package com.example.petvetericano

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import com.example.petvetericano.databinding.LayoutBottomNavCiudadanoBinding
import com.example.petvetericano.databinding.LayoutBottomNavVeterinarioBinding

object BottomNavHelper {

    enum class CiudadanoTab { INICIO, REPORTES, EVENTOS, PERFIL }

    enum class VeterinarioTab { INICIO, PETICIONES, MALETIN, PERFIL }

    private const val EXTRA_DESDE_BOTTOM_NAV = "com.example.petvetericano.EXTRA_DESDE_BOTTOM_NAV"
    private const val DURACION_FADE_CONTENIDO_MS = 120L

    private val historialCiudadano = mutableListOf<CiudadanoTab>()
    private val historialVeterinario = mutableListOf<VeterinarioTab>()

    fun limpiarHistorial() {
        historialCiudadano.clear()
        historialVeterinario.clear()
    }

    fun aplicarInsetSuperior(view: View) {
        val initialTop = view.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
            v.setPadding(v.paddingLeft, initialTop + top, v.paddingRight, v.paddingBottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    fun aplicarInsetInferior(view: View) {
        val initialBottom = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, initialBottom + bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    private fun registrarTabCiudadano(activeTab: CiudadanoTab) {
        historialVeterinario.clear()
        if (activeTab == CiudadanoTab.INICIO) {
            historialCiudadano.clear()
            historialCiudadano.add(CiudadanoTab.INICIO)
        } else {
            if (historialCiudadano.isEmpty()) {
                historialCiudadano.add(CiudadanoTab.INICIO)
            }
            historialCiudadano.remove(activeTab)
            historialCiudadano.add(activeTab)
        }
    }

    private fun registrarTabVeterinario(activeTab: VeterinarioTab) {
        historialCiudadano.clear()
        if (activeTab == VeterinarioTab.INICIO) {
            historialVeterinario.clear()
            historialVeterinario.add(VeterinarioTab.INICIO)
        } else {
            if (historialVeterinario.isEmpty()) {
                historialVeterinario.add(VeterinarioTab.INICIO)
            }
            historialVeterinario.remove(activeTab)
            historialVeterinario.add(activeTab)
        }
    }

    private fun claseCiudadano(tab: CiudadanoTab): Class<out Activity> = when (tab) {
        CiudadanoTab.INICIO -> bienvenida::class.java
        CiudadanoTab.REPORTES -> MisReportesActivity::class.java
        CiudadanoTab.EVENTOS -> Eventos::class.java
        CiudadanoTab.PERFIL -> editar_perfil::class.java
    }

    private fun claseVeterinario(tab: VeterinarioTab): Class<out Activity> = when (tab) {
        VeterinarioTab.INICIO -> dahsboard_veterianrio::class.java
        VeterinarioTab.PETICIONES -> PetitionsListActivity::class.java
        VeterinarioTab.MALETIN -> MaletinMedicamentosActivity::class.java
        VeterinarioTab.PERFIL -> OpcionesEditarPerfilActivity::class.java
    }

    private fun aplicarEstadoTab(
        context: Context,
        itemView: View,
        pillView: View,
        iconView: ImageView,
        labelView: TextView,
        activo: Boolean
    ) {
        itemView.isSelected = activo
        if (activo) {
            pillView.setBackgroundResource(R.drawable.bg_nav_pill_active)
            ImageViewCompat.setImageTintList(
                iconView,
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary))
            )
            labelView.setTextColor(ContextCompat.getColor(context, R.color.primary))
            labelView.typeface = ResourcesCompat.getFont(context, R.font.nunito_bold)
        } else {
            pillView.background = null
            ImageViewCompat.setImageTintList(
                iconView,
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_secondary))
            )
            labelView.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            labelView.typeface = ResourcesCompat.getFont(context, R.font.nunito)
        }
    }

    private fun ejecutarNavegacion(
        onBeforeNavigate: ((proceed: () -> Unit) -> Unit)?,
        accion: () -> Unit
    ) {
        if (onBeforeNavigate != null) onBeforeNavigate.invoke(accion) else accion()
    }

    fun navegarACiudadano(activity: Activity, destino: CiudadanoTab) {
        abrirTab(activity, claseCiudadano(destino))
    }

    fun navegarAVeterinario(activity: Activity, destino: VeterinarioTab) {
        abrirTab(activity, claseVeterinario(destino))
    }

    private fun retrocederCiudadano(activity: Activity, activeTab: CiudadanoTab) {
        historialCiudadano.remove(activeTab)
        val destino = historialCiudadano.lastOrNull() ?: CiudadanoTab.INICIO
        abrirTab(activity, claseCiudadano(destino))
    }

    private fun retrocederVeterinario(activity: Activity, activeTab: VeterinarioTab) {
        historialVeterinario.remove(activeTab)
        val destino = historialVeterinario.lastOrNull() ?: VeterinarioTab.INICIO
        abrirTab(activity, claseVeterinario(destino))
    }

    fun setupCiudadano(
        activity: ComponentActivity,
        binding: LayoutBottomNavCiudadanoBinding,
        activeTab: CiudadanoTab,
        onBeforeNavigate: ((proceed: () -> Unit) -> Unit)? = null,
        onReselectCurrent: (() -> Unit)? = null
    ) {
        registrarTabCiudadano(activeTab)
        aplicarInsetInferior(binding.root)
        animarContenidoSiVieneDeNav(activity, binding.root)

        if (activeTab != CiudadanoTab.INICIO) {
            activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    ejecutarNavegacion(onBeforeNavigate) {
                        retrocederCiudadano(activity, activeTab)
                    }
                }
            })
        }

        aplicarEstadoTab(
            activity,
            binding.navItemInicio,
            binding.navPillInicio,
            binding.ivNavInicio,
            binding.tvNavInicio,
            activeTab == CiudadanoTab.INICIO
        )
        aplicarEstadoTab(
            activity,
            binding.navItemReportes,
            binding.navPillReportes,
            binding.ivNavReportes,
            binding.tvNavReportes,
            activeTab == CiudadanoTab.REPORTES
        )
        aplicarEstadoTab(
            activity,
            binding.navItemEventos,
            binding.navPillEventos,
            binding.ivNavEventos,
            binding.tvNavEventos,
            activeTab == CiudadanoTab.EVENTOS
        )
        aplicarEstadoTab(
            activity,
            binding.navItemPerfil,
            binding.navPillPerfil,
            binding.ivNavPerfil,
            binding.tvNavPerfil,
            activeTab == CiudadanoTab.PERFIL
        )

        binding.navItemInicio.setOnClickListener {
            if (activeTab == CiudadanoTab.INICIO) {
                onReselectCurrent?.invoke()
            } else {
                ejecutarNavegacion(onBeforeNavigate) {
                    navegarACiudadano(activity, CiudadanoTab.INICIO)
                }
            }
        }

        binding.navItemReportes.setOnClickListener {
            if (activeTab == CiudadanoTab.REPORTES) {
                onReselectCurrent?.invoke()
            } else {
                ejecutarNavegacion(onBeforeNavigate) {
                    navegarACiudadano(activity, CiudadanoTab.REPORTES)
                }
            }
        }

        binding.navItemEventos.setOnClickListener {
            if (activeTab == CiudadanoTab.EVENTOS) {
                onReselectCurrent?.invoke()
            } else {
                ejecutarNavegacion(onBeforeNavigate) {
                    navegarACiudadano(activity, CiudadanoTab.EVENTOS)
                }
            }
        }

        binding.navItemPerfil.setOnClickListener {
            if (activeTab == CiudadanoTab.PERFIL) {
                onReselectCurrent?.invoke()
            } else {
                ejecutarNavegacion(onBeforeNavigate) {
                    navegarACiudadano(activity, CiudadanoTab.PERFIL)
                }
            }
        }
    }

    fun setupVeterinario(
        activity: ComponentActivity,
        binding: LayoutBottomNavVeterinarioBinding,
        activeTab: VeterinarioTab,
        onBeforeNavigate: ((proceed: () -> Unit) -> Unit)? = null,
        onReselectCurrent: (() -> Unit)? = null
    ) {
        registrarTabVeterinario(activeTab)
        aplicarInsetInferior(binding.root)
        animarContenidoSiVieneDeNav(activity, binding.root)

        if (activeTab != VeterinarioTab.INICIO) {
            activity.onBackPressedDispatcher.addCallback(activity, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    ejecutarNavegacion(onBeforeNavigate) {
                        retrocederVeterinario(activity, activeTab)
                    }
                }
            })
        }

        aplicarEstadoTab(
            activity,
            binding.navItemInicio,
            binding.navPillInicio,
            binding.ivNavInicio,
            binding.tvNavInicio,
            activeTab == VeterinarioTab.INICIO
        )
        aplicarEstadoTab(
            activity,
            binding.navItemPeticiones,
            binding.navPillPeticiones,
            binding.ivNavPeticiones,
            binding.tvNavPeticiones,
            activeTab == VeterinarioTab.PETICIONES
        )
        aplicarEstadoTab(
            activity,
            binding.navItemMaletin,
            binding.navPillMaletin,
            binding.ivNavMaletin,
            binding.tvNavMaletin,
            activeTab == VeterinarioTab.MALETIN
        )
        aplicarEstadoTab(
            activity,
            binding.navItemPerfil,
            binding.navPillPerfil,
            binding.ivNavPerfil,
            binding.tvNavPerfil,
            activeTab == VeterinarioTab.PERFIL
        )

        binding.navItemInicio.setOnClickListener {
            if (activeTab == VeterinarioTab.INICIO) {
                onReselectCurrent?.invoke()
            } else {
                ejecutarNavegacion(onBeforeNavigate) {
                    navegarAVeterinario(activity, VeterinarioTab.INICIO)
                }
            }
        }

        binding.navItemPeticiones.setOnClickListener {
            if (activeTab == VeterinarioTab.PETICIONES) {
                onReselectCurrent?.invoke()
            } else {
                ejecutarNavegacion(onBeforeNavigate) {
                    navegarAVeterinario(activity, VeterinarioTab.PETICIONES)
                }
            }
        }

        binding.navItemMaletin.setOnClickListener {
            if (activeTab == VeterinarioTab.MALETIN) {
                onReselectCurrent?.invoke()
            } else {
                ejecutarNavegacion(onBeforeNavigate) {
                    navegarAVeterinario(activity, VeterinarioTab.MALETIN)
                }
            }
        }

        binding.navItemPerfil.setOnClickListener {
            if (activeTab == VeterinarioTab.PERFIL) {
                onReselectCurrent?.invoke()
            } else {
                ejecutarNavegacion(onBeforeNavigate) {
                    navegarAVeterinario(activity, VeterinarioTab.PERFIL)
                }
            }
        }
    }

    private fun abrirTab(activity: Activity, destino: Class<out Activity>) {
        val intent = Intent(activity, destino).apply {
            putExtra(EXTRA_DESDE_BOTTOM_NAV, true)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val options = ActivityOptions.makeCustomAnimation(activity, 0, 0).toBundle()
        activity.startActivity(intent, options)
        desactivarAnimacionCierre(activity)
        activity.finish()
    }

    private fun animarContenidoSiVieneDeNav(activity: Activity, bottomNavRoot: View) {
        val desdeNav = activity.intent?.getBooleanExtra(EXTRA_DESDE_BOTTOM_NAV, false) == true
        if (!desdeNav) return
        activity.intent?.removeExtra(EXTRA_DESDE_BOTTOM_NAV)
        desactivarAnimacionApertura(activity)
        val parent = bottomNavRoot.parent as? ViewGroup ?: return
        val interpolator = DecelerateInterpolator()
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child === bottomNavRoot) continue
            child.alpha = 0f
            child.animate()
                .alpha(1f)
                .setDuration(DURACION_FADE_CONTENIDO_MS)
                .setInterpolator(interpolator)
                .start()
        }
    }

    private fun desactivarAnimacionApertura(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }
    }

    private fun desactivarAnimacionCierre(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            activity.overridePendingTransition(0, 0)
        }
    }
}
