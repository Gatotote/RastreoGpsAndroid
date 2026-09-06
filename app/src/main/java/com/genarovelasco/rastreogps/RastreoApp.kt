package com.genarovelasco.rastreogps

import android.app.Application
import com.genarovelasco.rastreogps.data.Preferencias
import com.genarovelasco.rastreogps.data.Repositorio
import com.genarovelasco.rastreogps.location.EscanerBle
import org.osmdroid.config.Configuration

class RastreoApp : Application() {
    lateinit var preferencias: Preferencias
        private set
    lateinit var repositorio: Repositorio
        private set
    lateinit var escaner: EscanerBle
        private set

    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        preferencias = Preferencias(this)
        repositorio = Repositorio(preferencias)
        escaner = EscanerBle(this)
    }
}
