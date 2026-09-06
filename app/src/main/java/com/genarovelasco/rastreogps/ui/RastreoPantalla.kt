package com.genarovelasco.rastreogps.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.genarovelasco.rastreogps.UiEstado
import com.genarovelasco.rastreogps.data.BaliceDetectada
import com.genarovelasco.rastreogps.data.DispositivoDto
import com.genarovelasco.rastreogps.data.EtiquetaDto
import com.genarovelasco.rastreogps.ui.theme.RastreoGradiente
import com.genarovelasco.rastreogps.ui.theme.RastreoGradienteInk
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RastreoPantalla(
    estado: UiEstado,
    permisosOk: Boolean,
    onAceptar: () -> Unit,
    onPedirPermisos: () -> Unit,
    onGuardarNombre: (String) -> Unit,
    onGuardarServidor: (String) -> Unit,
    onCentrar: (String?) -> Unit,
    onEscanearEtiquetas: () -> Unit = {},
    onRegistrarEtiqueta: (String, String) -> Unit = { _, _ -> },
    onOlvidarEtiqueta: (String) -> Unit = {},
    onIrAMapa: () -> Unit = {},
) {
    if (!estado.aceptado) {
        PantallaAcepto(onAceptar)
        return
    }
    var pestana by rememberSaveable { mutableIntStateOf(0) }
    var agregandoEtiqueta by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                val col = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NavigationBarItem(
                    selected = pestana == 0, onClick = { pestana = 0 }, colors = col,
                    icon = { Icon(Icons.Default.Map, contentDescription = "Mapa") },
                    label = { Text("Mapa") },
                )
                NavigationBarItem(
                    selected = pestana == 1, onClick = { pestana = 1 }, colors = col,
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Celulares") },
                    label = { Text("Celulares") },
                )
                NavigationBarItem(
                    selected = pestana == 2, onClick = { pestana = 2 }, colors = col,
                    icon = { Icon(Icons.Default.Sell, contentDescription = "Etiquetas") },
                    label = { Text("Etiquetas") },
                )
                NavigationBarItem(
                    selected = pestana == 3, onClick = { pestana = 3 }, colors = col,
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
                    label = { Text("Ajustes") },
                )
            }
        },
    ) { padding ->
        if (!permisosOk) {
            PermisosCuerpo(padding, onPedirPermisos)
            return@Scaffold
        }
        when (pestana) {
            0 -> MapaCuerpo(padding, estado, onCentrar)
            1 -> ListaCuerpo(padding, estado) { id ->
                onCentrar(id)
                pestana = 0
                onIrAMapa()
            }
            2 -> if (agregandoEtiqueta) {
                AgregarEtiquetaCuerpo(
                    padding = padding,
                    estado = estado,
                    onEscanear = onEscanearEtiquetas,
                    onRegistrar = onRegistrarEtiqueta,
                    onListo = { agregandoEtiqueta = false },
                )
            } else {
                EtiquetasCuerpo(
                    padding = padding,
                    estado = estado,
                    onAgregar = { agregandoEtiqueta = true },
                    onOlvidar = onOlvidarEtiqueta,
                    onCentrar = { id ->
                        onCentrar(id)
                        pestana = 0
                        onIrAMapa()
                    },
                )
            }
            else -> AjustesCuerpo(padding, estado, onGuardarNombre, onGuardarServidor)
        }
    }
}

@Composable
private fun PantallaAcepto(onAceptar: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        // resplandor suave detrás
        Box(
            modifier = Modifier
                .size(280.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x33FF6B6B), Color(0x11FFB86C), Color(0x00000000)),
                    ),
                    CircleShape,
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Rastreo GPS",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Comparte tu ubicación solo con los celulares que tienen esta app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(0.85f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            BotonGradiente(texto = "Acepto", onClick = onAceptar, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Text(
                "Al aceptar permitirás que la app use tu ubicación en segundo plano.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(0.85f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BotonGradiente(
    texto: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(RastreoGradiente)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = RastreoGradienteInk, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun PermisosCuerpo(padding: PaddingValues, onPedirPermisos: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(padding)
            .padding(24.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Ubicación", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Para verte y ver a los demás celulares con la app, hay que permitir la ubicación todo el tiempo.")
        Spacer(Modifier.height(16.dp))
        Button(onClick = onPedirPermisos) { Text("Permitir ubicación") }
    }
}

@Composable
private fun MapaCuerpo(
    padding: PaddingValues,
    estado: UiEstado,
    onCentrar: (String?) -> Unit,
) {
    // Mapa a pantalla completa: solo respeta el margen inferior de la barra de pestañas.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = padding.calculateBottomPadding()),
    ) {
        MapaOsm(
            dispositivos = estado.dispositivos,
            etiquetas = estado.etiquetas,
            miId = estado.usuario?.id,
            centrarId = estado.dispositivoCentradoId ?: estado.usuario?.id,
            centroToken = estado.centroToken,
            oscuro = androidx.compose.foundation.isSystemInDarkTheme(),
            modifier = Modifier.fillMaxSize(),
        )

        // Barra flotante translúcida
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(12.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
            tonalElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(RastreoGradiente),
                )
                Spacer(Modifier.width(10.dp))
                Text("Rastreo GPS", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                ChipEstado(
                    texto = if (estado.conectado) "En vivo" else "Sin conexión",
                    color = if (estado.conectado) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // FAB con degradado
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(RastreoGradiente)
                .clickable { onCentrar(estado.usuario?.id) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.MyLocation, contentDescription = "Centrar en mí", tint = RastreoGradienteInk)
        }

        estado.errorRed?.let { error ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 84.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(
                    "Sin conexión al servidor. Revisa la IP en Ajustes.\n$error",
                    modifier = Modifier.padding(12.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun ListaCuerpo(
    padding: PaddingValues,
    estado: UiEstado,
    onElegir: (String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Celulares", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Solo aparecen celulares con esta app instalada.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (estado.dispositivos.isEmpty()) {
            item {
                Text(
                    "Aún no hay nadie en la red. Instala la app en otro celular o revisa la IP del servidor.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(estado.dispositivos, key = { it.id }) { dispositivo ->
            TarjetaDispositivo(
                dispositivo = dispositivo,
                esMio = dispositivo.id == estado.usuario?.id,
                onClick = { onElegir(dispositivo.id) },
            )
        }
    }
}

@Composable
private fun TarjetaDispositivo(
    dispositivo: DispositivoDto,
    esMio: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = if (esMio) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else null,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarIniciales(dispositivo.nombre, mio = esMio)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    dispositivo.nombre,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    buildString {
                        if (esMio) append("este celular · ")
                        append(dispositivo.id.take(8).uppercase())
                        if (dispositivo.modelo.isNotBlank()) append(" · ${dispositivo.modelo}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    textoUbicacion(dispositivo),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            ChipEstado(
                texto = if (dispositivo.enLinea) "En línea" else "Sin señal",
                color = if (dispositivo.enLinea) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AvatarIniciales(nombre: String, mio: Boolean) {
    val letra = nombre.trim().firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .then(
                if (mio) Modifier.background(RastreoGradiente)
                else Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(13.dp)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letra,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (mio) RastreoGradienteInk else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AvatarEtiqueta() {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f))
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Sell,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ChipEstado(texto: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(texto, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EtiquetasCuerpo(
    padding: PaddingValues,
    estado: UiEstado,
    onAgregar: () -> Unit,
    onOlvidar: (String) -> Unit,
    onCentrar: (String?) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Etiquetas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Etiquetas Bluetooth (iBeacon). Su posición es la del último celular que las detectó.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            BotonGradiente("Agregar etiqueta", onAgregar, Modifier.fillMaxWidth())
        }
        if (estado.etiquetas.isEmpty()) {
            item {
                Text(
                    "Todavía no hay etiquetas. Acerca una y pulsa «Agregar etiqueta».",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(estado.etiquetas, key = { it.id }) { etiqueta ->
            TarjetaEtiqueta(
                etiqueta = etiqueta,
                onClick = { if (etiqueta.lat != null && etiqueta.lng != null) onCentrar(etiqueta.id) },
                onOlvidar = { onOlvidar(etiqueta.id) },
            )
        }
    }
}

@Composable
private fun TarjetaEtiqueta(
    etiqueta: EtiquetaDto,
    onClick: () -> Unit,
    onOlvidar: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarEtiqueta()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        etiqueta.nombre,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(
                        textoEtiqueta(etiqueta),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                ChipEstado(
                    texto = if (etiqueta.enLinea) "En rango" else "Fuera",
                    color = if (etiqueta.enLinea) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(
                onClick = onOlvidar,
                modifier = Modifier.align(Alignment.End),
            ) { Text("Quitar") }
        }
    }
}

@Composable
private fun AgregarEtiquetaCuerpo(
    padding: PaddingValues,
    estado: UiEstado,
    onEscanear: () -> Unit,
    onRegistrar: (String, String) -> Unit,
    onListo: () -> Unit,
) {
    LaunchedEffect(Unit) { onEscanear() }
    var eligiendo by rememberSaveable { mutableStateOf<String?>(null) }
    val yaRegistradas = estado.etiquetas.map { it.id.lowercase() }.toSet()
    val nuevas = estado.balizasDetectadas.filter { it.id.lowercase() !in yaRegistradas }

    LazyColumn(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Balizas iBeacon que este celular ve ahora mismo. Acerca la etiqueta al teléfono.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (nuevas.isEmpty()) {
            item { Text("Buscando… no se ve ninguna baliza nueva. Revisa que la etiqueta esté encendida y cerca, y que el Bluetooth esté activo.") }
        }
        items(nuevas, key = { it.id }) { baliza ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(baliza.id, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "${baliza.rssi} dBm" + (baliza.distancia?.let { " · ~%.0f m".format(it) } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { eligiendo = baliza.id }) { Text("Agregar esta") }
                }
            }
        }
        item {
            OutlinedButton(onClick = onListo, modifier = Modifier.fillMaxWidth()) { Text("Listo") }
        }
    }

    eligiendo?.let { id ->
        var nombre by rememberSaveable(id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { eligiendo = null },
            title = { Text("Nombre de la etiqueta") },
            text = {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Ej. Mochila de Yuhe") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = nombre.isNotBlank(),
                    onClick = {
                        onRegistrar(id, nombre.trim())
                        eligiendo = null
                    },
                ) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { eligiendo = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun AjustesCuerpo(
    padding: PaddingValues,
    estado: UiEstado,
    onGuardarNombre: (String) -> Unit,
    onGuardarServidor: (String) -> Unit,
) {
    val usuario = estado.usuario
    var nombre by rememberSaveable(usuario?.nombre) { mutableStateOf(usuario?.nombre.orEmpty()) }
    var servidor by rememberSaveable(usuario?.servidorUrl) { mutableStateOf(usuario?.servidorUrl.orEmpty()) }
    Column(
        modifier = Modifier
            .padding(padding)
            .padding(16.dp)
            .fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Cada instalación crea un usuario nuevo para ese celular. Si desinstalas y vuelves a instalar, serás otro.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = nombre,
            onValueChange = { nombre = it },
            label = { Text("Nombre de este celular") },
            modifier = Modifier.fillMaxWidth(),
        )
        BotonGradiente("Guardar nombre", { onGuardarNombre(nombre) })
        Text(
            "ID de este celular: ${usuario?.idCorto ?: "…"}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = servidor,
            onValueChange = { servidor = it },
            label = { Text("URL del servidor") },
            supportingText = { Text("Ejemplo: http://192.168.1.20:8080  (la IP que imprime el servidor en la PC)") },
            modifier = Modifier.fillMaxWidth(),
        )
        BotonGradiente("Guardar servidor", { onGuardarServidor(servidor) })
        Text(
            if (estado.conectado) "Conectado a la red de la app." else "Sin conexión. El servidor debe estar encendido en la misma red Wi‑Fi.",
            color = if (estado.conectado) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
        )
    }
}

private fun textoUbicacion(dispositivo: DispositivoDto): String {
    if (dispositivo.lat == null || dispositivo.lng == null) return "Todavía no hay ubicación"
    val hace = dispositivo.actualizado?.let { haceCuanto(it) } ?: "sin fecha"
    return "%.5f, %.5f · %s".format(dispositivo.lat, dispositivo.lng, hace)
}

private fun textoEtiqueta(etiqueta: EtiquetaDto): String {
    if (etiqueta.lat == null || etiqueta.lng == null) return "Todavía sin detecciones"
    return buildString {
        etiqueta.vistoPorNombre?.let { append("vista por $it") }
        etiqueta.actualizado?.let {
            if (isNotEmpty()) append(" · ")
            append(haceCuanto(it))
        }
        etiqueta.distancia?.let { append(" · ~%.0f m".format(it)) }
    }.ifBlank { "%.5f, %.5f".format(etiqueta.lat, etiqueta.lng) }
}

private fun haceCuanto(millis: Long): String {
    val delta = (System.currentTimeMillis() - millis).coerceAtLeast(0)
    val minutos = TimeUnit.MILLISECONDS.toMinutes(delta)
    val horas = TimeUnit.MILLISECONDS.toHours(delta)
    return when {
        minutos < 1 -> "hace un momento"
        minutos < 60 -> "hace ${minutos} min"
        horas < 24 -> "hace ${horas} h"
        else -> "hace ${TimeUnit.HOURS.toDays(horas)} d"
    }
}
