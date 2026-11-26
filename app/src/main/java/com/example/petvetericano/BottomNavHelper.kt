package com.example.petvetericano

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
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

    fun setupCiudadano(
        activity: Activity,
        binding: LayoutBottomNavCiudadanoBinding,
        activeTab: CiudadanoTab,
        onBeforeNavigate: ((proceed: () -> Unit) -> Unit)? = null,
        onReselectCurrent: (() -> Unit)? = null
    ) {
        aplicarInsetInferior(binding.root)

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
                    val intent = Intent(activity, bienvenida::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                    }
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
        }

        binding.navItemReportes.setOnClickListener {
            if (activeTab == CiudadanoTab.REPORTES) {
                onReselectCurrent?.invoke()
            } else {
                ejecutarNavegacion(onBeforeNavigate) {
                    navegarATabHermano(
                        activity = activity,
                        destino = MisReportesActivity::class.java,
                        esDesdeInicio = (activeTab == CiudadanoTab.INICIO)
                    )
                }
            }
        }

        binding.navItemEventos.setOnClickListener {
            if (activeTab == CiudadanoTab.EVENTOS) {
                onReselectCurrent?.invoke()
            } else {
                ejecutarNavegacion(onBeforeNavigate) {
                    navegarATabHermano(
                        activity = activity,
                        destino = Eventos::class.java,
                        esDesdeInicio = (activeTab == CiudadanoTab.INICIO)
                    )
                }
            }
        }

        binding.navItemPerfil.setOnClickListener {
            if (activeTab == CiudadanoTab.PERFIL) {
                onReselectCurrent?.invoke()
            } else {
                ejecutarNavegacion(onBeforeNavigate) {
                    navegarATabHermano(
                        activity = activity,
                        destino = editar_perfil::class.java,
                        esDesdeInicio = (activeTab == CiudadanoTab.INICIO)
                    )
                }
            }
        }
    }

    fun setupVeterinario(
        activity: Activity,
        binding: LayoutBottomNavVeterinarioBinding,
        activeTab: VeterinarioTab,
        onBeforeNavigate: ((proceed: () -> Unit) -> Unit)? = null,
        onReselectCurrent: (() -> Unit)? = null
    ) {
        aplicarInsetInferior(binding.root)

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
                    val intent = Intent(activity, dahsboard_veterianrio::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_NO_ANIMATION
                    }
                    activity.startActivity(intent)
                    activity.finish()
                }
            }
        }

        binding.navItemPeticiones.setOnClickListener {
            if (activeTab == VeterinarioTab.PETICIONES) {
                onReselectCurrent?.invoke()
            } else {
                ejecutarNavegacion(onBeforeNavigate) {
                    navegarATabHermano(
                        activity = activity,
                        destino = PetitionsListActivity::class.java,
                        esDesdeInicio = (activeTab == VeterinarioTab.INICIO)
                    )
                }
            }
        }

        binding.navItemMaletin.setOnClickListener {
            if (activeTab == VeterinarioTab.MALETIN) {
                onReselectCurrent?.invoke()
            } else {
                ejecutarNavegacion(onBeforeNavigate) {
                    navegarATabHermano(
                        activity = activity,
                        destino = MaletinMedicamentosActivity::class.java,
                        esDesdeInicio = (activeTab == VeterinarioTab.INICIO)
                    )
                }
            }
        }

        binding.navItemPerfil.setOnClickListener {
            if (activeTab == VeterinarioTab.PERFIL) {
                onReselectCurrent?.invoke()
            } else {
                ejecutarNavegacion(onBeforeNavigate) {
                    navegarATabHermano(
                        activity = activity,
                        destino = OpcionesEditarPerfilActivity::class.java,
                        esDesdeInicio = (activeTab == VeterinarioTab.INICIO)
                    )
                }
            }
        }
    }

    private fun navegarATabHermano(
        activity: Activity,
        destino: Class<out Activity>,
        esDesdeInicio: Boolean
    ) {
        val intent = Intent(activity, destino).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION
        }
        activity.startActivity(intent)
        if (!esDesdeInicio) {
            activity.finish()
        }
    }
}
