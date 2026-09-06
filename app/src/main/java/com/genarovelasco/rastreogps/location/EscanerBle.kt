package com.genarovelasco.rastreogps.location

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.genarovelasco.rastreogps.data.BaliceDetectada
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

/**
 * Escanea balizas iBeacon con el Bluetooth del celular y publica las que se han
 * visto en los últimos ~30 s. No sabe nombres ni habla con el servidor: eso lo
 * hacen el repositorio y el servicio de ubicación.
 */
class EscanerBle(private val context: Context) {

    private val _detectadas = MutableStateFlow<List<BaliceDetectada>>(emptyList())
    val detectadas: StateFlow<List<BaliceDetectada>> = _detectadas.asStateFlow()

    private var scanner: BluetoothLeScanner? = null
    private var escaneando = false
    private val vistas = HashMap<String, BaliceDetectada>()

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val bytes = result.scanRecord?.getManufacturerSpecificData(APPLE) ?: return
            val baliza = parseIBeacon(bytes, result.rssi) ?: return
            synchronized(vistas) {
                vistas[baliza.id] = baliza
                podar()
                _detectadas.value = vistas.values.sortedByDescending { it.rssi }
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }
    }

    @SuppressLint("MissingPermission")
    fun iniciar() {
        if (escaneando || !tienePermiso()) return
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
        val sc = bm.adapter?.bluetoothLeScanner ?: return
        scanner = sc
        val filtro = ScanFilter.Builder()
            .setManufacturerData(APPLE, byteArrayOf(0x02, 0x15))
            .build()
        val ajustes = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
        try {
            sc.startScan(listOf(filtro), ajustes, callback)
            escaneando = true
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    fun detener() {
        if (!escaneando) return
        try {
            scanner?.stopScan(callback)
        } catch (_: Exception) {
        }
        escaneando = false
    }

    private fun podar() {
        val limite = System.currentTimeMillis() - VIGENCIA_MS
        val it = vistas.entries.iterator()
        while (it.hasNext()) {
            if (it.next().value.visto < limite) it.remove()
        }
    }

    private fun tienePermiso(): Boolean {
        val permiso = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, permiso) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val APPLE = 0x004C
        private const val VIGENCIA_MS = 30_000L

        /** Los 23+ bytes de datos de fabricante Apple: 02 15 | UUID(16) | major(2) | minor(2) | txPower(1). */
        fun parseIBeacon(bytes: ByteArray, rssi: Int): BaliceDetectada? {
            if (bytes.size < 23 || bytes[0].toInt() != 0x02 || bytes[1].toInt() != 0x15) return null
            val uuid = buildString {
                for (i in 2..17) {
                    append("%02x".format(bytes[i]))
                    if (length == 8 || length == 13 || length == 18 || length == 23) append('-')
                }
            }
            val major = ((bytes[18].toInt() and 0xFF) shl 8) or (bytes[19].toInt() and 0xFF)
            val minor = ((bytes[20].toInt() and 0xFF) shl 8) or (bytes[21].toInt() and 0xFF)
            val txPower = bytes[22].toInt() // signed
            val id = String.format(Locale.US, "%s-%d-%d", uuid, major, minor)
            val referencia = if (txPower in -100..-30) txPower else -59
            val distancia = if (rssi == 0) null else 10.0.pow((referencia - rssi) / 20.0)
            return BaliceDetectada(id = id, rssi = rssi, distancia = distancia, visto = System.currentTimeMillis())
        }
    }
}
