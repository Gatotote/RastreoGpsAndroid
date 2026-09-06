package com.genarovelasco.rastreogps.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class Repositorio(
    private val preferencias: Preferencias,
    private val api: RastreoApi = RastreoApi(),
) {
    private val _dispositivos = MutableStateFlow<List<DispositivoDto>>(emptyList())
    val dispositivos: StateFlow<List<DispositivoDto>> = _dispositivos.asStateFlow()

    private val _etiquetas = MutableStateFlow<List<EtiquetaDto>>(emptyList())
    val etiquetas: StateFlow<List<EtiquetaDto>> = _etiquetas.asStateFlow()

    private val _errorRed = MutableStateFlow<String?>(null)
    val errorRed: StateFlow<String?> = _errorRed.asStateFlow()

    private val _conectado = MutableStateFlow(false)
    val conectado: StateFlow<Boolean> = _conectado.asStateFlow()

    suspend fun registrarSiHaceFalta() {
        val usuario = preferencias.asegurarUsuario()
        try {
            api.registrar(
                usuario.servidorUrl,
                RegistroRequest(id = usuario.id, nombre = usuario.nombre, modelo = usuario.modelo),
            )
            _conectado.value = true
            _errorRed.value = null
        } catch (e: Exception) {
            _conectado.value = false
            _errorRed.value = e.message ?: "No se pudo registrar este celular"
        }
    }

    suspend fun actualizarNombre(nombre: String) {
        preferencias.guardarNombre(nombre)
        registrarSiHaceFalta()
    }

    suspend fun actualizarServidor(url: String) {
        preferencias.guardarServidor(url)
        registrarSiHaceFalta()
        refrescar()
    }

    suspend fun enviarUbicacion(lat: Double, lng: Double, precision: Double?) {
        val usuario = preferencias.asegurarUsuario()
        try {
            val propio = api.enviarUbicacion(
                usuario.servidorUrl,
                usuario.id,
                UbicacionRequest(lat = lat, lng = lng, precision = precision),
            )
            _conectado.value = true
            _errorRed.value = null
            mezclar(propio)
        } catch (e: Exception) {
            _conectado.value = false
            _errorRed.value = e.message ?: "No se pudo enviar la ubicación"
            mezclar(
                DispositivoDto(
                    id = usuario.id,
                    nombre = usuario.nombre,
                    modelo = usuario.modelo,
                    lat = lat,
                    lng = lng,
                    precision = precision,
                    actualizado = System.currentTimeMillis(),
                    enLinea = true,
                ),
            )
        }
    }

    suspend fun refrescar() {
        val usuario = preferencias.usuario.first()
        if (usuario.id.isBlank()) return
        try {
            _dispositivos.value = api.listar(usuario.servidorUrl)
            _etiquetas.value = api.listarEtiquetas(usuario.servidorUrl)
            _conectado.value = true
            _errorRed.value = null
        } catch (e: Exception) {
            _conectado.value = false
            _errorRed.value = e.message ?: "No se pudo leer la red"
        }
    }

    suspend fun registrarEtiqueta(id: String, nombre: String) {
        val usuario = preferencias.usuario.first()
        try {
            api.registrarEtiqueta(usuario.servidorUrl, RegistroEtiquetaRequest(id = id.lowercase(), nombre = nombre))
            _etiquetas.value = api.listarEtiquetas(usuario.servidorUrl)
            _errorRed.value = null
        } catch (e: Exception) {
            _errorRed.value = e.message ?: "No se pudo guardar la etiqueta"
        }
    }

    suspend fun olvidarEtiqueta(id: String) {
        val usuario = preferencias.usuario.first()
        try {
            api.borrarEtiqueta(usuario.servidorUrl, id)
            _etiquetas.value = _etiquetas.value.filterNot { it.id.equals(id, ignoreCase = true) }
        } catch (_: Exception) {
        }
    }

    /** Un celular reporta que vio una baliza; su posición pasa a ser la de este celular. */
    suspend fun reportarVistaEtiqueta(baliza: BaliceDetectada, lat: Double, lng: Double, precision: Double?) {
        val usuario = preferencias.usuario.first()
        if (_etiquetas.value.none { it.id.equals(baliza.id, ignoreCase = true) }) return
        try {
            api.reportarVista(
                usuario.servidorUrl,
                baliza.id,
                VistaEtiquetaRequest(
                    lat = lat,
                    lng = lng,
                    precision = precision,
                    rssi = baliza.rssi,
                    distancia = baliza.distancia,
                    porId = usuario.id,
                    porNombre = usuario.nombre,
                ),
            )
        } catch (_: Exception) {
        }
    }

    private fun mezclar(dispositivo: DispositivoDto) {
        val resto = _dispositivos.value.filterNot { it.id == dispositivo.id }
        _dispositivos.value = (resto + dispositivo).sortedBy { it.nombre.lowercase() }
    }
}
