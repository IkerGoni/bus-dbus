# Borrador de ficha de Play Store — Bus Donostia (v1.1.0)

> Borrador para revisión de Iker. **No subir a Play Console sin orden explícita.**
> Corregida el 2026-11-23 tras la auditoría adversarial (A5-03, A5-07, A5-09):
> descripción corta ≤ 80 caracteres, errata del permiso, versión y nombre
> alineados con el APK, categoría única, tope de vigilancia y reinicio
> documentados. Pendientes de Iker: vídeo del FGS, política de privacidad
> alojada (URL), capturas, icono y keystore de subida.

## Datos de la aplicación

| Campo | Valor |
|---|---|
| Nombre | Bus Donostia (no oficial) |
| applicationId | `eus.busdonostia.app` (pendiente de visto bueno de Iker) |
| Categoría | Mapas y navegación (una sola; Play no admite «Mapas y navegación / Transporte») |
| Versión | 1.1.0 (versionCode 2) — coincide con `app/build.gradle.kts` |
| SDK mínimo | 26 |
| SDK destino | 36 |
| Precio | Gratis |
| Compras integradas | No |
| Anuncios | No |

El nombre coincide con el label del launcher (`app_launcher_name`:
«Bus Donostia (no oficial)» / «Bus Donostia (ez-ofiziala)»). La cabecera dentro
de la app sigue siendo «Bus Donostia».

## Idiomas

Español (es) y euskera (eu), con claves idénticas en `values/` y `values-eu/`
(paridad verificada por `StringsParityTest`).

## Descripción corta (es) — 77 caracteres (límite: 80)

Llegadas de Dbus en Donostia en vivo y avisos antes de que llegue tu autobús.

## Descripción larga (es)

Bus Donostia es una aplicación no oficial para consultar las llegadas en tiempo real de los autobuses urbanos de Dbus en Donostia / San Sebastián. No tiene ninguna relación con Dbus ni con el Ayuntamiento de Donostia.

**¿Qué hace?**
- Consulta las próximas llegadas de cada parada, con el destino y si los datos son en vivo o de horario.
- Guarda paradas y líneas favoritas.
- Crea avisos de llegada: la aplicación te avisa unos minutos antes de que llegue tu autobús (una sola vez por aviso; caduca a los 7 días si no llega a avisar).
- Ve las paradas más cercanas a tu ubicación (solo para ordenar por distancia; la ubicación no se guarda ni se envía).

**Datos reales únicamente**
Todos los horarios y llegadas provienen de la API pública de Dbus (`app.dbus.eus`), consultada en vivo. No hay servidor propio ni datos inventados.

**Permisos**
- Ubicación: solo para ordenar las paradas cercanas por distancia. Nunca se guarda ni se envía.
- Notificaciones: para avisarte cuando tu autobús está cerca. Solo se activa si tú creas un aviso.

**Atribución**
Las coordenadas de paradas provienen de OpenStreetMap bajo licencia ODbL.

## Descripción larga (eu)

> Texto corregido el 2026-11-23 (el original estaba inventado, A5-03).
> Pendiente de revisión por una persona euskaldun antes de subir a Play.
> Versión completa y paralela en [`ficha-play-eu.md`](ficha-play-eu.md).

Bus Donostia Dbus-en hiri-autobusen iritsierak denbora errealean kontsultatzeko aplikazio ez-ofiziala da, Donostian / San Sebastian. Aplikazioak ez du loturarik Dbus-ekin ez Donostiako Udalarekin.

**Zer egiten du?**
- Geltoki bakoitzaren hurrengo iritsierak kontsultatu, helmugarekin eta datuak zuzenean (GPS) edo ordutegitik datozen adierazita.
- Geltoki eta linea gogokoak gorde.
- Iritsiera-abisuak sortu: autobusa iritsi baino minutu batzuk lehenago abisatzen dizu (behin bakarrik abisu bakoitzeko; 7 egunera iraungitzen da abisatu ez badu).
- Zure kokapenetik hurbilen dauden geltokiak ikusi (distantziaren arabera ordenatzeko bakarrik; kokapena ez da gordetzen ez bidaltzen).

**Benetako datuak soilik**
Ordutegi eta iritsiera guztiak Dbus-en API publikotik (`app.dbus.eus`) datoz, zuzenean kontsultatuta. Ez dago zerbitzari propiorik ez asmatutako daturik.

**Baimenak**
- Kokapena: hurbileko geltokiak distantziaren arabera ordenatzeko bakarrik. Ez da inoiz gordetzen ez bidaltzen.
- Jakinarazpenak: zure autobusa hurbil dagoenean abisatzeko. Abisu bat sortzen duzunean bakarrik aktibatzen da.

**Atribuzioa**
Geltokien koordenatuak OpenStreetMap-etik datoz, ODbL lizentziarekin.

## Fichas gráficas (pendiente de Iker)

- Icono: no oficial, pendiente de diseño/aprobación de Iker (hoy: glifo genérico de autobús, provisional).
- Capturas: es y eu, A01 Inicio, A02 Paradas (Cercanas), A05 Detalle, A04 Mis avisos, A06 Configurar aviso.
- **Vídeo de la función con servicio en primer plano (obligatorio para Play)**: ver guion abajo.

## Declaración de datos de seguridad (Data Safety)

**No se recopilan ni se comparten datos de usuario con ningún tercero.**
- No se recopilan datos personales.
- No se recopilan datos de ubicación (la ubicación se usa solo localmente para ordenar Cercanas y nunca se guarda ni envía).
- No hay servidor propio ni backend.
- No se usa FCM ni notificaciones push de terceros.

## Justificación del servicio en primer plano (`FOREGROUND_SERVICE_DATA_SYNC`)

**¿Por qué es necesario?** El motor de avisos debe consultar las llegadas de Dbus en segundo plano mientras la aplicación no está visible, para lanzar la notificación de llegada en el momento justo. Android obliga a los trabajos prolongados en segundo plano a ejecutarse como servicio en primer plano con notificación visible («Vigilando …»).

**¿Cuánto se ejecuta?** Solo mientras haya al menos un aviso activo creado por el usuario. Se detiene automáticamente cuando no quedan avisos activos (o cuando el usuario los pausa o elimina). Además:
- Un aviso caduca a los **7 días** si no llega a dispararse.
- Pasadas **3 horas** desde que se guardó un aviso (`alerts.maxWatchHours` en `config.json`), si en ese momento no hay ningún bus de su línea y destino en las llegadas, la app deja de vigilarlo, lo notifica y lo elimina. No es un tope fijo: si la línea sigue teniendo llegadas, el aviso se vigila hasta que suena, caduca o Android detiene el servicio.
- Si Dbus lleva 60 min sin servicio o sin responder en todas las paradas vigiladas, el motor se para y lo notifica.
- Android 15+ limita el FGS `dataSync` a 6 h por 24 h; al alcanzar el límite, la app lo notifica.
- **Tras reiniciar el teléfono**, la vigilancia no arranca sola (Android 15 no permite iniciar `dataSync` desde `BOOT_COMPLETED`): se retoma al abrir la app.

**¿El usuario lo inicia?** Sí. El servicio arranca solo después de que el usuario crea su primer aviso desde la pantalla A06. El usuario puede pausar o eliminar avisos en cualquier momento desde A04.

**Vídeo exigido por Play** ([Play Console Help 13392821](https://support.google.com/googleplay/android-developer/answer/13392821)): pendiente de grabar (Iker). Guion sugerido, en un móvil real o AVD con la app en es:
1. A05 Detalle de parada con llegadas en vivo → tocar 🔔 en una llegada.
2. A06: elegir antelación (p. ej. 2') → «Guardar aviso».
3. Mostrar la notificación persistente «Vigilando L… en …» (el FGS) con la app en segundo plano.
4. Esperar la notificación de llegada «L… → … en X min» y mostrar que el aviso desaparece de A04 y el FGS se detiene.
Duración real del flujo: la que dure la espera del bus (con antelación 2', normalmente 1–3 min); el vídeo debe mostrar el flujo completo, no una simulación.

## Ubicación

Se declaran `ACCESS_COARSE_LOCATION` y `ACCESS_FINE_LOCATION` (errata
«ACCCESS_FINE_LOCATION» del borrador original corregida; el manifiesto siempre
tuvo `ACCESS_FINE_LOCATION`), pero:
- Solo se usan para ordenar las paradas de la pantalla «Cercanas» por distancia a pie.
- Nunca se guardan en disco ni se envían a ningún servidor.
- Se piden solo por gesto del usuario y se pueden denegar sin bloquear el resto de la aplicación.

## Atribución OpenStreetMap (ODbL)

Las coordenadas de las paradas se generan a partir del índice local de OpenStreetMap y se empaquetan en el asset `catalog.json`. La atribución es visible en la aplicación (`osm_attribution`): «Coordenadas de paradas: © OpenStreetMap contributors, ODbL».

## Política de privacidad

**Pendiente de alojar (Iker).** Play exige una URL pública; el resumen siguiente
es el texto listo para publicar tal cual en el hosting que elija Iker:

> Bus Donostia no recopila ni almacena datos personales. Los únicos datos que tocan el dispositivo son:
> - El catálogo de paradas y líneas (embutido en la aplicación, actualizable).
> - Tus favoritas y avisos (almacenados solo en tu dispositivo, en la base de datos local de la aplicación).
> - Tu ubicación actual (solo en memoria, para ordenar Cercanas; nunca persistida ni enviada).
>
> No hay cuenta de usuario, no hay inicio de sesión, no hay servidor propio.

## Políticas de Play Store relevantes

- Declaración de `FOREGROUND_SERVICE_DATA_SYNC` justificada arriba, con vídeo pendiente.
- Sin acceso a ubicaciones precisas en segundo plano continuo (solo a petición del usuario, en pantalla).
- Sin publicidad ni SDK de terceros salvo las librerías estándar de Android/Compose y OkHttp (consulta directa a `app.dbus.eus`).
- Política de suplantación: nombre con «(no oficial)» y descargo explícito en A07 («App no oficial, sin relación con Dbus ni con el Ayuntamiento de Donostia»).
