package com.genarovelasco.rastreogps.ui

import android.os.SystemClock
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.genarovelasco.rastreogps.UiEstado
import com.genarovelasco.rastreogps.data.BaliceDetectada
import com.genarovelasco.rastreogps.data.DispositivoDto
import com.genarovelasco.rastreogps.data.EtiquetaDto
import com.genarovelasco.rastreogps.ui.theme.RastreoGradiente
import com.genarovelasco.rastreogps.ui.theme.RastreoGradienteInk
import java.util.concurrent.TimeUnit
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.launch

private enum class FiltroHoja { Todos, Celulares, Etiquetas }

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
    onReintentarConexion: () -> Unit = {},
    onTerminarConexion: () -> Unit = {},
    onReanudarConexion: () -> Unit = {},
) {
    if (!estado.aceptado) {
        PantallaAcepto(onAceptar)
        return
    }
    if (!permisosOk) {
        PantallaPermisos(onPedirPermisos)
        return
    }

    var mostrarAjustes by rememberSaveable { mutableStateOf(false) }
    var agregandoEtiqueta by rememberSaveable { mutableStateOf(false) }

    when {
        mostrarAjustes -> AjustesPantalla(
            estado = estado,
            onGuardarNombre = onGuardarNombre,
            onGuardarServidor = onGuardarServidor,
            onCerrar = { mostrarAjustes = false },
            onReintentarConexion = onReintentarConexion,
            onTerminarConexion = onTerminarConexion,
            onReanudarConexion = onReanudarConexion,
        )
        agregandoEtiqueta -> AgregarEtiquetaPantalla(
            estado = estado,
            onEscanear = onEscanearEtiquetas,
            onRegistrar = onRegistrarEtiqueta,
            onListo = { agregandoEtiqueta = false },
        )
        else -> MapaConHoja(
            estado = estado,
            onCentrar = onCentrar,
            onAjustes = { mostrarAjustes = true },
            onAgregarEtiqueta = { agregandoEtiqueta = true },
            onOlvidarEtiqueta = onOlvidarEtiqueta,
            onReintentarConexion = onReintentarConexion,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapaConHoja(
    estado: UiEstado,
    onCentrar: (String?) -> Unit,
    onAjustes: () -> Unit,
    onAgregarEtiqueta: () -> Unit,
    onOlvidarEtiqueta: (String) -> Unit,
    onReintentarConexion: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val hoja = rememberBottomSheetScaffoldState()
    var seleccion by remember { mutableStateOf<SeleccionMapa?>(null) }
    var filtro by rememberSaveable { mutableStateOf(FiltroHoja.Todos) }
    var etiquetaAOlvidar by remember { mutableStateOf<EtiquetaDto?>(null) }
    val toqueMarcadorEn = remember { mutableLongStateOf(0L) }

    LaunchedEffect(estado.dispositivos, estado.etiquetas, seleccion) {
        when (val actual = seleccion) {
            is SeleccionMapa.Celular ->
                if (estado.dispositivos.none { it.id == actual.id }) seleccion = null
            is SeleccionMapa.Etiqueta ->
                if (estado.etiquetas.none { it.id == actual.id }) seleccion = null
            null -> Unit
        }
    }

    val yo = estado.dispositivos.firstOrNull { it.id == estado.usuario?.id }
    val navInferior = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun irA(sel: SeleccionMapa) {
        toqueMarcadorEn.longValue = SystemClock.uptimeMillis()
        seleccion = sel
        onCentrar(sel.id)
        scope.launch {
            if (hoja.bottomSheetState.currentValue == SheetValue.Expanded) {
                hoja.bottomSheetState.partialExpand()
            }
        }
    }

    BottomSheetScaffold(
        modifier = Modifier.fillMaxSize(),
        scaffoldState = hoja,
        sheetPeekHeight = 140.dp + navInferior,
        sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetDragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = Color.Transparent,
        sheetContent = {
            HojaContenido(
                estado = estado,
                yo = yo,
                seleccion = seleccion,
                filtro = filtro,
                onFiltro = { filtro = it },
                onElegir = ::irA,
                onAgregarEtiqueta = onAgregarEtiqueta,
                onOlvidar = { etiquetaAOlvidar = it },
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            MapaOsm(
                dispositivos = estado.dispositivos,
                etiquetas = estado.etiquetas,
                miId = estado.usuario?.id,
                centrarId = estado.dispositivoCentradoId ?: estado.usuario?.id,
                centroToken = estado.centroToken,
                seleccion = seleccion,
                onMarcador = ::irA,
                onMapaLibre = {
                    if (SystemClock.uptimeMillis() - toqueMarcadorEn.longValue > 250) {
                        seleccion = null
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            PastillaEstado(
                estado = estado,
                onAjustes = onAjustes,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(12.dp),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = padding.calculateBottomPadding() + 12.dp,
                    )
                    .size(52.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(RastreoGradiente)
                    .clickable {
                        estado.usuario?.id?.let { irA(SeleccionMapa.Celular(it)) }
                            ?: onCentrar(null)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Centrar en mí", tint = RastreoGradienteInk)
            }

            estado.errorRed?.let { error ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            bottom = padding.calculateBottomPadding() + 72.dp,
                        )
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Sin conexión al servidor. Revisa la IP en Ajustes.\n$error",
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        TextButton(onClick = onReintentarConexion) { Text("Reintentar") }
                    }
                }
            }
        }
    }

    etiquetaAOlvidar?.let { etiqueta ->
        AlertDialog(
            onDismissRequest = { etiquetaAOlvidar = null },
            title = { Text("Quitar ${etiqueta.nombre}") },
            text = { Text("Dejará de aparecer en el mapa de esta red. Puedes volver a agregarla después.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onOlvidarEtiqueta(etiqueta.id)
                        if (seleccion is SeleccionMapa.Etiqueta && seleccion?.id == etiqueta.id) {
                            seleccion = null
                        }
                        etiquetaAOlvidar = null
                    },
                ) { Text("Quitar") }
            },
            dismissButton = {
                TextButton(onClick = { etiquetaAOlvidar = null }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun ColumnScope.HojaContenido(
    estado: UiEstado,
    yo: DispositivoDto?,
    seleccion: SeleccionMapa?,
    filtro: FiltroHoja,
    onFiltro: (FiltroHoja) -> Unit,
    onElegir: (SeleccionMapa) -> Unit,
    onAgregarEtiqueta: () -> Unit,
    onOlvidar: (EtiquetaDto) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispositivoElegido = (seleccion as? SeleccionMapa.Celular)?.let { sel ->
        estado.dispositivos.firstOrNull { it.id == sel.id }
    }
    val etiquetaElegida = (seleccion as? SeleccionMapa.Etiqueta)?.let { sel ->
        estado.etiquetas.firstOrNull { it.id == sel.id }
    }

    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            when {
                dispositivoElegido != null -> FichaCelular(
                    dispositivo = dispositivoElegido,
                    esMio = dispositivoElegido.id == estado.usuario?.id,
                    yo = yo,
                )
                etiquetaElegida != null -> FichaEtiqueta(
                    etiqueta = etiquetaElegida,
                    onOlvidar = { onOlvidar(etiquetaElegida) },
                )
                else -> FilaEnLinea(
                    estado = estado,
                    onElegir = onElegir,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(FiltroHoja.entries.toList(), key = { it.name }) { opcion ->
                FilterChip(
                    selected = filtro == opcion,
                    onClick = { onFiltro(opcion) },
                    label = {
                        Text(
                            when (opcion) {
                                FiltroHoja.Todos -> "Todos"
                                FiltroHoja.Celulares -> "Celulares"
                                FiltroHoja.Etiquetas -> "Etiquetas"
                            },
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }

        val celulares = if (filtro != FiltroHoja.Etiquetas) estado.dispositivos else emptyList()
        val etiquetas = if (filtro != FiltroHoja.Celulares) estado.etiquetas else emptyList()

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
        ) {
            if (filtro != FiltroHoja.Etiquetas) {
                item {
                    TextoSeccion("Celulares")
                }
                if (celulares.isEmpty()) {
                    item {
                        TextoVacio("Aún no hay nadie en la red. Instala la app en otro celular o revisa la IP del servidor.")
                    }
                }
                items(celulares, key = { "d-${it.id}" }) { dispositivo ->
                    val sel = seleccion is SeleccionMapa.Celular && seleccion.id == dispositivo.id
                    FilaCelular(
                        dispositivo = dispositivo,
                        esMio = dispositivo.id == estado.usuario?.id,
                        yo = yo,
                        elegido = sel,
                        onClick = { onElegir(SeleccionMapa.Celular(dispositivo.id)) },
                    )
                }
            }
            if (filtro != FiltroHoja.Celulares) {
                item {
                    TextoSeccion("Etiquetas")
                }
                item {
                    BotonGradiente(
                        texto = "Agregar etiqueta",
                        onClick = onAgregarEtiqueta,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (etiquetas.isEmpty()) {
                    item {
                        TextoVacio("Todavía no hay etiquetas. Acerca una y pulsa «Agregar etiqueta».")
                    }
                }
                items(etiquetas, key = { "e-${it.id}" }) { etiqueta ->
                    val sel = seleccion is SeleccionMapa.Etiqueta && seleccion.id == etiqueta.id
                    FilaEtiqueta(
                        etiqueta = etiqueta,
                        elegido = sel,
                        onClick = {
                            if (etiqueta.lat != null && etiqueta.lng != null) {
                                onElegir(SeleccionMapa.Etiqueta(etiqueta.id))
                            }
                        },
                        onOlvidar = { onOlvidar(etiqueta) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilaEnLinea(
    estado: UiEstado,
    onElegir: (SeleccionMapa) -> Unit,
) {
    val vivos = estado.dispositivos.filter { it.enLinea }
    val etiquetas = estado.etiquetas.filter { it.enLinea }
    if (vivos.isEmpty() && etiquetas.isEmpty()) {
        Text(
            "Nadie en línea ahora",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(vivos, key = { it.id }) { dispositivo ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .clickable { onElegir(SeleccionMapa.Celular(dispositivo.id)) },
            ) {
                AvatarIniciales(dispositivo.nombre, mio = dispositivo.id == estado.usuario?.id)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (dispositivo.id == estado.usuario?.id) "Tú" else dispositivo.nombre,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        items(etiquetas, key = { it.id }) { etiqueta ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .clickable {
                        if (etiqueta.lat != null && etiqueta.lng != null) {
                            onElegir(SeleccionMapa.Etiqueta(etiqueta.id))
                        }
                    },
            ) {
                AvatarEtiqueta()
                Spacer(Modifier.height(4.dp))
                Text(
                    etiqueta.nombre,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FichaCelular(
    dispositivo: DispositivoDto,
    esMio: Boolean,
    yo: DispositivoDto?,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        AvatarIniciales(dispositivo.nombre, mio = esMio)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                dispositivo.nombre,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                textoHumanoCelular(dispositivo, esMio, yo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(8.dp))
        ChipEstado(
            texto = if (dispositivo.enLinea) "En línea" else "Sin señal",
            color = if (dispositivo.enLinea) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            vivo = dispositivo.enLinea,
        )
    }
}

@Composable
private fun FichaEtiqueta(
    etiqueta: EtiquetaDto,
    onOlvidar: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        AvatarEtiqueta()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                etiqueta.nombre,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                textoHumanoEtiqueta(etiqueta),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
        TextButton(onClick = onOlvidar) { Text("Quitar") }
    }
}

@Composable
private fun FilaCelular(
    dispositivo: DispositivoDto,
    esMio: Boolean,
    yo: DispositivoDto?,
    elegido: Boolean,
    onClick: () -> Unit,
) {
    val forma = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(forma)
            .background(if (elegido) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
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
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                textoHumanoCelular(dispositivo, esMio, yo),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        ChipEstado(
            texto = if (dispositivo.enLinea) "En línea" else "Sin señal",
            color = if (dispositivo.enLinea) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            vivo = dispositivo.enLinea,
        )
    }
}

@Composable
private fun FilaEtiqueta(
    etiqueta: EtiquetaDto,
    elegido: Boolean,
    onClick: () -> Unit,
    onOlvidar: () -> Unit,
) {
    val forma = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(forma)
            .background(if (elegido) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f) else Color.Transparent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(start = 8.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvatarEtiqueta()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    etiqueta.nombre,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    textoHumanoEtiqueta(etiqueta),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(onClick = onOlvidar) { Text("Quitar") }
    }
}

@Composable
private fun PastillaEstado(
    estado: UiEstado,
    onAjustes: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val n = estado.dispositivos.size
    val titulo = when {
        !estado.compartiendoActivo -> "Detenido"
        !estado.conectado -> "Sin conexión"
        n == 0 -> "En vivo"
        n == 1 -> "En vivo · 1 celular"
        else -> "En vivo · $n celulares"
    }
    val enVivo = estado.conectado && estado.compartiendoActivo
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PuntoVivo(
                color = if (enVivo) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                vivo = enVivo,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                titulo,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onAjustes) {
                Icon(Icons.Default.Settings, contentDescription = "Ajustes")
            }
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
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(RastreoGradiente),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = null, tint = RastreoGradienteInk)
            }
            Spacer(Modifier.height(20.dp))
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
                modifier = Modifier.fillMaxWidth(0.9f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            BloqueInfo(
                "En segundo plano",
                "La app sigue compartiendo para que los tuyos te vean aunque esté cerrada.",
            )
            Spacer(Modifier.height(10.dp))
            BloqueInfo(
                "Solo esta red",
                "Nada pasa por Google ni por un mapa de terceros. El servidor es el tuyo.",
            )
            Spacer(Modifier.height(28.dp))
            BotonGradiente(texto = "Acepto", onClick = onAceptar, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(14.dp))
            Text(
                "Al aceptar permitirás que la app use tu ubicación en segundo plano.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(0.9f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PantallaPermisos(onPedirPermisos: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(32.dp),
        ) {
            Text("Ubicación", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(
                "Para verte y ver a los demás hay que permitir la ubicación todo el tiempo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            BloqueInfo(
                "Todo el tiempo",
                "Así el mapa sigue en vivo cuando la app está en segundo plano.",
            )
            Spacer(Modifier.height(10.dp))
            BloqueInfo(
                "Dispositivos cercanos",
                "Solo si usas etiquetas Bluetooth. El sistema lo pide en el mismo flujo.",
            )
            Spacer(Modifier.height(28.dp))
            BotonGradiente("Permitir ubicación", onPedirPermisos, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BloqueInfo(titulo: String, cuerpo: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(cuerpo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun AjustesPantalla(
    estado: UiEstado,
    onGuardarNombre: (String) -> Unit,
    onGuardarServidor: (String) -> Unit,
    onCerrar: () -> Unit,
    onReintentarConexion: () -> Unit,
    onTerminarConexion: () -> Unit,
    onReanudarConexion: () -> Unit,
) {
    val usuario = estado.usuario
    var nombre by rememberSaveable(usuario?.nombre) { mutableStateOf(usuario?.nombre.orEmpty()) }
    var servidor by rememberSaveable(usuario?.servidorUrl) { mutableStateOf(usuario?.servidorUrl.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCerrar) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text("Ajustes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Cada instalación crea un usuario nuevo para ese celular. Si desinstalas y vuelves a instalar, serás otro.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Este celular", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = nombre,
                        onValueChange = { nombre = it },
                        label = { Text("Nombre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "ID: ${usuario?.idCorto ?: "…"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    BotonGradiente("Guardar nombre", { onGuardarNombre(nombre) }, Modifier.fillMaxWidth())
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Servidor", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = servidor,
                        onValueChange = { servidor = it },
                        label = { Text("URL") },
                        supportingText = { Text("Ejemplo: http://192.168.1.20:8080  (la IP que imprime el servidor en la PC)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    BotonGradiente("Guardar servidor", { onGuardarServidor(servidor) }, Modifier.fillMaxWidth())
                    Text(
                        if (estado.conectado) "Conectado a la red de la app." else "Sin conexión. El servidor debe estar encendido en la misma red Wi‑Fi.",
                        color = if (estado.conectado) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Conexión", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (estado.compartiendoActivo) {
                            "Compartiendo tu ubicación con la red."
                        } else {
                            "Detenido. Este celular no envía ni recibe ubicaciones."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = onReintentarConexion, modifier = Modifier.weight(1f)) {
                            Text("Reintentar conexión")
                        }
                        if (estado.compartiendoActivo) {
                            TextButton(onClick = onTerminarConexion, modifier = Modifier.weight(1f)) {
                                Text("Terminar conexión", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            TextButton(onClick = onReanudarConexion, modifier = Modifier.weight(1f)) {
                                Text("Reanudar")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgregarEtiquetaPantalla(
    estado: UiEstado,
    onEscanear: () -> Unit,
    onRegistrar: (String, String) -> Unit,
    onListo: () -> Unit,
) {
    LaunchedEffect(Unit) { onEscanear() }
    var eligiendo by rememberSaveable { mutableStateOf<String?>(null) }
    val yaRegistradas = estado.etiquetas.map { it.id.lowercase() }.toSet()
    val nuevas = estado.balizasDetectadas.filter { it.id.lowercase() !in yaRegistradas }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onListo) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
            Text("Agregar etiqueta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Balizas iBeacon que este celular ve ahora mismo. Acerca la etiqueta al teléfono.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (nuevas.isEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface) {
                        Text(
                            "Buscando… no se ve ninguna baliza nueva. Revisa que la etiqueta esté encendida y cerca, y que el Bluetooth esté activo.",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(nuevas, key = { it.id }) { baliza ->
                TarjetaBaliza(baliza, onAgregar = { eligiendo = baliza.id })
            }
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
                    label = { Text("Ej. Mochila, llaves del coche…") },
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
private fun TarjetaBaliza(baliza: BaliceDetectada, onAgregar: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(baliza.id, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                "${baliza.rssi} dBm" + (baliza.distancia?.let { " · ~%.0f m".format(it) } ?: ""),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            BotonGradiente("Agregar esta", onAgregar, Modifier.fillMaxWidth())
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
private fun ChipEstado(texto: String, color: Color, vivo: Boolean = false) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PuntoVivo(color, vivo)
        Spacer(Modifier.width(5.dp))
        Text(texto, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PuntoVivo(color: Color, vivo: Boolean) {
    val alpha = if (!vivo) {
        1f
    } else {
        val transicion = rememberInfiniteTransition(label = "vivo")
        val valor by transicion.animateFloat(
            initialValue = 1f,
            targetValue = 0.35f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "a",
        )
        valor
    }
    Box(Modifier.size(8.dp).clip(CircleShape).background(color.copy(alpha = alpha)))
}

@Composable
private fun TextoSeccion(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun TextoVacio(texto: String) {
    Text(
        texto,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

private fun textoHumanoCelular(
    dispositivo: DispositivoDto,
    esMio: Boolean,
    yo: DispositivoDto?,
): String {
    val partes = mutableListOf<String>()
    if (esMio) partes += "este celular"
    if (dispositivo.lat == null || dispositivo.lng == null) {
        partes += "todavía no hay ubicación"
        return partes.joinToString(" · ")
    }
    dispositivo.actualizado?.let { partes += haceCuanto(it) }
    if (!esMio && yo?.lat != null && yo.lng != null) {
        partes += textoDistancia(metrosEntre(yo.lat, yo.lng, dispositivo.lat, dispositivo.lng))
    }
    return partes.joinToString(" · ").ifBlank { "en el mapa" }
}

private fun textoHumanoEtiqueta(etiqueta: EtiquetaDto): String {
    if (etiqueta.lat == null || etiqueta.lng == null) return "Todavía sin detecciones"
    return buildString {
        etiqueta.vistoPorNombre?.let { append("vista por $it") }
        etiqueta.actualizado?.let {
            if (isNotEmpty()) append(" · ")
            append(haceCuanto(it))
        }
        etiqueta.distancia?.let {
            if (isNotEmpty()) append(" · ")
            append("~%.0f m".format(it))
        }
    }.ifBlank { "en el mapa" }
}

private fun metrosEntre(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val radio = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * radio * asin(sqrt(a))
}

private fun textoDistancia(metros: Double): String = when {
    metros < 1 -> "aquí al lado"
    metros < 1000 -> "a %.0f m".format(metros)
    else -> "a %.1f km".format(metros / 1000.0)
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
