package com.genarovelasco.rastreogps.ui

import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.genarovelasco.rastreogps.R
import com.genarovelasco.rastreogps.data.DispositivoDto
import com.genarovelasco.rastreogps.data.EtiquetaDto
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker

sealed interface SeleccionMapa {
    val id: String
    data class Celular(override val id: String) : SeleccionMapa
    data class Etiqueta(override val id: String) : SeleccionMapa
}

// Modo oscuro sin depender de teselas con API key: se filtran las de OSM.
private val FiltroOscuro = ColorMatrixColorFilter(
    floatArrayOf(
        -0.66f, -0.30f, -0.036f, 0f, 245f,
        -0.30f, -0.66f, -0.036f, 0f, 245f,
        -0.30f, -0.30f, -0.66f, 0f, 245f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

private class MapaCallbacks {
    var onMarcador: (SeleccionMapa) -> Unit = {}
    var onMapaLibre: () -> Unit = {}
}

@Composable
fun MapaOsm(
    dispositivos: List<DispositivoDto>,
    etiquetas: List<EtiquetaDto>,
    miId: String?,
    centrarId: String?,
    centroToken: Int,
    seleccion: SeleccionMapa? = null,
    onMarcador: (SeleccionMapa) -> Unit = {},
    onMapaLibre: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val control = remember { ControlMapa() }
    val callbacks = remember { MapaCallbacks() }
    callbacks.onMarcador = onMarcador
    callbacks.onMapaLibre = onMapaLibre

    val mapa = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 4.0
            maxZoomLevel = 20.0
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(19.4326, -99.1332))
            overlays.add(
                MapEventsOverlay(
                    object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            callbacks.onMapaLibre()
                            return false
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean = false
                    },
                ),
            )
        }
    }

    LaunchedEffect(Unit) {
        mapa.setBackgroundColor(0xFF1B1B21.toInt())
        mapa.overlayManager.tilesOverlay.apply {
            setColorFilter(FiltroOscuro)
            loadingBackgroundColor = Color.TRANSPARENT
            loadingLineColor = Color.TRANSPARENT
        }
        mapa.invalidate()
    }

    DisposableEffect(mapa) {
        mapa.onResume()
        onDispose { mapa.onPause() }
    }

    AndroidView(
        modifier = modifier,
        factory = { mapa },
        update = { view ->
            view.overlays.removeAll { it is Marker }
            val conUbicacion = dispositivos.filter { it.lat != null && it.lng != null }
            conUbicacion.forEach { dispositivo ->
                val punto = GeoPoint(dispositivo.lat!!, dispositivo.lng!!)
                val elegido = seleccion is SeleccionMapa.Celular && seleccion.id == dispositivo.id
                val marcador = Marker(view).apply {
                    position = punto
                    title = if (dispositivo.id == miId) "${dispositivo.nombre} (tú)" else dispositivo.nombre
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    infoWindow = null
                    val icono = if (dispositivo.id == miId) R.drawable.ic_marker_yo else R.drawable.ic_marker_otro
                    val tam = if (elegido) 112 else 96
                    val drawable = ContextCompat.getDrawable(context, icono)
                    if (drawable != null) {
                        icon = BitmapDrawable(context.resources, drawable.toBitmap(tam, tam))
                    }
                    setOnMarkerClickListener { _, _ ->
                        callbacks.onMarcador(SeleccionMapa.Celular(dispositivo.id))
                        true
                    }
                }
                view.overlays.add(marcador)
            }
            etiquetas.filter { it.lat != null && it.lng != null }.forEach { etiqueta ->
                val elegido = seleccion is SeleccionMapa.Etiqueta && seleccion.id == etiqueta.id
                val marcador = Marker(view).apply {
                    position = GeoPoint(etiqueta.lat!!, etiqueta.lng!!)
                    title = etiqueta.nombre
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    infoWindow = null
                    val tam = if (elegido) 104 else 88
                    val drawable = ContextCompat.getDrawable(context, R.drawable.ic_marker_etiqueta)
                    if (drawable != null) {
                        icon = BitmapDrawable(context.resources, drawable.toBitmap(tam, tam))
                    }
                    setOnMarkerClickListener { _, _ ->
                        callbacks.onMarcador(SeleccionMapa.Etiqueta(etiqueta.id))
                        true
                    }
                }
                view.overlays.add(marcador)
            }
            val propio = conUbicacion.firstOrNull { it.id == miId }
            if (!control.inicial && propio?.lat != null && propio.lng != null) {
                view.controller.setZoom(16.0)
                view.controller.setCenter(GeoPoint(propio.lat, propio.lng))
                control.inicial = true
            }
            if (centroToken != control.ultimoToken) {
                val punto = puntoDe(centrarId, conUbicacion, etiquetas) ?: propio?.let {
                    if (it.lat != null && it.lng != null) GeoPoint(it.lat, it.lng) else null
                }
                if (punto != null) {
                    view.controller.animateTo(punto)
                    control.ultimoToken = centroToken
                }
            }
            view.invalidate()
        },
    )
}

private fun puntoDe(
    id: String?,
    dispositivos: List<DispositivoDto>,
    etiquetas: List<EtiquetaDto>,
): GeoPoint? {
    if (id == null) return null
    dispositivos.firstOrNull { it.id == id && it.lat != null && it.lng != null }?.let {
        return GeoPoint(it.lat!!, it.lng!!)
    }
    etiquetas.firstOrNull { it.id == id && it.lat != null && it.lng != null }?.let {
        return GeoPoint(it.lat!!, it.lng!!)
    }
    return null
}

private class ControlMapa {
    var inicial: Boolean = false
    var ultimoToken: Int = 0
}
