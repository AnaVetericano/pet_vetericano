package com.example.petvetericano

import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.petvetericano.databinding.ActivityActaAtencionBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Extensiones de ViewBinding para ActivityActaAtencionBinding.
 * Permiten que todo el código existente en ActaAtencionActivity continúe interactuando
 * con los componentes mediante binding.<campo> de forma transparente y segura,
 * tras modularizar el formulario en 3 layouts independientes (layout_acta_paso1, layout_acta_paso2, layout_acta_paso3).
 */

// ==========================================
// PASO 1 (layoutPaso1)
// ==========================================
val ActivityActaAtencionBinding.step1Layout: LinearLayout get() = layoutPaso1.root
val ActivityActaAtencionBinding.tilRadicado: TextInputLayout get() = layoutPaso1.tilRadicado
val ActivityActaAtencionBinding.etRadicado: TextInputEditText get() = layoutPaso1.etRadicado
val ActivityActaAtencionBinding.tilFechaAtencion: TextInputLayout get() = layoutPaso1.tilFechaAtencion
val ActivityActaAtencionBinding.etFechaAtencion: TextInputEditText get() = layoutPaso1.etFechaAtencion
val ActivityActaAtencionBinding.tilSolicitudAtencion: TextInputLayout get() = layoutPaso1.tilSolicitudAtencion
val ActivityActaAtencionBinding.etSolicitudAtencion: MaterialAutoCompleteTextView get() = layoutPaso1.etSolicitudAtencion
val ActivityActaAtencionBinding.tilQuienReporta: TextInputLayout get() = layoutPaso1.tilQuienReporta
val ActivityActaAtencionBinding.autoCompleteQuienReporta: MaterialAutoCompleteTextView get() = layoutPaso1.autoCompleteQuienReporta
val ActivityActaAtencionBinding.tilQuienReportaOtro: TextInputLayout get() = layoutPaso1.tilQuienReportaOtro
val ActivityActaAtencionBinding.etQuienReportaOtro: TextInputEditText get() = layoutPaso1.etQuienReportaOtro
val ActivityActaAtencionBinding.layoutDatosPropietario: LinearLayout get() = layoutPaso1.layoutDatosPropietario
val ActivityActaAtencionBinding.chipSinPropietario: MaterialButton get() = layoutPaso1.chipSinPropietario
val ActivityActaAtencionBinding.tilPropietarioNombre: TextInputLayout get() = layoutPaso1.tilPropietarioNombre
val ActivityActaAtencionBinding.etPropietarioNombre: TextInputEditText get() = layoutPaso1.etPropietarioNombre
val ActivityActaAtencionBinding.tilPropietarioCedula: TextInputLayout get() = layoutPaso1.tilPropietarioCedula
val ActivityActaAtencionBinding.etPropietarioCedula: TextInputEditText get() = layoutPaso1.etPropietarioCedula
val ActivityActaAtencionBinding.tilPropietarioTelefono: TextInputLayout get() = layoutPaso1.tilPropietarioTelefono
val ActivityActaAtencionBinding.etPropietarioTelefono: TextInputEditText get() = layoutPaso1.etPropietarioTelefono
val ActivityActaAtencionBinding.tilPropietarioEmail: TextInputLayout get() = layoutPaso1.tilPropietarioEmail
val ActivityActaAtencionBinding.etPropietarioEmail: TextInputEditText get() = layoutPaso1.etPropietarioEmail
val ActivityActaAtencionBinding.tilPropietarioBarrio: TextInputLayout get() = layoutPaso1.tilPropietarioBarrio
val ActivityActaAtencionBinding.etPropietarioBarrio: TextInputEditText get() = layoutPaso1.etPropietarioBarrio
val ActivityActaAtencionBinding.tilPropietarioDireccion: TextInputLayout get() = layoutPaso1.tilPropietarioDireccion
val ActivityActaAtencionBinding.etPropietarioDireccion: TextInputEditText get() = layoutPaso1.etPropietarioDireccion
val ActivityActaAtencionBinding.cardLugarAtencion: MaterialCardView get() = layoutPaso1.cardLugarAtencion
val ActivityActaAtencionBinding.btnCapturarUbicacion: MaterialButton get() = layoutPaso1.btnCapturarUbicacion
val ActivityActaAtencionBinding.tvLugarAtencionStatus: TextView get() = layoutPaso1.tvLugarAtencionStatus
val ActivityActaAtencionBinding.tvLugarAtencionInfo: TextView get() = layoutPaso1.tvLugarAtencionInfo
val ActivityActaAtencionBinding.etLugarAtencion: TextInputEditText get() = layoutPaso1.etLugarAtencion

// ==========================================
// PASO 2 (layoutPaso2)
// ==========================================
val ActivityActaAtencionBinding.step2Layout: LinearLayout get() = layoutPaso2.root
val ActivityActaAtencionBinding.btnAddAnimal: MaterialButton get() = layoutPaso2.btnAddAnimal
val ActivityActaAtencionBinding.containerAnimales: LinearLayout get() = layoutPaso2.containerAnimales
val ActivityActaAtencionBinding.layoutClinicaSeresSintientes: LinearLayout get() = layoutPaso2.layoutClinicaSeresSintientes
val ActivityActaAtencionBinding.tilTemperatura: TextInputLayout get() = layoutPaso2.tilTemperatura
val ActivityActaAtencionBinding.etTemperatura: TextInputEditText get() = layoutPaso2.etTemperatura
val ActivityActaAtencionBinding.tilFrecuenciaCardiaca: TextInputLayout get() = layoutPaso2.tilFrecuenciaCardiaca
val ActivityActaAtencionBinding.etFrecuenciaCardiaca: TextInputEditText get() = layoutPaso2.etFrecuenciaCardiaca
val ActivityActaAtencionBinding.tilFrecuenciaRespiratoria: TextInputLayout get() = layoutPaso2.tilFrecuenciaRespiratoria
val ActivityActaAtencionBinding.etFrecuenciaRespiratoria: TextInputEditText get() = layoutPaso2.etFrecuenciaRespiratoria
val ActivityActaAtencionBinding.tilMucosas: TextInputLayout get() = layoutPaso2.tilMucosas
val ActivityActaAtencionBinding.etMucosas: MaterialAutoCompleteTextView get() = layoutPaso2.etMucosas
val ActivityActaAtencionBinding.chipAnamnesisCalle: MaterialButton get() = layoutPaso2.chipAnamnesisCalle
val ActivityActaAtencionBinding.chipAnamnesisHerido: MaterialButton get() = layoutPaso2.chipAnamnesisHerido
val ActivityActaAtencionBinding.chipAnamnesisDesnutrido: MaterialButton get() = layoutPaso2.chipAnamnesisDesnutrido
val ActivityActaAtencionBinding.tilAnamnesis: TextInputLayout get() = layoutPaso2.tilAnamnesis
val ActivityActaAtencionBinding.etAnamnesis: TextInputEditText get() = layoutPaso2.etAnamnesis
val ActivityActaAtencionBinding.chipTratamientoCuracion: MaterialButton get() = layoutPaso2.chipTratamientoCuracion
val ActivityActaAtencionBinding.chipTratamientoMedicacion: MaterialButton get() = layoutPaso2.chipTratamientoMedicacion
val ActivityActaAtencionBinding.chipTratamientoTraslado: MaterialButton get() = layoutPaso2.chipTratamientoTraslado
val ActivityActaAtencionBinding.tilTratamiento: TextInputLayout get() = layoutPaso2.tilTratamiento
val ActivityActaAtencionBinding.etTratamiento: TextInputEditText get() = layoutPaso2.etTratamiento
val ActivityActaAtencionBinding.cardEvidenciaMultimedia: MaterialCardView get() = layoutPaso2.cardEvidenciaMultimedia
val ActivityActaAtencionBinding.tvSubtituloMedia: TextView get() = layoutPaso2.tvSubtituloMedia
val ActivityActaAtencionBinding.layoutBotonesAccionMedia: LinearLayout get() = layoutPaso2.layoutBotonesAccionMedia
val ActivityActaAtencionBinding.btnAdjuntarFoto: MaterialButton get() = layoutPaso2.btnAdjuntarFoto
val ActivityActaAtencionBinding.btnAdjuntarVideo: MaterialButton get() = layoutPaso2.btnAdjuntarVideo
val ActivityActaAtencionBinding.scrollMediaAdjuntos: HorizontalScrollView get() = layoutPaso2.scrollMediaAdjuntos
val ActivityActaAtencionBinding.containerMediaThumbnails: LinearLayout get() = layoutPaso2.containerMediaThumbnails
val ActivityActaAtencionBinding.tvConteoMedia: TextView get() = layoutPaso2.tvConteoMedia

// ==========================================
// PASO 3 (layoutPaso3)
// ==========================================
val ActivityActaAtencionBinding.step3Layout: LinearLayout get() = layoutPaso3.root
val ActivityActaAtencionBinding.layoutProcedimientosSeresSintientes: LinearLayout get() = layoutPaso3.layoutProcedimientosSeresSintientes
val ActivityActaAtencionBinding.swDesparasitacion: MaterialSwitch get() = layoutPaso3.swDesparasitacion
val ActivityActaAtencionBinding.tvEstadoDesparasitacion: TextView get() = layoutPaso3.tvEstadoDesparasitacion
val ActivityActaAtencionBinding.tilTipoPruebaDropdown: TextInputLayout get() = layoutPaso3.tilTipoPruebaDropdown
val ActivityActaAtencionBinding.autoCompleteTipoPrueba: MaterialAutoCompleteTextView get() = layoutPaso3.autoCompleteTipoPrueba
val ActivityActaAtencionBinding.tilPruebasComplementarias: TextInputLayout get() = layoutPaso3.tilPruebasComplementarias
val ActivityActaAtencionBinding.etPruebasComplementarias: TextInputEditText get() = layoutPaso3.etPruebasComplementarias
val ActivityActaAtencionBinding.chipResultadoNegativo: MaterialButton get() = layoutPaso3.chipResultadoNegativo
val ActivityActaAtencionBinding.chipResultadoPositivo: MaterialButton get() = layoutPaso3.chipResultadoPositivo
val ActivityActaAtencionBinding.chipResultadoPendiente: MaterialButton get() = layoutPaso3.chipResultadoPendiente
val ActivityActaAtencionBinding.tilResultadoPruebas: TextInputLayout get() = layoutPaso3.tilResultadoPruebas
val ActivityActaAtencionBinding.etResultadoPruebas: TextInputEditText get() = layoutPaso3.etResultadoPruebas
val ActivityActaAtencionBinding.btnAgregarExamenLista: MaterialButton get() = layoutPaso3.btnAgregarExamenLista
val ActivityActaAtencionBinding.layoutExamenesAgregados: LinearLayout get() = layoutPaso3.layoutExamenesAgregados
val ActivityActaAtencionBinding.tvExamenesAgregadosTitulo: TextView get() = layoutPaso3.tvExamenesAgregadosTitulo
val ActivityActaAtencionBinding.containerExamenesAgregados: LinearLayout get() = layoutPaso3.containerExamenesAgregados
val ActivityActaAtencionBinding.chipCompromisoAgua: MaterialButton get() = layoutPaso3.chipCompromisoAgua
val ActivityActaAtencionBinding.chipCompromisoTecho: MaterialButton get() = layoutPaso3.chipCompromisoTecho
val ActivityActaAtencionBinding.chipCompromisoVacunas: MaterialButton get() = layoutPaso3.chipCompromisoVacunas
val ActivityActaAtencionBinding.chipCompromisoEsterilizacion: MaterialButton get() = layoutPaso3.chipCompromisoEsterilizacion
val ActivityActaAtencionBinding.chipCompromisoVeterinario: MaterialButton get() = layoutPaso3.chipCompromisoVeterinario
val ActivityActaAtencionBinding.tilCompromisos: TextInputLayout get() = layoutPaso3.tilCompromisos
val ActivityActaAtencionBinding.etCompromisos: TextInputEditText get() = layoutPaso3.etCompromisos
val ActivityActaAtencionBinding.tilFundamentoLegal: TextInputLayout get() = layoutPaso3.tilFundamentoLegal
val ActivityActaAtencionBinding.etFundamentoLegal: TextInputEditText get() = layoutPaso3.etFundamentoLegal
val ActivityActaAtencionBinding.btnPlazoMenos: MaterialButton get() = layoutPaso3.btnPlazoMenos
val ActivityActaAtencionBinding.tilPlazoDias: TextInputLayout get() = layoutPaso3.tilPlazoDias
val ActivityActaAtencionBinding.etPlazoDias: TextInputEditText get() = layoutPaso3.etPlazoDias
val ActivityActaAtencionBinding.btnPlazoMas: MaterialButton get() = layoutPaso3.btnPlazoMas
val ActivityActaAtencionBinding.chipPlazo3: MaterialButton get() = layoutPaso3.chipPlazo3
val ActivityActaAtencionBinding.chipPlazo5: MaterialButton get() = layoutPaso3.chipPlazo5
val ActivityActaAtencionBinding.chipPlazo8: MaterialButton get() = layoutPaso3.chipPlazo8
val ActivityActaAtencionBinding.chipPlazo15: MaterialButton get() = layoutPaso3.chipPlazo15
val ActivityActaAtencionBinding.chipPlazo30: MaterialButton get() = layoutPaso3.chipPlazo30
val ActivityActaAtencionBinding.cardOpcionRescate: MaterialCardView get() = layoutPaso3.cardOpcionRescate
val ActivityActaAtencionBinding.swRequiereRescate: MaterialSwitch get() = layoutPaso3.swRequiereRescate
val ActivityActaAtencionBinding.tvEstadoRequiereRescate: TextView get() = layoutPaso3.tvEstadoRequiereRescate
val ActivityActaAtencionBinding.layoutModuloRescateIntegrado: LinearLayout get() = layoutPaso3.layoutModuloRescateIntegrado
val ActivityActaAtencionBinding.btnCargarDatosRescate: MaterialButton get() = layoutPaso3.btnCargarDatosRescate
val ActivityActaAtencionBinding.tilMotivoRescateDropdown: TextInputLayout get() = layoutPaso3.tilMotivoRescateDropdown
val ActivityActaAtencionBinding.autoCompleteMotivoRescate: MaterialAutoCompleteTextView get() = layoutPaso3.autoCompleteMotivoRescate
val ActivityActaAtencionBinding.tilPersonaAtiendeRescatista: TextInputLayout get() = layoutPaso3.tilPersonaAtiendeRescatista
val ActivityActaAtencionBinding.etPersonaAtiendeRescatista: TextInputEditText get() = layoutPaso3.etPersonaAtiendeRescatista
val ActivityActaAtencionBinding.tilPersonaAtiendeCedula: TextInputLayout get() = layoutPaso3.tilPersonaAtiendeCedula
val ActivityActaAtencionBinding.etPersonaAtiendeCedula: TextInputEditText get() = layoutPaso3.etPersonaAtiendeCedula
val ActivityActaAtencionBinding.tilPersonaAtiendeTelefono: TextInputLayout get() = layoutPaso3.tilPersonaAtiendeTelefono
val ActivityActaAtencionBinding.etPersonaAtiendeTelefono: TextInputEditText get() = layoutPaso3.etPersonaAtiendeTelefono
val ActivityActaAtencionBinding.tilBarrioRescate: TextInputLayout get() = layoutPaso3.tilBarrioRescate
val ActivityActaAtencionBinding.etBarrioRescate: TextInputEditText get() = layoutPaso3.etBarrioRescate
val ActivityActaAtencionBinding.tilTestigo1Nombre: TextInputLayout get() = layoutPaso3.tilTestigo1Nombre
val ActivityActaAtencionBinding.etTestigo1Nombre: TextInputEditText get() = layoutPaso3.etTestigo1Nombre
val ActivityActaAtencionBinding.tilTestigo1Cedula: TextInputLayout get() = layoutPaso3.tilTestigo1Cedula
val ActivityActaAtencionBinding.etTestigo1Cedula: TextInputEditText get() = layoutPaso3.etTestigo1Cedula
val ActivityActaAtencionBinding.tilTestigo1Telefono: TextInputLayout get() = layoutPaso3.tilTestigo1Telefono
val ActivityActaAtencionBinding.etTestigo1Telefono: TextInputEditText get() = layoutPaso3.etTestigo1Telefono
val ActivityActaAtencionBinding.tilTestigo2Nombre: TextInputLayout get() = layoutPaso3.tilTestigo2Nombre
val ActivityActaAtencionBinding.etTestigo2Nombre: TextInputEditText get() = layoutPaso3.etTestigo2Nombre
val ActivityActaAtencionBinding.tilTestigo2Cedula: TextInputLayout get() = layoutPaso3.tilTestigo2Cedula
val ActivityActaAtencionBinding.etTestigo2Cedula: TextInputEditText get() = layoutPaso3.etTestigo2Cedula
val ActivityActaAtencionBinding.tilTestigo2Telefono: TextInputLayout get() = layoutPaso3.tilTestigo2Telefono
val ActivityActaAtencionBinding.etTestigo2Telefono: TextInputEditText get() = layoutPaso3.etTestigo2Telefono

// 19 Switches Checklist y Estados
val ActivityActaAtencionBinding.swRespiratorioMoco: MaterialSwitch get() = layoutPaso3.swRespiratorioMoco
val ActivityActaAtencionBinding.tvEstadoRespiratorioMoco: TextView get() = layoutPaso3.tvEstadoRespiratorioMoco
val ActivityActaAtencionBinding.swRespiratorioDificultad: MaterialSwitch get() = layoutPaso3.swRespiratorioDificultad
val ActivityActaAtencionBinding.tvEstadoRespiratorioDificultad: TextView get() = layoutPaso3.tvEstadoRespiratorioDificultad
val ActivityActaAtencionBinding.swRespiratorioTos: MaterialSwitch get() = layoutPaso3.swRespiratorioTos
val ActivityActaAtencionBinding.tvEstadoRespiratorioTos: TextView get() = layoutPaso3.tvEstadoRespiratorioTos
val ActivityActaAtencionBinding.swRespiratorioLaganeo: MaterialSwitch get() = layoutPaso3.swRespiratorioLaganeo
val ActivityActaAtencionBinding.tvEstadoRespiratorioLaganeo: TextView get() = layoutPaso3.tvEstadoRespiratorioLaganeo

val ActivityActaAtencionBinding.swDigestivoVomito: MaterialSwitch get() = layoutPaso3.swDigestivoVomito
val ActivityActaAtencionBinding.tvEstadoDigestivoVomito: TextView get() = layoutPaso3.tvEstadoDigestivoVomito
val ActivityActaAtencionBinding.swDigestivoDiarrea: MaterialSwitch get() = layoutPaso3.swDigestivoDiarrea
val ActivityActaAtencionBinding.tvEstadoDigestivoDiarrea: TextView get() = layoutPaso3.tvEstadoDigestivoDiarrea
val ActivityActaAtencionBinding.swDigestivoDesnutrido: MaterialSwitch get() = layoutPaso3.swDigestivoDesnutrido
val ActivityActaAtencionBinding.tvEstadoDigestivoDesnutrido: TextView get() = layoutPaso3.tvEstadoDigestivoDesnutrido
val ActivityActaAtencionBinding.swDigestivoDeshidratado: MaterialSwitch get() = layoutPaso3.swDigestivoDeshidratado
val ActivityActaAtencionBinding.tvEstadoDigestivoDeshidratado: TextView get() = layoutPaso3.tvEstadoDigestivoDeshidratado

val ActivityActaAtencionBinding.swMusculoCaidaPatas: MaterialSwitch get() = layoutPaso3.swMusculoCaidaPatas
val ActivityActaAtencionBinding.tvEstadoMusculoCaidaPatas: TextView get() = layoutPaso3.tvEstadoMusculoCaidaPatas
val ActivityActaAtencionBinding.swMusculoCojo: MaterialSwitch get() = layoutPaso3.swMusculoCojo
val ActivityActaAtencionBinding.tvEstadoMusculoCojo: TextView get() = layoutPaso3.tvEstadoMusculoCojo
val ActivityActaAtencionBinding.swMusculoHeridaAbierta: MaterialSwitch get() = layoutPaso3.swMusculoHeridaAbierta
val ActivityActaAtencionBinding.tvEstadoMusculoHeridaAbierta: TextView get() = layoutPaso3.tvEstadoMusculoHeridaAbierta
val ActivityActaAtencionBinding.swMusculoGusanos: MaterialSwitch get() = layoutPaso3.swMusculoGusanos
val ActivityActaAtencionBinding.tvEstadoMusculoGusanos: TextView get() = layoutPaso3.tvEstadoMusculoGusanos
val ActivityActaAtencionBinding.swMusculoPostrado: MaterialSwitch get() = layoutPaso3.swMusculoPostrado
val ActivityActaAtencionBinding.tvEstadoMusculoPostrado: TextView get() = layoutPaso3.tvEstadoMusculoPostrado

val ActivityActaAtencionBinding.swDermatoPeladuras: MaterialSwitch get() = layoutPaso3.swDermatoPeladuras
val ActivityActaAtencionBinding.tvEstadoDermatoPeladuras: TextView get() = layoutPaso3.tvEstadoDermatoPeladuras
val ActivityActaAtencionBinding.swDermatoHeridasSangre: MaterialSwitch get() = layoutPaso3.swDermatoHeridasSangre
val ActivityActaAtencionBinding.tvEstadoDermatoHeridasSangre: TextView get() = layoutPaso3.tvEstadoDermatoHeridasSangre
val ActivityActaAtencionBinding.swDermatoPulgas: MaterialSwitch get() = layoutPaso3.swDermatoPulgas
val ActivityActaAtencionBinding.tvEstadoDermatoPulgas: TextView get() = layoutPaso3.tvEstadoDermatoPulgas

val ActivityActaAtencionBinding.swGenitoMasaSecrecion: MaterialSwitch get() = layoutPaso3.swGenitoMasaSecrecion
val ActivityActaAtencionBinding.tvEstadoGenitoMasaSecrecion: TextView get() = layoutPaso3.tvEstadoGenitoMasaSecrecion
val ActivityActaAtencionBinding.swGenitoGestante: MaterialSwitch get() = layoutPaso3.swGenitoGestante
val ActivityActaAtencionBinding.tvEstadoGenitoGestante: TextView get() = layoutPaso3.tvEstadoGenitoGestante
val ActivityActaAtencionBinding.swGenitoCelo: MaterialSwitch get() = layoutPaso3.swGenitoCelo
val ActivityActaAtencionBinding.tvEstadoGenitoCelo: TextView get() = layoutPaso3.tvEstadoGenitoCelo

val ActivityActaAtencionBinding.tilObservacionesRescate: TextInputLayout get() = layoutPaso3.tilObservacionesRescate
val ActivityActaAtencionBinding.etObservacionesRescate: TextInputEditText get() = layoutPaso3.etObservacionesRescate
val ActivityActaAtencionBinding.tilVeterinarioReceptor: TextInputLayout get() = layoutPaso3.tilVeterinarioReceptor
val ActivityActaAtencionBinding.etVeterinarioReceptor: TextInputEditText get() = layoutPaso3.etVeterinarioReceptor
val ActivityActaAtencionBinding.tilFechaRecepcion: TextInputLayout get() = layoutPaso3.tilFechaRecepcion
val ActivityActaAtencionBinding.etFechaRecepcion: TextInputEditText get() = layoutPaso3.etFechaRecepcion
val ActivityActaAtencionBinding.tilHoraRecepcion: TextInputLayout get() = layoutPaso3.tilHoraRecepcion
val ActivityActaAtencionBinding.etHoraRecepcion: TextInputEditText get() = layoutPaso3.etHoraRecepcion
val ActivityActaAtencionBinding.tilObservacionesRecepcion: TextInputLayout get() = layoutPaso3.tilObservacionesRecepcion
val ActivityActaAtencionBinding.etObservacionesRecepcion: TextInputEditText get() = layoutPaso3.etObservacionesRecepcion

// Notificación y Funcionarios
val ActivityActaAtencionBinding.tilNotificadoNombre: TextInputLayout get() = layoutPaso3.tilNotificadoNombre
val ActivityActaAtencionBinding.etNotificadoNombre: TextInputEditText get() = layoutPaso3.etNotificadoNombre
val ActivityActaAtencionBinding.tilNotificadoIdentificacion: TextInputLayout get() = layoutPaso3.tilNotificadoIdentificacion
val ActivityActaAtencionBinding.etNotificadoIdentificacion: TextInputEditText get() = layoutPaso3.etNotificadoIdentificacion
val ActivityActaAtencionBinding.tilFuncionarioNombre: TextInputLayout get() = layoutPaso3.tilFuncionarioNombre
val ActivityActaAtencionBinding.etFuncionarioNombre: TextInputEditText get() = layoutPaso3.etFuncionarioNombre
val ActivityActaAtencionBinding.tilFuncionarioCargo: TextInputLayout get() = layoutPaso3.tilFuncionarioCargo
val ActivityActaAtencionBinding.etFuncionarioCargo: TextInputEditText get() = layoutPaso3.etFuncionarioCargo
val ActivityActaAtencionBinding.tilFuncionarioIdentificacion: TextInputLayout get() = layoutPaso3.tilFuncionarioIdentificacion
val ActivityActaAtencionBinding.etFuncionarioIdentificacion: TextInputEditText get() = layoutPaso3.etFuncionarioIdentificacion

// Firmas
val ActivityActaAtencionBinding.tvFirmasSeccionTitulo: TextView get() = layoutPaso3.tvFirmasSeccionTitulo
val ActivityActaAtencionBinding.tvFirma1Titulo: TextView get() = layoutPaso3.tvFirma1Titulo
val ActivityActaAtencionBinding.btnClearSignatureNotificador: MaterialButton get() = layoutPaso3.btnClearSignatureNotificador
val ActivityActaAtencionBinding.signatureNotificador: SignatureView get() = layoutPaso3.signatureNotificador
val ActivityActaAtencionBinding.tvFirma2Titulo: TextView get() = layoutPaso3.tvFirma2Titulo
val ActivityActaAtencionBinding.btnClearSignatureNotificado: MaterialButton get() = layoutPaso3.btnClearSignatureNotificado
val ActivityActaAtencionBinding.signatureNotificado: SignatureView get() = layoutPaso3.signatureNotificado

// Resumen Multimedia Paso 3
val ActivityActaAtencionBinding.cardResumenMultimediaPaso3: MaterialCardView get() = layoutPaso3.cardResumenMultimediaPaso3
val ActivityActaAtencionBinding.tvResumenMediaPaso3: TextView get() = layoutPaso3.tvResumenMediaPaso3
val ActivityActaAtencionBinding.btnVerMediaDesdePaso3: TextView get() = layoutPaso3.btnVerMediaDesdePaso3

// Cierre
val ActivityActaAtencionBinding.tilEstadoCierre: TextInputLayout get() = layoutPaso3.tilEstadoCierre
val ActivityActaAtencionBinding.autoCompleteEstadoCierre: MaterialAutoCompleteTextView get() = layoutPaso3.autoCompleteEstadoCierre
val ActivityActaAtencionBinding.tilObservaciones: TextInputLayout get() = layoutPaso3.tilObservaciones
val ActivityActaAtencionBinding.etObservaciones: TextInputEditText get() = layoutPaso3.etObservaciones
