"""Servidor de rastreo GPS: comparte ubicaciones entre los celulares con la app
y la última posición conocida de etiquetas Bluetooth (iBeacon), vistas por esos celulares."""

from __future__ import annotations

import os
import socket
import sqlite3
import time
from pathlib import Path

from flask import Flask, g, jsonify, render_template_string, request
from flask_cors import CORS

PUERTO = int(os.environ.get("PUERTO", "8080"))
CODIGO_RED = os.environ.get("CODIGO_RED", "rastreo")
EN_LINEA_MS = 120_000
DB_PATH = Path(__file__).resolve().parent / "rastreo.db"

app = Flask(__name__)
CORS(app)


def get_db() -> sqlite3.Connection:
    if "db" not in g:
        g.db = sqlite3.connect(DB_PATH)
        g.db.row_factory = sqlite3.Row
    return g.db


@app.teardown_appcontext
def close_db(_exc: BaseException | None) -> None:
    db = g.pop("db", None)
    if db is not None:
        db.close()


def init_db() -> None:
    with sqlite3.connect(DB_PATH) as db:
        db.execute(
            """
            CREATE TABLE IF NOT EXISTS dispositivos (
                id TEXT PRIMARY KEY,
                nombre TEXT NOT NULL,
                modelo TEXT NOT NULL DEFAULT '',
                lat REAL,
                lng REAL,
                precision REAL,
                actualizado INTEGER
            )
            """
        )
        db.execute(
            """
            CREATE TABLE IF NOT EXISTS etiquetas (
                id TEXT PRIMARY KEY,               -- iBeacon "uuid-major-minor" en minúsculas
                nombre TEXT NOT NULL,
                tipo TEXT NOT NULL DEFAULT 'ibeacon',
                lat REAL,                          -- posición del celular que la vio
                lng REAL,
                precision REAL,                    -- precisión GPS de ese celular
                rssi INTEGER,                      -- señal de la etiqueta
                distancia REAL,                    -- metros estimados desde rssi
                visto_por TEXT,                    -- id del celular
                visto_por_nombre TEXT,
                actualizado INTEGER
            )
            """
        )
        db.commit()


def codigo_valido() -> bool:
    enviado = request.headers.get("X-Codigo-Red", "")
    return enviado == CODIGO_RED


def fila_a_dict(row: sqlite3.Row) -> dict:
    ahora = int(time.time() * 1000)
    actualizado = row["actualizado"]
    en_linea = bool(actualizado and (ahora - actualizado) <= EN_LINEA_MS)
    return {
        "id": row["id"],
        "nombre": row["nombre"],
        "modelo": row["modelo"],
        "lat": row["lat"],
        "lng": row["lng"],
        "precision": row["precision"],
        "actualizado": actualizado,
        "enLinea": en_linea,
    }


def etiqueta_a_dict(row: sqlite3.Row) -> dict:
    ahora = int(time.time() * 1000)
    actualizado = row["actualizado"]
    en_rango = bool(actualizado and (ahora - actualizado) <= EN_LINEA_MS)
    return {
        "id": row["id"],
        "nombre": row["nombre"],
        "tipo": row["tipo"],
        "lat": row["lat"],
        "lng": row["lng"],
        "precision": row["precision"],
        "rssi": row["rssi"],
        "distancia": row["distancia"],
        "vistoPor": row["visto_por"],
        "vistoPorNombre": row["visto_por_nombre"],
        "actualizado": actualizado,
        "enLinea": en_rango,
    }


def ips_locales() -> list[str]:
    encontradas: list[str] = []
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.connect(("8.8.8.8", 80))
            encontradas.append(sock.getsockname()[0])
    except OSError:
        pass
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if ip not in encontradas and not ip.startswith("127."):
                encontradas.append(ip)
    except OSError:
        pass
    return encontradas or ["127.0.0.1"]


@app.get("/")
def panel():
    db = get_db()
    dispositivos = [fila_a_dict(r) for r in db.execute("SELECT * FROM dispositivos")]
    etiquetas = [etiqueta_a_dict(r) for r in db.execute("SELECT * FROM etiquetas")]
    return render_template_string(PANEL_HTML, dispositivos=dispositivos, etiquetas=etiquetas)


@app.post("/api/dispositivos")
def registrar():
    if not codigo_valido():
        return jsonify({"error": "codigo de red invalido"}), 403
    datos = request.get_json(silent=True) or {}
    dispositivo_id = str(datos.get("id", "")).strip()
    nombre = str(datos.get("nombre", "")).strip() or "Celular"
    modelo = str(datos.get("modelo", "")).strip()
    if not dispositivo_id:
        return jsonify({"error": "falta id"}), 400
    db = get_db()
    existente = db.execute(
        "SELECT * FROM dispositivos WHERE id = ?", (dispositivo_id,)
    ).fetchone()
    if existente:
        db.execute(
            "UPDATE dispositivos SET nombre = ?, modelo = ? WHERE id = ?",
            (nombre, modelo or existente["modelo"], dispositivo_id),
        )
    else:
        db.execute(
            "INSERT INTO dispositivos (id, nombre, modelo) VALUES (?, ?, ?)",
            (dispositivo_id, nombre, modelo),
        )
    db.commit()
    row = db.execute(
        "SELECT * FROM dispositivos WHERE id = ?", (dispositivo_id,)
    ).fetchone()
    return jsonify(fila_a_dict(row))


@app.put("/api/dispositivos/<dispositivo_id>/ubicacion")
def ubicacion(dispositivo_id: str):
    if not codigo_valido():
        return jsonify({"error": "codigo de red invalido"}), 403
    datos = request.get_json(silent=True) or {}
    try:
        lat = float(datos["lat"])
        lng = float(datos["lng"])
    except (KeyError, TypeError, ValueError):
        return jsonify({"error": "lat y lng son obligatorios"}), 400
    precision = datos.get("precision")
    try:
        precision_val = float(precision) if precision is not None else None
    except (TypeError, ValueError):
        precision_val = None
    ahora = int(time.time() * 1000)
    db = get_db()
    row = db.execute(
        "SELECT * FROM dispositivos WHERE id = ?", (dispositivo_id,)
    ).fetchone()
    if not row:
        return jsonify({"error": "dispositivo no registrado"}), 404
    db.execute(
        """
        UPDATE dispositivos
        SET lat = ?, lng = ?, precision = ?, actualizado = ?
        WHERE id = ?
        """,
        (lat, lng, precision_val, ahora, dispositivo_id),
    )
    db.commit()
    row = db.execute(
        "SELECT * FROM dispositivos WHERE id = ?", (dispositivo_id,)
    ).fetchone()
    return jsonify(fila_a_dict(row))


@app.get("/api/dispositivos")
def listar():
    if not codigo_valido():
        return jsonify({"error": "codigo de red invalido"}), 403
    filas = get_db().execute(
        "SELECT * FROM dispositivos ORDER BY nombre COLLATE NOCASE"
    ).fetchall()
    return jsonify({"dispositivos": [fila_a_dict(r) for r in filas]})


@app.delete("/api/dispositivos/<dispositivo_id>")
def borrar(dispositivo_id: str):
    if not codigo_valido():
        return jsonify({"error": "codigo de red invalido"}), 403
    db = get_db()
    db.execute("DELETE FROM dispositivos WHERE id = ?", (dispositivo_id,))
    db.commit()
    return jsonify({"ok": True})


# ---------------------------------------------------------------------------
# Etiquetas Bluetooth (iBeacon). No reportan su posición: la de cada etiqueta
# es la del celular que la detectó por última vez.
# ---------------------------------------------------------------------------

@app.get("/api/etiquetas")
def listar_etiquetas():
    if not codigo_valido():
        return jsonify({"error": "codigo de red invalido"}), 403
    filas = get_db().execute(
        "SELECT * FROM etiquetas ORDER BY nombre COLLATE NOCASE"
    ).fetchall()
    return jsonify({"etiquetas": [etiqueta_a_dict(r) for r in filas]})


@app.post("/api/etiquetas")
def registrar_etiqueta():
    if not codigo_valido():
        return jsonify({"error": "codigo de red invalido"}), 403
    datos = request.get_json(silent=True) or {}
    etiqueta_id = str(datos.get("id", "")).strip().lower()
    nombre = str(datos.get("nombre", "")).strip() or "Etiqueta"
    tipo = str(datos.get("tipo", "")).strip() or "ibeacon"
    if not etiqueta_id:
        return jsonify({"error": "falta id"}), 400
    db = get_db()
    existe = db.execute("SELECT 1 FROM etiquetas WHERE id = ?", (etiqueta_id,)).fetchone()
    if existe:
        db.execute("UPDATE etiquetas SET nombre = ?, tipo = ? WHERE id = ?", (nombre, tipo, etiqueta_id))
    else:
        db.execute(
            "INSERT INTO etiquetas (id, nombre, tipo) VALUES (?, ?, ?)",
            (etiqueta_id, nombre, tipo),
        )
    db.commit()
    row = db.execute("SELECT * FROM etiquetas WHERE id = ?", (etiqueta_id,)).fetchone()
    return jsonify(etiqueta_a_dict(row))


@app.put("/api/etiquetas/<etiqueta_id>/vista")
def vista_etiqueta(etiqueta_id: str):
    if not codigo_valido():
        return jsonify({"error": "codigo de red invalido"}), 403
    etiqueta_id = etiqueta_id.strip().lower()
    datos = request.get_json(silent=True) or {}
    try:
        lat = float(datos["lat"])
        lng = float(datos["lng"])
    except (KeyError, TypeError, ValueError):
        return jsonify({"error": "lat y lng son obligatorios"}), 400

    def numero(clave):
        valor = datos.get(clave)
        try:
            return float(valor) if valor is not None else None
        except (TypeError, ValueError):
            return None

    rssi = datos.get("rssi")
    try:
        rssi_val = int(rssi) if rssi is not None else None
    except (TypeError, ValueError):
        rssi_val = None
    por_id = str(datos.get("porId", "")).strip() or None
    por_nombre = str(datos.get("porNombre", "")).strip() or None
    ahora = int(time.time() * 1000)

    db = get_db()
    if not db.execute("SELECT 1 FROM etiquetas WHERE id = ?", (etiqueta_id,)).fetchone():
        return jsonify({"error": "etiqueta no registrada"}), 404
    db.execute(
        """
        UPDATE etiquetas
        SET lat = ?, lng = ?, precision = ?, rssi = ?, distancia = ?,
            visto_por = ?, visto_por_nombre = ?, actualizado = ?
        WHERE id = ?
        """,
        (lat, lng, numero("precision"), rssi_val, numero("distancia"),
         por_id, por_nombre, ahora, etiqueta_id),
    )
    db.commit()
    row = db.execute("SELECT * FROM etiquetas WHERE id = ?", (etiqueta_id,)).fetchone()
    return jsonify(etiqueta_a_dict(row))


@app.delete("/api/etiquetas/<etiqueta_id>")
def borrar_etiqueta(etiqueta_id: str):
    if not codigo_valido():
        return jsonify({"error": "codigo de red invalido"}), 403
    db = get_db()
    db.execute("DELETE FROM etiquetas WHERE id = ?", (etiqueta_id.strip().lower(),))
    db.commit()
    return jsonify({"ok": True})


PANEL_HTML = """
<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Rastreo GPS</title>
  <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
  <style>
    :root { font-family: Segoe UI, sans-serif; color: #123837; }
    body { margin: 0; background: #EEF2ED; }
    header { background: #205C5A; color: #fff; padding: 16px 20px; }
    header p { margin: 6px 0 0; opacity: .85; font-size: 14px; }
    #mapa { height: 70vh; }
    .lista { padding: 12px 20px 24px; }
    .item { background: #fff; border-radius: 12px; padding: 12px 14px; margin: 8px 0; }
    .ok { color: #1b7f4a; font-weight: 600; }
    .off { color: #8a6a00; }
  </style>
</head>
<body>
  <header>
    <h1>Rastreo GPS</h1>
    <p>Celulares con la app y etiquetas Bluetooth vistas por ellos.</p>
  </header>
  <div id="mapa"></div>
  <div class="lista">
    <h2>Celulares</h2>
    {% for d in dispositivos %}
      <div class="item">
        <strong>{{ d.nombre }}</strong>
        {% if d.enLinea %}<span class="ok">en linea</span>{% else %}<span class="off">sin senal</span>{% endif %}
        <div>{{ d.modelo }} · {{ d.id[:8] }}</div>
      </div>
    {% else %}
      <p>Todavia no hay celulares registrados. Abre la app en un telefono.</p>
    {% endfor %}

    <h2>Etiquetas</h2>
    {% for e in etiquetas %}
      <div class="item">
        <strong>{{ e.nombre }}</strong>
        {% if e.enLinea %}<span class="ok">en rango</span>{% else %}<span class="off">fuera de rango</span>{% endif %}
        <div>
          {% if e.vistoPorNombre %}vista por {{ e.vistoPorNombre }}{% else %}sin detecciones{% endif %}
          {% if e.rssi is not none %} · {{ e.rssi }} dBm{% endif %}
          {% if e.distancia is not none %} · ~{{ '%.0f' % e.distancia }} m{% endif %}
        </div>
      </div>
    {% else %}
      <p>Sin etiquetas registradas.</p>
    {% endfor %}
  </div>
  <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
  <script>
    const dispositivos = {{ dispositivos | tojson }};
    const etiquetas = {{ etiquetas | tojson }};
    const mapa = L.map('mapa').setView([19.43, -99.13], 12);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap'
    }).addTo(mapa);
    const puntos = [];
    dispositivos.forEach((d) => {
      if (d.lat == null || d.lng == null) return;
      const m = L.marker([d.lat, d.lng]).addTo(mapa).bindPopup(d.nombre);
      puntos.push(m.getLatLng());
    });
    const iconoEtiqueta = L.divIcon({
      className: 'etq',
      html: '<div style="width:16px;height:16px;border-radius:4px;background:#a66cff;border:2px solid #fff;box-shadow:0 0 0 2px #a66cff55"></div>',
      iconSize: [16, 16],
      iconAnchor: [8, 8],
    });
    etiquetas.forEach((e) => {
      if (e.lat == null || e.lng == null) return;
      const cuando = e.vistoPorNombre ? ('vista por ' + e.vistoPorNombre) : '';
      const m = L.marker([e.lat, e.lng], { icon: iconoEtiqueta }).addTo(mapa)
        .bindPopup('<b>' + e.nombre + '</b><br>' + cuando);
      puntos.push(m.getLatLng());
    });
    if (puntos.length) mapa.fitBounds(puntos, { padding: [40, 40], maxZoom: 16 });
  </script>
</body>
</html>
"""


init_db()

if __name__ == "__main__":
    ips = ips_locales()
    print("Rastreo GPS — servidor listo")
    print("En la app, usa una de estas URLs:")
    for ip in ips:
        print(f"  http://{ip}:{PUERTO}")
    print("Panel web: abre esa misma URL en el navegador.")
    app.run(host="0.0.0.0", port=PUERTO, debug=False)
