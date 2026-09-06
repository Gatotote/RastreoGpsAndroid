# Rastreo GPS

App Android (Kotlin / Jetpack Compose) + servidor propio para ver la ubicación
**solo entre los celulares que tienen esta app**. Cada instalación crea un
usuario nuevo para ese teléfono; si se desinstala y se reinstala, es otro
usuario. Nada pasa por servicios de terceros.

## Cómo funciona

1. Al abrir la app por primera vez, el celular genera un ID, pide permiso de
   ubicación y se registra en el servidor.
2. El GPS se comparte en segundo plano (servicio en primer plano). También manda
   un *latido* periódico para seguir "en línea" aunque no se mueva.
3. El mapa y la lista muestran únicamente esos celulares. Extra: **etiquetas
   Bluetooth (iBeacon)** — cada celular escanea balizas y reporta dónde las vio,
   así puedes seguir mochilas, llaves, etc.

Hace falta un servidor porque los teléfonos no se ven entre sí sin un punto en
común. El servidor es un Flask pequeño con SQLite.

## 1. Servidor

Requisitos: Python 3.11+.

```bash
cd servidor
python -m venv .venv && . .venv/bin/activate     # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python app.py
```

Imprime la URL a usar, p. ej. `http://192.168.1.20:8080`. Esa misma dirección
abre el panel web en el navegador.

Para que arranque solo (Linux, systemd de usuario) hay un ejemplo en
[`servidor/rastreo-gps.service.example`](servidor/rastreo-gps.service.example).

## 2. App

Requisitos de compilación: **JDK 21** (el AGP no soporta JDK 25/26).

```bash
JAVA_HOME=/ruta/al/jdk-21 ./gradlew :app:assembleDebug
# o abre la carpeta en Android Studio y pulsa Run
```

En **Ajustes** de la app, pega la URL del servidor. Concede la ubicación
**"Permitir todo el tiempo"** y, si vas a usar etiquetas, el permiso de
**dispositivos cercanos** (Bluetooth).

## Acceso desde fuera de la red local

El servidor escucha en `0.0.0.0`, así que basta con que el celular llegue a la
IP del servidor. Si el servidor está detrás de CGNAT / doble NAT, lo más simple
es una VPN mesh tipo **Tailscale**: instala Tailscale en la máquina del servidor
y en cada celular (misma cuenta), y usa la IP `100.x` de la tailnet en Ajustes.
No hace falta abrir puertos.

## Notas

- Celular y servidor deben poder verse por IP (misma red, o VPN).
- Pin naranja = tú; el resto = los demás; marcador morado = etiqueta.
- Código de red compartido: `rastreo` (app y servidor lo traen; cámbialo en
  ambos si quieres). No es un mecanismo de seguridad fuerte — pensado para una
  red privada / tailnet.
- El mapa usa teselas de OpenStreetMap con un filtro para el modo oscuro.
