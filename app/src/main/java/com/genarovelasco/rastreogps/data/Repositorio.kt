package com.genarovelasco.rastreogps.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class Repositorio(
    private val preferencias: Preferencias,
    private val api: RastreoApi = RastreoApi(),
) {
    private val _dispositivos = MutableStateFlow<List<DispositivoDto>>(emptyList())
    val dispositivos: StateFlow<List<DispositivoDto>> = _dispositivos.asStateFlow()

    // El registro corre una sola vez; enviarUbicacion() lo espera antes del PUT,
    // así el servidor nunca recibe una ubicación de un celular sin registrar (404).
    private val registroMutex = Mutex()
    private var registrado = false

    private val _etiquetas = MutableStateFlow<List<EtiquetaDto>>(emptyList())
    val etiquetas: StateFlow<List<EtiquetaDto>> = _etiquetas.asStateFlow()

    private val _errorRed = MutableStateFlow<String?>(null)
    val errorRed: StateFlow<String?> = _errorRed.asStateFlow()

    private val _conectado = MutableStateFlow(false)
    val conectado: StateFlow<Boolean> = _conectado.asStateFlow()

    suspend fun registrarSiHaceFalta() {
        registroMutex.withLock {
            if (registrado) return
            val usuario = preferencias.asegurarUsuario()
            try {
                api.registrar(
                    usuario.servidorUrl,
                    RegistroRequest(id = usuario.id, nombre = usuario.nombre, modelo = usuario.modelo),
                )
                registrado = true
                _conectado.value = true
                _errorRed.value = null
            } catch (e: Exception) {
                _conectado.value = false
                _errorRed.value = e.message ?: "No se pudo registrar este celular"
            }
        }
    }

    private suspend fun forzarReRegistro() {
        registroMutex.withLock { registrado = false }
        registrarSiHaceFalta()
    }

    /** Reintento manual: fuerza un nuevo registro y refresca la lista, sin esperar el sondeo automático. */
    suspend fun reconectar() {
        forzarReRegistro()
        refrescar()
    }

    suspend fun actualizarNombre(nombre: String) {
        preferencias.guardarNombre(nombre)
        forzarReRegistro()
    }

    suspend fun actualizarServidor(url: String) {
        preferencias.guardarServidor(url)
        forzarReRegistro()
        refrescar()
    }

    suspend fun enviarUbicacion(lat: Double, lng: Double, precision: Double?) {
        registrarSiHaceFalta() // bloquea hasta que el registro en curso termine (ok o falle)
        val usuario = preferencias.asegurarUsuario()

        fun guardarLocal() = mezclar(
            DispositivoDto(
                id = usuario.id, nombre = usuario.nombre, modelo = usuario.modelo,
                lat = lat, lng = lng, precision = precision,
                actualizado = System.currentTimeMillis(), enLinea = true,
            ),
        )

        // Si el registro aún no está confirmado, NO se manda el PUT (así el servidor
        // nunca ve una ubicación de un celular sin registrar → nunca hay 404).
        // El siguiente sondeo / la siguiente ubicación reintentará el registro.
        if (!registrado) {
            _conectado.value = false
            guardarLocal()
            return
        }

        val cuerpo = UbicacionRequest(lat = lat, lng = lng, precision = precision)
        try {
            mezclar(api.enviarUbicacion(usuario.servidorUrl, usuario.id, cuerpo))
            _conectado.value = true
            _errorRed.value = null
        } catch (e: Exception) {
            // El servidor perdió el registro (BD reiniciada): re-registra y reintenta una vez.
            if ((e.message ?: "").contains("404")) {
                forzarReRegistro()
                if (registrado) {
                    try {
                        mezclar(api.enviarUbicacion(usuario.servidorUrl, usuario.id, cuerpo))
                        _conectado.value = true
                        _errorRed.value = null
                        return
                    } catch (_: Exception) {
                    }
                }
            }
            _conectado.value = false
            _errorRed.value = e.message ?: "No se pudo enviar la ubicación"
            guardarLocal()
        }
    }

    suspend fun refrescar() {
        val usuario = preferencias.usuario.first()
        if (usuario.id.isBlank()) return
        if (!registrado) registrarSiHaceFalta() // reintenta el registro en cada sondeo hasta que pegue
        try {
            _dispositivos.value = api.listar(usuario.servidorUrl)
            _etiquetas.value = api.listarEtiquetas(usuario.servidorUrl)
            if (registrado) runCatching { api.latido(usuario.servidorUrl, usuario.id) }
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
        if (_etiquetas.value.none { it.id.equals(baliza.id, ignoreCase = true) }) return
        registrarSiHaceFalta()
        val usuario = preferencias.usuario.first()
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
