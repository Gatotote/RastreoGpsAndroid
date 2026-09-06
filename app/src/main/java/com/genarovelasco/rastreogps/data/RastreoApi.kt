package com.genarovelasco.rastreogps.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class RastreoApi {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mediaJson = "application/json; charset=utf-8".toMediaType()
    private val cliente = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun registrar(baseUrl: String, body: RegistroRequest): DispositivoDto =
        post(baseUrl, "/api/dispositivos", json.encodeToString(RegistroRequest.serializer(), body))

    suspend fun enviarUbicacion(baseUrl: String, id: String, body: UbicacionRequest): DispositivoDto =
        put(baseUrl, "/api/dispositivos/$id/ubicacion", json.encodeToString(UbicacionRequest.serializer(), body))

    suspend fun listar(baseUrl: String): List<DispositivoDto> = withContext(Dispatchers.IO) {
        val respuesta = ejecutar(get(baseUrl, "/api/dispositivos"))
        json.decodeFromString(ListaDispositivosDto.serializer(), respuesta).dispositivos
    }

    suspend fun listarEtiquetas(baseUrl: String): List<EtiquetaDto> = withContext(Dispatchers.IO) {
        val respuesta = ejecutar(get(baseUrl, "/api/etiquetas"))
        json.decodeFromString(ListaEtiquetasDto.serializer(), respuesta).etiquetas
    }

    /** Latido: avisa al servidor que este celular sigue conectado aunque no se mueva. */
    suspend fun latido(baseUrl: String, id: String) {
        withContext(Dispatchers.IO) {
            ejecutar(
                Request.Builder()
                    .url(url(baseUrl, "/api/dispositivos/$id/latido"))
                    .header("X-Codigo-Red", Preferencias.CODIGO_RED)
                    .post("".toRequestBody(mediaJson))
                    .build(),
            )
        }
    }

    suspend fun registrarEtiqueta(baseUrl: String, body: RegistroEtiquetaRequest): EtiquetaDto =
        withContext(Dispatchers.IO) {
            val respuesta = ejecutar(
                Request.Builder()
                    .url(url(baseUrl, "/api/etiquetas"))
                    .header("X-Codigo-Red", Preferencias.CODIGO_RED)
                    .post(json.encodeToString(RegistroEtiquetaRequest.serializer(), body).toRequestBody(mediaJson))
                    .build(),
            )
            json.decodeFromString(EtiquetaDto.serializer(), respuesta)
        }

    suspend fun reportarVista(baseUrl: String, id: String, body: VistaEtiquetaRequest): EtiquetaDto =
        withContext(Dispatchers.IO) {
            val respuesta = ejecutar(
                Request.Builder()
                    .url(url(baseUrl, "/api/etiquetas/$id/vista"))
                    .header("X-Codigo-Red", Preferencias.CODIGO_RED)
                    .put(json.encodeToString(VistaEtiquetaRequest.serializer(), body).toRequestBody(mediaJson))
                    .build(),
            )
            json.decodeFromString(EtiquetaDto.serializer(), respuesta)
        }

    suspend fun borrarEtiqueta(baseUrl: String, id: String) {
        withContext(Dispatchers.IO) {
            ejecutar(
                Request.Builder()
                    .url(url(baseUrl, "/api/etiquetas/$id"))
                    .header("X-Codigo-Red", Preferencias.CODIGO_RED)
                    .delete()
                    .build(),
            )
        }
    }

    private suspend fun post(baseUrl: String, path: String, cuerpo: String): DispositivoDto =
        withContext(Dispatchers.IO) {
            val respuesta = ejecutar(
                Request.Builder()
                    .url(url(baseUrl, path))
                    .header("X-Codigo-Red", Preferencias.CODIGO_RED)
                    .post(cuerpo.toRequestBody(mediaJson))
                    .build(),
            )
            json.decodeFromString(DispositivoDto.serializer(), respuesta)
        }

    private suspend fun put(baseUrl: String, path: String, cuerpo: String): DispositivoDto =
        withContext(Dispatchers.IO) {
            val respuesta = ejecutar(
                Request.Builder()
                    .url(url(baseUrl, path))
                    .header("X-Codigo-Red", Preferencias.CODIGO_RED)
                    .put(cuerpo.toRequestBody(mediaJson))
                    .build(),
            )
            json.decodeFromString(DispositivoDto.serializer(), respuesta)
        }

    private fun get(baseUrl: String, path: String): Request =
        Request.Builder()
            .url(url(baseUrl, path))
            .header("X-Codigo-Red", Preferencias.CODIGO_RED)
            .get()
            .build()

    private fun ejecutar(request: Request): String {
        cliente.newCall(request).execute().use { respuesta ->
            val texto = respuesta.body?.string().orEmpty()
            if (!respuesta.isSuccessful) {
                throw IllegalStateException("Servidor ${respuesta.code}: ${texto.ifBlank { respuesta.message }}")
            }
            return texto
        }
    }

    private fun url(baseUrl: String, path: String): String = baseUrl.trimEnd('/') + path
}
