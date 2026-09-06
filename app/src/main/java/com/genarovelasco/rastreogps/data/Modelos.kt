package com.genarovelasco.rastreogps.data

data class UsuarioLocal(
    val id: String,
    val nombre: String,
    val modelo: String,
    val servidorUrl: String,
) {
    val idCorto: String get() = id.take(8).uppercase()
}

@kotlinx.serialization.Serializable
data class DispositivoDto(
    val id: String,
    val nombre: String,
    val modelo: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val precision: Double? = null,
    val actualizado: Long? = null,
    val enLinea: Boolean = false,
)

@kotlinx.serialization.Serializable
data class ListaDispositivosDto(
    val dispositivos: List<DispositivoDto> = emptyList(),
)

@kotlinx.serialization.Serializable
data class RegistroRequest(
    val id: String,
    val nombre: String,
    val modelo: String,
)

@kotlinx.serialization.Serializable
data class UbicacionRequest(
    val lat: Double,
    val lng: Double,
    val precision: Double? = null,
)

// ---------------------------------------------------------------------------
// Etiquetas Bluetooth (iBeacon)
// ---------------------------------------------------------------------------

@kotlinx.serialization.Serializable
data class EtiquetaDto(
    val id: String,
    val nombre: String,
    val tipo: String = "ibeacon",
    val lat: Double? = null,
    val lng: Double? = null,
    val precision: Double? = null,
    val rssi: Int? = null,
    val distancia: Double? = null,
    val vistoPor: String? = null,
    val vistoPorNombre: String? = null,
    val actualizado: Long? = null,
    val enLinea: Boolean = false,
)

@kotlinx.serialization.Serializable
data class ListaEtiquetasDto(
    val etiquetas: List<EtiquetaDto> = emptyList(),
)

@kotlinx.serialization.Serializable
data class RegistroEtiquetaRequest(
    val id: String,
    val nombre: String,
    val tipo: String = "ibeacon",
)

@kotlinx.serialization.Serializable
data class VistaEtiquetaRequest(
    val lat: Double,
    val lng: Double,
    val precision: Double? = null,
    val rssi: Int? = null,
    val distancia: Double? = null,
    val porId: String,
    val porNombre: String,
)

/** Una baliza iBeacon detectada por el Bluetooth de este celular (aún sin nombre ni servidor). */
data class BaliceDetectada(
    val id: String,          // "uuid-major-minor" en minúsculas
    val rssi: Int,
    val distancia: Double?,  // metros estimados
    val visto: Long,         // epoch millis de la última detección
)
