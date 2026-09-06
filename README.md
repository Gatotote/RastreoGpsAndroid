# Rastreo GPS Android

App en Kotlin para ver la ubicación **solo entre celulares que tienen esta app**. Al instalarse se crea un usuario nuevo para ese teléfono; si se desinstala y se vuelve a instalar, es otro usuario.

## Cómo funciona

1. Cada celular genera un ID al abrir la app por primera vez y se registra en el servidor.
2. El GPS se comparte en segundo plano.
3. El mapa y la lista muestran únicamente esos dispositivos.

Hace falta un servidor en la PC (o en la red) porque los teléfonos no se ven entre sí sin un punto en común.

## 1. Arrancar el servidor

En una PC de la misma Wi‑Fi que los celulares:

```bat
cd C:\Users\genar\Downloads\RastreoGpsAndroid\servidor
python -m pip install -r requirements.txt
python app.py
```

La consola imprime la URL, por ejemplo `http://192.168.1.20:8080`. Esa misma dirección se abre en el navegador para ver el panel.

## 2. Compilar e instalar la app

Abre esta carpeta en Android Studio, espera el sync de Gradle y pulsa Run en cada celular.

En **Ajustes** de la app pega la URL del servidor (la IP que imprimió `python app.py`).

Permite ubicación **todo el tiempo** cuando Android lo pida.

## Notas

- Celular y PC deben estar en la misma red, salvo que publiques el servidor.
- El pin rojo eres tú; los verdes son los demás.
- Código de red interno: `rastreo` (app y servidor ya lo traen).
