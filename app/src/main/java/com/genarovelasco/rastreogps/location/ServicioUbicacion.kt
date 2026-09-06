package com.genarovelasco.rastreogps.location

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.genarovelasco.rastreogps.MainActivity
import com.genarovelasco.rastreogps.R
import com.genarovelasco.rastreogps.RastreoApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ServicioUbicacion : Service(), LocationListener {
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var locationManager: LocationManager? = null
    private var ultimaUbicacion: Location? = null
    private val ultimoReporteEtiqueta = HashMap<String, Long>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        crearCanal()
        val notificacion = notificacion()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(ID_NOTIFICACION, notificacion, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(ID_NOTIFICACION, notificacion)
        }
        iniciarGps()
        iniciarBalizas()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        locationManager?.removeUpdates(this)
        (application as RastreoApp).escaner.detener()
        ambito.cancel()
        super.onDestroy()
    }

    override fun onLocationChanged(location: Location) {
        ultimaUbicacion = location
        val app = application as RastreoApp
        ambito.launch {
            app.repositorio.enviarUbicacion(
                lat = location.latitude,
                lng = location.longitude,
                precision = if (location.hasAccuracy()) location.accuracy.toDouble() else null,
            )
        }
    }

    /** Arranca el escáner iBeacon y reporta cada baliza conocida como mucho una vez cada 45 s. */
    private fun iniciarBalizas() {
        val app = application as RastreoApp
        app.escaner.iniciar()
        ambito.launch {
            app.escaner.detectadas.collect { lista ->
                val ubic = ultimaUbicacion ?: return@collect
                val ahora = System.currentTimeMillis()
                lista.forEach { baliza ->
                    val previo = ultimoReporteEtiqueta[baliza.id] ?: 0L
                    if (ahora - previo < REPORTE_ETIQUETA_MS) return@forEach
                    ultimoReporteEtiqueta[baliza.id] = ahora
                    app.repositorio.reportarVistaEtiqueta(
                        baliza = baliza,
                        lat = ubic.latitude,
                        lng = ubic.longitude,
                        precision = if (ubic.hasAccuracy()) ubic.accuracy.toDouble() else null,
                    )
                }
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    private fun iniciarGps() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        locationManager = manager
        val proveedores = buildList {
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
        }
        if (proveedores.isEmpty()) return
        proveedores.forEach { proveedor ->
            manager.getLastKnownLocation(proveedor)?.let { onLocationChanged(it) }
            manager.requestLocationUpdates(proveedor, INTERVALO_MS, DISTANCIA_MINIMA_M, this)
        }
    }

    private fun crearCanal() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CANAL_ID, "Ubicación", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notificacion(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CANAL_ID)
            .setSmallIcon(R.drawable.ic_app)
            .setContentTitle(getString(R.string.notificacion_titulo))
            .setContentText(getString(R.string.notificacion_texto))
            .setContentIntent(pending)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CANAL_ID = "ubicacion"
        private const val ID_NOTIFICACION = 41
        private const val INTERVALO_MS = 10_000L
        private const val DISTANCIA_MINIMA_M = 5f
        private const val REPORTE_ETIQUETA_MS = 45_000L
    }
}
