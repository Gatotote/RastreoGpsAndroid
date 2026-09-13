package com.genarovelasco.rastreogps.data

import android.content.Context
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "rastreo")

class Preferencias(private val context: Context) {
    private val claveId = stringPreferencesKey("usuario_id")
    private val claveNombre = stringPreferencesKey("nombre")
    private val claveServidor = stringPreferencesKey("servidor_url")
    private val claveAceptado = booleanPreferencesKey("aceptado")
    private val claveActivo = booleanPreferencesKey("compartiendo_activo")

    /** true en cuanto el usuario pulsa "Acepto" en la primera pantalla. */
    val aceptado: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[claveAceptado] ?: false
    }

    /** false cuando el usuario pulsó "Terminar conexión"; deja de compartir hasta que reanude. */
    val activo: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[claveActivo] ?: true
    }

    suspend fun establecerActivo(valor: Boolean) {
        context.dataStore.edit { it[claveActivo] = valor }
    }

    val usuario: Flow<UsuarioLocal> = context.dataStore.data.map { prefs ->
        UsuarioLocal(
            id = prefs[claveId].orEmpty(),
            nombre = prefs[claveNombre].orEmpty(),
            modelo = modeloDispositivo(),
            servidorUrl = prefs[claveServidor] ?: URL_SERVIDOR_DEFAULT,
        )
    }

    suspend fun asegurarUsuario(): UsuarioLocal {
        val actual = usuario.first()
        if (actual.id.isNotBlank()) {
            val nombre = actual.nombre.ifBlank { modeloDispositivo() }
            if (actual.nombre.isBlank()) {
                guardarNombre(nombre)
            }
            return actual.copy(nombre = nombre, modelo = modeloDispositivo())
        }
        val nuevo = UsuarioLocal(
            id = UUID.randomUUID().toString(),
            nombre = modeloDispositivo(),
            modelo = modeloDispositivo(),
            servidorUrl = actual.servidorUrl.ifBlank { URL_SERVIDOR_DEFAULT },
        )
        context.dataStore.edit { prefs ->
            prefs[claveId] = nuevo.id
            prefs[claveNombre] = nuevo.nombre
            prefs[claveServidor] = nuevo.servidorUrl
        }
        return nuevo
    }

    suspend fun guardarAceptado() {
        context.dataStore.edit { it[claveAceptado] = true }
    }

    suspend fun guardarNombre(nombre: String) {
        context.dataStore.edit { it[claveNombre] = nombre.trim().ifBlank { modeloDispositivo() } }
    }

    suspend fun guardarServidor(url: String) {
        val limpia = url.trim().trimEnd('/')
        context.dataStore.edit { it[claveServidor] = limpia.ifBlank { URL_SERVIDOR_DEFAULT } }
    }

    companion object {
        const val URL_SERVIDOR_DEFAULT = "http://192.168.1.100:8080"
        const val CODIGO_RED = "rastreo"

        fun modeloDispositivo(): String {
            val fabricante = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val modelo = Build.MODEL
            return if (modelo.startsWith(fabricante, ignoreCase = true)) modelo else "$fabricante $modelo"
        }
    }
}
