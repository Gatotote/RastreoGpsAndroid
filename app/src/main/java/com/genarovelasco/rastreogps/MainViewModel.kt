package com.genarovelasco.rastreogps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.genarovelasco.rastreogps.data.BaliceDetectada
import com.genarovelasco.rastreogps.data.DispositivoDto
import com.genarovelasco.rastreogps.data.EtiquetaDto
import com.genarovelasco.rastreogps.data.Preferencias
import com.genarovelasco.rastreogps.data.Repositorio
import com.genarovelasco.rastreogps.data.UsuarioLocal
import com.genarovelasco.rastreogps.location.EscanerBle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class UiEstado(
    val aceptado: Boolean = false,
    val usuario: UsuarioLocal? = null,
    val dispositivos: List<DispositivoDto> = emptyList(),
    val etiquetas: List<EtiquetaDto> = emptyList(),
    val balizasDetectadas: List<BaliceDetectada> = emptyList(),
    val conectado: Boolean = false,
    val errorRed: String? = null,
    val compartiendoActivo: Boolean = true,
    val dispositivoCentradoId: String? = null,
    val centroToken: Int = 0,
)

class MainViewModel(
    private val preferencias: Preferencias,
    private val repositorio: Repositorio,
    private val escaner: EscanerBle,
) : ViewModel() {
    private val _estado = MutableStateFlow(UiEstado())
    val estado: StateFlow<UiEstado> = _estado.asStateFlow()

    init {
        viewModelScope.launch {
            preferencias.aceptado.collect { ok ->
                _estado.update { it.copy(aceptado = ok) }
            }
        }
        viewModelScope.launch {
            // No se genera identidad ni se habla con el servidor hasta que el usuario acepta.
            preferencias.aceptado.first { it }
            val usuario = preferencias.asegurarUsuario()
            _estado.update { it.copy(usuario = usuario) }
            repositorio.registrarSiHaceFalta()
            while (isActive) {
                if (preferencias.activo.first()) repositorio.refrescar()
                delay(8_000)
            }
        }
        viewModelScope.launch {
            preferencias.activo.collect { activo ->
                _estado.update { it.copy(compartiendoActivo = activo) }
            }
        }
        viewModelScope.launch {
            preferencias.usuario.collect { usuario ->
                _estado.update { it.copy(usuario = usuario) }
            }
        }
        viewModelScope.launch {
            repositorio.dispositivos.collect { lista ->
                _estado.update { it.copy(dispositivos = lista) }
            }
        }
        viewModelScope.launch {
            repositorio.etiquetas.collect { lista ->
                _estado.update { it.copy(etiquetas = lista) }
            }
        }
        viewModelScope.launch {
            escaner.detectadas.collect { lista ->
                _estado.update { it.copy(balizasDetectadas = lista) }
            }
        }
        viewModelScope.launch {
            repositorio.conectado.collect { ok ->
                _estado.update { it.copy(conectado = ok) }
            }
        }
        viewModelScope.launch {
            repositorio.errorRed.collect { error ->
                _estado.update { it.copy(errorRed = error) }
            }
        }
    }

    fun aceptar() {
        viewModelScope.launch { preferencias.guardarAceptado() }
    }

    fun guardarNombre(nombre: String) {
        viewModelScope.launch { repositorio.actualizarNombre(nombre) }
    }

    fun guardarServidor(url: String) {
        viewModelScope.launch { repositorio.actualizarServidor(url) }
    }

    fun centrarEn(id: String?) {
        _estado.update { it.copy(dispositivoCentradoId = id, centroToken = it.centroToken + 1) }
    }

    fun refrescarAhora() {
        viewModelScope.launch { repositorio.refrescar() }
    }

    /** Reintenta la conexión ya, sin esperar el sondeo automático de 8 s. */
    fun reintentarConexion() {
        viewModelScope.launch { repositorio.reconectar() }
    }

    /** Deja de compartir ubicación: apaga el servicio en primer plano hasta que se reanude. */
    fun terminarConexion() {
        viewModelScope.launch { preferencias.establecerActivo(false) }
    }

    fun reanudarConexion() {
        viewModelScope.launch {
            preferencias.establecerActivo(true)
            repositorio.reconectar()
        }
    }

    /** El servicio ya escanea si hay permisos; esto solo asegura el escaneo mientras se ve la pantalla de agregar. */
    fun asegurarEscaneo() {
        escaner.iniciar()
    }

    fun registrarEtiqueta(id: String, nombre: String) {
        viewModelScope.launch { repositorio.registrarEtiqueta(id, nombre) }
    }

    fun olvidarEtiqueta(id: String) {
        viewModelScope.launch { repositorio.olvidarEtiqueta(id) }
    }

    companion object {
        fun factory(app: RastreoApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(app.preferencias, app.repositorio, app.escaner) as T
                }
            }
    }
}
