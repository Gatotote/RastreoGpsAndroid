package com.genarovelasco.rastreogps.ui

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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// Teselas oscuras de CARTO (uso ligero; requiere atribución OSM + CARTO).
private val CartoOscuro = XYTileSource(
    "CartoDarkAll", 3, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
    ),
    "© OpenStreetMap, © CARTO",
)

@Composable
fun MapaOsm(
    dispositivos: List<DispositivoDto>,
    etiquetas: List<EtiquetaDto>,
    miId: String?,
    centrarId: String?,
    centroToken: Int,
    oscuro: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val control = remember { ControlMapa() }
    val mapa = remember {
        MapView(context).apply {
            setTileSource(if (oscuro) CartoOscuro else TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 4.0
            maxZoomLevel = 20.0
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(19.4326, -99.1332))
        }
    }

    LaunchedEffect(oscuro) {
        mapa.setTileSource(if (oscuro) CartoOscuro else TileSourceFactory.MAPNIK)
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
                val marcador = Marker(view).apply {
                    position = punto
                    title = if (dispositivo.id == miId) "${dispositivo.nombre} (tú)" else dispositivo.nombre
                    snippet = if (dispositivo.enLinea) "En línea" else "Sin señal"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    val icono = if (dispositivo.id == miId) R.drawable.ic_marker_yo else R.drawable.ic_marker_otro
                    val drawable = ContextCompat.getDrawable(context, icono)
                    if (drawable != null) {
                        icon = BitmapDrawable(context.resources, drawable.toBitmap(96, 96))
                    }
                }
                view.overlays.add(marcador)
            }
            etiquetas.filter { it.lat != null && it.lng != null }.forEach { etiqueta ->
                val marcador = Marker(view).apply {
                    position = GeoPoint(etiqueta.lat!!, etiqueta.lng!!)
                    title = etiqueta.nombre
                    snippet = etiqueta.vistoPorNombre?.let { "Vista por $it" } ?: "Sin detecciones"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    val drawable = ContextCompat.getDrawable(context, R.drawable.ic_marker_etiqueta)
                    if (drawable != null) {
                        icon = BitmapDrawable(context.resources, drawable.toBitmap(88, 88))
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
                val objetivo = conUbicacion.firstOrNull { it.id == centrarId } ?: propio
                if (objetivo?.lat != null && objetivo.lng != null) {
                    view.controller.animateTo(GeoPoint(objetivo.lat, objetivo.lng))
                    control.ultimoToken = centroToken
                }
            }
            view.invalidate()
        },
    )
}

private class ControlMapa {
    var inicial: Boolean = false
    var ultimoToken: Int = 0
}
