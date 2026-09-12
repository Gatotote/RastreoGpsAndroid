package com.genarovelasco.rastreogps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genarovelasco.rastreogps.location.ServicioUbicacion
import com.genarovelasco.rastreogps.ui.RastreoPantalla
import com.genarovelasco.rastreogps.ui.theme.RastreoTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.factory(application as RastreoApp)
    }

    private var permisosOk by mutableStateOf(false)

    private val pedirBasicos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { resultado ->
        val fine = resultado[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            tienePermiso(Manifest.permission.ACCESS_FINE_LOCATION)
        if (fine && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pedirFondo.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            actualizarPermisos()
        }
    }

    private val pedirFondo = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        actualizarPermisos()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        actualizarPermisos()
        setContent {
            val estado by viewModel.estado.collectAsStateWithLifecycle()
            RastreoTheme(oscuro = true) {
                RastreoPantalla(
                    estado = estado,
                    permisosOk = permisosOk,
                    onAceptar = viewModel::aceptar,
                    onPedirPermisos = ::solicitarPermisos,
                    onGuardarNombre = viewModel::guardarNombre,
                    onGuardarServidor = viewModel::guardarServidor,
                    onCentrar = viewModel::centrarEn,
                    onEscanearEtiquetas = viewModel::asegurarEscaneo,
                    onRegistrarEtiqueta = viewModel::registrarEtiqueta,
                    onOlvidarEtiqueta = viewModel::olvidarEtiqueta,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        actualizarPermisos()
        if (permisosOk) iniciarServicio()
    }

    private fun solicitarPermisos() {
        val pendientes = buildList {
            if (!tienePermiso(Manifest.permission.ACCESS_FINE_LOCATION)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !tienePermiso(Manifest.permission.BLUETOOTH_SCAN)
            ) {
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !tienePermiso(Manifest.permission.POST_NOTIFICATIONS)
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        when {
            pendientes.isNotEmpty() -> pedirBasicos.launch(pendientes.toTypedArray())
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !tienePermiso(Manifest.permission.ACCESS_BACKGROUND_LOCATION) -> {
                pedirFondo.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
            else -> actualizarPermisos()
        }
    }

    private fun actualizarPermisos() {
        permisosOk = tienePermiso(Manifest.permission.ACCESS_FINE_LOCATION)
        if (permisosOk) iniciarServicio()
    }

    private fun iniciarServicio() {
        val intent = Intent(this, ServicioUbicacion::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun tienePermiso(permiso: String): Boolean =
        ContextCompat.checkSelfPermission(this, permiso) == PackageManager.PERMISSION_GRANTED
}
