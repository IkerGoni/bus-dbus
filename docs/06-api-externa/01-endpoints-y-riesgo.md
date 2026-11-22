# Investigación: endpoints de Dbus y riesgo de corte de acceso

Fecha: 2026-11-22. Método: peticiones HTTP reales con `curl`, lectura del
código del repo (`packages/core`, `apps/bot/scripts`) y consulta a Play Store,
APKPure y `dbus.eus`. Nada de esto es suposición salvo la sección 10.

## 1. Resumen ejecutivo

- La fuente activa es la API JSON del servicio `SSIIMovilWSv2` de la app oficial de Dbus, sobre HTTP sin autenticación.
- Hoy cualquier cliente puede llamar: no hay token, fingerprint, CORS restrictivo ni limitación de tasa observable.
- Se pueden obtener llegadas por parada, destino, línea, color, empresa, indicador GPS/horario, avisos y recorridos ordenados por paradas.
- No se puede obtener la posición geográfica de los autobuses desde esta API; la única fuente con posiciones (GTFS-RT VehiclePositions) devuelve 403.
- Con el bot (una IP) el bloqueo es trivial; con apps distribuidas (una IP por usuario) el bloqueo por IP no funciona porque no podemos distinguirnos de los usuarios legítimos de la app oficial.
- La única palanca técnica real del operador es añadir una comprobación en su servidor; la palanca no técnica es la vía legal/tienda.
- Defensa práctica: configuración remota del cliente para sobrevivir cambios de contrato sin actualización de la tienda.

## 2. Endpoints confirmados (vivos)

Base: `http://app.dbus.eus/SSIIMovilWSv2/ws/cons`

| Endpoint | Parámetros | Qué devuelve |
|---|---|---|
| `GET /tiemposParada` | `codParada`, `idioma` (`eu|es|en|fr`) | Llegadas en vivo de una parada |
| `GET /itinerariosLinea` | `idLinea` | Variantes de ruta de una línea |
| `GET /paradasItinerario` | `idItinerario` | Paradas ordenadas de una ruta |
| `GET /avisos` | `idioma` (obligatorio) | Avisos globales; hoy `NO DATOS` |

Fuente auxiliar, no Dbus: Overpass API (OpenStreetMap), usada por
`apps/bot/scripts/build-stop-coordinates.mjs` para el índice local de
coordenadas de paradas.

`apps/bot/data/dbus-catalog.json` no es un endpoint: es un índice generado en
build time con `itinerariosLinea` + `paradasItinerario`. Nunca contiene horarios.

### 2.1 Respuesta real de `/tiemposParada` (parada `50`, Tolosa 138)

```json
{
  "estado": "DATOS",
  "avisos": null,
  "parada": { "id": "232", "cod": "50", "desc": "Tolosa 138", "ordenParada": 0, "habitual": null },
  "tiempos": [
    {
      "itinerario": {
        "id": "684", "cod": "102", "destino": "Gros-Altza", "nombre": "Gros-Altza",
        "linea": { "id": "30", "cod": "27", "empresa": "dBUS", "nombre": "27", "color": -9144551 },
        "actual": false, "puntos": null, "tipoRecorrido": 0, "colorRGB": 0, "habitual": 1
      },
      "creacion": "22/11/2026 10:20:57",
      "minutos": 16,
      "cabecera": false,
      "tipo": 1,
      "hora": "10:36"
    }
  ]
}
```

Estados observados en `estado`: `DATOS`, `SIN_SERVICIO`, `NO DESC`.
Cuando no hay datos, `tiempos` es `null` y `avisos` puede contener texto de
supresión/traslado (p. ej. parada `1`: «Parada suprimida - traslados: L28 Pte. Sta. Catalina L21 sin servicio.»).

## 3. Endpoints descartados

- **404**: `/lineas`, `/listaLineas`, `/getLineas`, `/paradasLinea`, `/itinerarios`, `/empresas`, `/posiciones`, `/posicionamiento`, `/vehiculos`, `/autobuses`, `/bus`, `/noticias`, `/detalleLinea`, `/infoLinea`, `/recorrido`, `/trazado`, `/itinerario`, `/paradas`, `/listaParadas`, `/getParadas`, `/tiemposLinea`, `/getTiemposParada`, `/consultaParada`, `/puntos`, `/puntosItinerario`, `/posicionAutobuses`, raíz del servicio y `/?wsdl`.
- **400**: `/lineasParada` con toda combinación de parámetros probada (`codParada`, `codigoParada`, `parada`, `idParada`, `id`, `codigo`, `idLinea`, `idioma`, `idEmpresa`…; también `?50`). Con POST devuelve **405**: el endpoint existe pero solo admite GET; no encontramos la signatura válida.
- **400**: `/avisos` sin `idioma`.
- No hay WSDL ni documentación publicada. La raíz de `/SSIIMovilWSv2/` solo muestra un HTML: «SSIIMovilWS -- Version v0.9.2».
- HTTPS `https://app.dbus.eus/...` da *timeout*; solo responde HTTP.

## 4. Información que se puede obtener

- Minutos hasta la llegada de cada bus a una parada (`minutos`).
- Hora absoluta prevista de la llegada (`hora`, `HH:MM`).
- Si el dato viene de GPS en tiempo real (`tipo=1`) o de horario programado (`tipo=2`).
  El 2026-11-22 ~18:40 la parada 2 devolvió también `tipo=0` (L36, L46), de
  significado desconocido: bot y Android lo tratan como horario (todo lo que no
  es `1`).
- Código público de línea (`linea.cod`) y su id interno de Dbus (`linea.id`).
- Nombre de la línea, empresa (`dBUS`) y color entero de Dbus (`linea.color`).
- Destino / headsign real de cada bus (`itinerario.destino`, `nombre`).
- Indicador de si el bus sale de cabecera de línea (`cabecera`).
- Marca de tiempo de generación de cada predicción (`creacion`) y del response.
- Estado de la parada: con datos, sin servicio o desconocida (`estado`).
- Avisos de servicio por parada: supresiones, traslados, líneas sin servicio (`avisos`).
- Identificador del itinerario y su código interno (`itinerario.id`, `cod`).
- Variantes de ruta de una línea, con destino, `habitual`, `tipoRecorrido` y `colorRGB`.
- Recorrido ordenado de una ruta: id de parada, código público, nombre y `ordenParada`.
- Indicadores `actual` y `habitual` sobre si la ruta o parada son regulares.
- Coordenadas de las paradas, pero vía Overpass (OSM), no vía Dbus.

## 5. Localización de autobuses

- **No está disponible** en `SSIIMovilWSv2`: ningún endpoint devuelve lat/lon de vehículos.
- El campo `itinerario.puntos` existe en todas las respuestas pero viene `null` en todo lo sondeado; parece reservado a geometría de ruta, no a posiciones de vehículos.
- La única fuente con posiciones era GTFS-RT `mdb-3065` (VehiclePositions) de `opendata.euskadi.eus`; hoy redirige a `s3.itbatera.euskadi.eus` y devuelve `403 AccessDenied`. Igual `mdb-3066` (TripUpdates) y `mdb-3064` (Alerts).
- La app oficial no anuncia mapa de buses en tiempo real: describe llegadas en tiempo real, planos de recorrido, horarios, geolocalización de *paradas* cercanas, recarga Mugi y billetes QR.

## 6. ¿Son estos los endpoints de la app de Android?

- Sí: `SSIIMovilWSv2` es el servicio que consume la app oficial; el repo ya lo documentaba y las respuestas encajan con sus funciones.
- App oficial: «App oficial DBUS», paquete `com.indra.dbus`, desarrollador Compañía del Tranvía de San Sebastián, S.A., v2.7 (2026-08-01), 100k+ descargas.
- No es una API pública documentada: es un servicio interno de la app, sin contrato, SLA ni versión formal.
- **No verificado**: las cabeceras exactas que envía la app (no se pudo descargar el APK; APKPure devuelve 403).

## 7. Riesgo de corte de acceso

### 7.1 Escenario actual: bot con IP propia

- El bot sale de una única IP de VPS; en los logs del operador es identificable.
- Un ban por IP, un rate limit o un bloqueo de rangos de datacenter lo cortan con una regla.
- Mitigación parcial: caché TTL, sondeo de catálogo solo en build time, poco volumen.

### 7.2 Escenario distribuido: Android/iOS con IP por usuario

- Cada usuario sale con su IP residencial o móvil; banear una IP mata a un usuario, no al producto.
- Además nuestras peticiones son indistinguibles de las de un usuario legítimo de la app oficial: mismo endpoint, mismos parámetros, sin auth.
- Cualquier filtro por IP, ASN, país, User-Agent u Origin también afectaría a los usuarios de la app oficial.
- **Conclusión**: sin un cambio en el servidor del operador, no pueden cortarnos. La IP deja de ser una palanca.

### 7.3 Palancas que sí les quedan

| Palanca | Coste para ellos | Nos afecta |
|---|---|---|
| Cambio de contrato (renombrar path/param, añadir header) | Un deploy; rompe también a sus clientes antiguos | Sí, hasta que adaptemos el cliente |
| Token estático en su APK | Pequeño | No, se extrae con ingeniería inversa |
| Token rotatorio en servidor | Medio | Sí, obliga a reaccionar rápido |
| Play Integrity / App Attest | Alto: integrar SDK, verificar en servidor, forzar actualización de su app | **Sí, de forma efectiva**: el token está atado a su clave de firma; desde nuestra APK no se puede falsificar |
| Presión legal / retirada en tiendas | Bajo: no depende de tecnología | Sí, puede sacar la app del mercado |

Asimetría: ellos publican un cambio una vez; nosotros debemos detectarlo,
invertirlo, recompilar y esperar a que los usuarios actualicen desde la tienda.

### 7.4 Riesgo legal

- Las condiciones de uso de `dbus.eus` reservan derechos de propiedad
  intelectual sobre los contenidos, prohíben reproducción, distribución,
  transformación y comunicación pública sin autorización expresa, y prohíben
  ingeniería inversa.
- Se reservan el derecho a modificar las condiciones sin preaviso y a
  interrumpir el servicio total o parcialmente; jurisdicción: San Sebastián.
- Matiz importante: el texto habla del **website** (`www.dbus.es`), no
  explícitamente de la API `app.dbus.eus` ni de la app. Su aplicabilidad a
  nuestro caso es plausible pero no está probada.

### 7.5 Corrección a nuestro análisis inicial

- Valoración inicial: «pueden cortar por IP, país/ASN o User-Agent».
- Corrección: eso es cierto para el bot (una IP) pero **falso para apps
  distribuidas**, donde somos indistinguibles de los usuarios de la app
  oficial. Un filtro de ese tipo sería un tiro en el pie para el operador.
- Conclusión revisada: el riesgo real no es el ban de IP, es el cambio de
  contrato / attestation y la vía legal.

## 8. Mitigaciones recomendadas

- **Configuración remota**: descargar al arrancar base URL, paths, nombres de
  parámetros y headers desde un endpoint nuestro. Un renombre o un header nuevo
  se arregla sin pasar por la tienda. Neutraliza todos los cambios de contrato
  salvo Play Integrity/App Attest.
- **Petición directa desde el dispositivo**, sin un proxy único nuestro: si
  todas las apps pasan por nuestro servidor, recuperamos el problema del bot
  (una IP bloqueable).
- **Volumen bajo**: caché TTL agresiva, polling poco frecuente, catálogo
  generado en build time y nunca escaneado desde cada dispositivo.
- **Fuente alternativa lista** antes de depender de este endpoint en
  producción.
- **No suplantar la identidad de marca**: compatibilidad de protocolo sí;
  usar su nombre, logo o presentarse como app oficial, no.

## 9. Evidencia reproducible

```bash
# Endpoints vivos
curl -sS 'http://app.dbus.eus/SSIIMovilWSv2/ws/cons/tiemposParada?codParada=50&idioma=es'
curl -sS 'http://app.dbus.eus/SSIIMovilWSv2/ws/cons/itinerariosLinea?idLinea=30'
curl -sS 'http://app.dbus.eus/SSIIMovilWSv2/ws/cons/paradasItinerario?idItinerario=684'
curl -sS 'http://app.dbus.eus/SSIIMovilWSv2/ws/cons/avisos?idioma=es'

# Sin fingerprinting: mismo 200 con UA ajeno y Origin externo
curl -sS -o /dev/null -w '%{http_code}\n' \
  -A 'Dalvik/2.1.0 (Linux; U; Android 14; Pixel 8)' \
  -H 'Origin: https://mi-app.example' \
  'http://app.dbus.eus/SSIIMovilWSv2/ws/cons/tiemposParada?codParada=50&idioma=es'

# CORS / headers
curl -sS -D - -o /dev/null \
  -X OPTIONS 'http://app.dbus.eus/SSIIMovilWSv2/ws/cons/tiemposParada?codParada=50&idioma=es' \
  -H 'Origin: https://midominio.example' -H 'Access-Control-Request-Method: GET'

# Feeds GTFS-RT caídos
curl -sS -L -o /dev/null -w '%{http_code} %{url_effective}\n' \
  'https://opendata.euskadi.eus/transport/moveuskadi/dbus/mdb-3065.pb'
```

Observaciones de red: la respuesta GET solo expone `Content-Type`,
`Transfer-Encoding` y `Date`; sin cabeceras de rate limit. `OPTIONS` devuelve
`200` con `Allow: GET, HEAD, POST, PUT, DELETE, OPTIONS, PATCH` y sin
`Access-Control-Allow-Origin` (los navegadores bloquean lecturas cross-origin;
las apps nativas no). 20 peticiones rápidas seguidas devolvieron 200 en
0,11–0,22 s, sin señales de throttling.

## 10. Abierto / no verificado

- Cabeceras y User-Agent reales de la app oficial (APK no descargable desde APKPure).
- Existencia de algún endpoint no descubierto que devuelva posiciones de vehículos.
- Signatura válida de `/lineasParada` (existe, siempre 400 con lo probado).
- Otros parámetros útiles de `/avisos`.
- Aplicabilidad real de las condiciones de uso del website a la API.
- Umbrales reales de limitación de tasa; no se hicieron pruebas de carga.
