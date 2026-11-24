# Data Safety: por qué se declara «sin recogida ni compartición»

Decisión del 2026-11-24 (reauditoría de publicación, hallazgo PLAY-02).
Respuestas en [`data-safety-responses.json`](data-safety-responses.json), generadas
por `tools/play-store/02-audit-data-safety.mjs`.

## Qué sale del dispositivo

| Flujo | Qué viaja | Quién lo recibe |
|---|---|---|
| Llegadas (`tiemposParada`) al abrir una parada o con un aviso activo | Código público de la parada e idioma | API pública de Dbus (`app.dbus.eus`), directamente desde el móvil |
| Cualquier conexión TCP | IP y metadatos de conexión | El servidor de Dbus, como cualquier servidor |
| «Cómo llegar» (`geo:`) | Coordenadas públicas y nombre de la parada | La app de mapas que elija el usuario, en el propio dispositivo |

No sale nada más: la ubicación del dispositivo se usa solo en memoria para ordenar
paradas cercanas; favoritos, avisos y ajustes se quedan en Room y SharedPreferences
con `allowBackup="false"`. No hay SDKs de analítica, publicidad ni fallos (DEX
revisado: sin Firebase ni GMS). El desarrollador no tiene servidores.

## Contraste con la guía de Google Play

Guía: <https://support.google.com/googleplay/android-developer/answer/10787469>.

- «Collect» es transmitir datos de usuario fuera del dispositivo. El código de una
  parada pública no identifica ni describe al usuario, y no va acompañado de ningún
  identificador de usuario o dispositivo.
- La IP no es un tipo de dato propio en el formulario. La guía pide declarar la
  ubicación aproximada o precisa **inferida** a partir de la IP; la app no hace esa
  inferencia.
- El intent `geo:` no transfiere datos del usuario (son datos públicos de la parada)
  y lo inicia el usuario, que espera que se abra su app de mapas: encaja en la
  excepción de acción iniciada por el usuario.

Con esto se responde «No» a «¿Recoge o comparte tu aplicación alguno de los tipos de
datos de usuario requeridos?», y `encryptsInTransit: false` (la API solo responde por
HTTP).

## Riesgo residual (no cerrado)

- La política de privacidad de Dbus (HTTP 200 en es/eu/en) cubre su **web**, no la
  API `SSIIMovilWSv2`. No hay evidencia documental de qué registra Dbus de esas
  peticiones ni durante cuánto tiempo.
- Las consultas de paradas, vistas en conjunto por Dbus junto a la IP, podrían
  interpretarse como «App activity». Si Google lo pide en la revisión, la
  alternativa es declarar «App activity → Other actions» como recogida, no
  compartida, para la funcionalidad de la app y sin fines publicitarios.
- Las políticas de la app ya avisan de que el proveedor puede ver la IP, metadatos de
  conexión y los códigos de parada consultados.
