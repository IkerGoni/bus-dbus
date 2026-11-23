# Play Store-ko fitxaren zirriborroa — Bus Donostia (1.1.0 bertsioa)

> **Zirriborroa, euskaldun baten berrikuspenaren zain.** Ez igo Play Console-ra
> Ikerren baimen espliziturik gabe.
> 2026-11-23an erabat berridatzia: jatorrizko testua asmatua zen (koreerazko eta
> arabierazko karaktereak zituen, A5-03). Orain aplikazioaren `values-eu/`
> kateetatik abiatuta dago idatzita.

## Aplikazioaren datuak

| Eremua | Balioa |
|---|---|
| Izena | Bus Donostia (ez-ofiziala) |
| applicationId | `eus.busdonostia.app` (Ikerren onespenaren zain) |
| Kategoria | Mapak eta nabigazioa (bakarra) |
| Bertsioa | 1.1.0 (versionCode 2) — `app/build.gradle.kts`-ekin bat |
| SDK minimoa | 26 |
| SDK helburua | 36 |
| Prezioa | Doan |
| Aplikazio barruko erosketak | Ez |
| Iragarkiak | Ez |

Izena launcher-aren labelarekin bat dator (`app_launcher_name`:
«Bus Donostia (ez-ofiziala)»). Aplikazio barruko goiburua «Bus Donostia» da.

## Hizkuntzak

Gaztelania (es) eta euskara (eu), `values/` eta `values-eu/`-ean gako berberak
(`StringsParityTest`-ek egiaztatutako paritatea).

## Deskribapen laburra (eu) — 75 karaktere (muga: 80)

Donostiako Dbus autobusuen iritsierak zuzenean, eta abisuak zure geltokian.

## Deskribapen luzea (eu)

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

## Grafiko-fitxak (Ikerren zain)

- Ikonoa: ez-ofiziala, Ikerren diseinuaren/onespenaren zain (gaur egun: autobus-glifo generikoa, behin-behinekoa).
- Pantaila-argazkiak: es eta eu, A01 Hasiera, A02 Geltokiak (Hurbilekoak), A05 Xehetasuna, A04 Nire abisuak, A06 Abisua konfiguratu.
- **Aurreko planoko zerbitzua duen funtzioaren bideoa (Play-k eskatua)**: grabatzeko zain; gidoia [`ficha-play-es.md`](ficha-play-es.md)-n.

## Datuen segurtasun-deklarazioa (Data Safety)

**Ez da erabiltzailearen daturik biltzen ez hirugarrenekin partekatzen.**
- Ez da datu pertsonalik biltzen.
- Ez da kokapen-daturik biltzen (kokapena tokian bertan erabiltzen da Hurbilekoak ordenatzeko; ez da inoiz gordetzen ez bidaltzen).
- Ez dago zerbitzari propiorik ez backend-ik.
- Ez da FCM ez hirugarrenen push-jakinarazpenik erabiltzen.

## Aurreko planoko zerbitzuaren justifikazioa (`FOREGROUND_SERVICE_DATA_SYNC`)

**Zergatik da beharrezkoa?** Abisuen motorrak Dbus-en iritsierak kontsultatu behar ditu aplikazioa ikusgai ez dagoen bitartean, iritsiera-jakinarazpena une egokian bidaltzeko. Android-ek atzeko planoko lan luzeak aurreko planoko zerbitzu gisa exekutatzera behartzen ditu, jakinarazpen ikusgai batekin («L… zaintzen…»).

**Zenbat denboraz exekutatzen da?** Erabiltzaileak sortutako gutxienez abisu aktibo bat dagoen bitartean bakarrik. Automatikoki gelditzen da abisu aktiborik geratzen ez denean (erabiltzaileak pausatzen edo ezabatzen dituenean). Gainera:
- Abisu bat **7 egunera** iraungitzen da abisatu ez badu.
- Abisu bat gehienez **3 orduz** zaintzen da jarraian bat datorren busik aurkitu gabe (`alerts.maxWatchHours`, `config.json`-en); muga horren ondoren, aplikazioak zaintzeari uzten diola jakinarazi eta abisua ezabatzen du.
- Dbus-ek 60 min badaramatza zerbitzurik gabe edo erantzun gabe zaindutako geltoki guztietan, motorra gelditu eta jakinarazi egiten du.
- Android 15+-ek `dataSync` FGSa 6 ordura mugatzen du 24 ordeko; muga iristean, aplikazioak jakinarazten du.
- **Telefonoa berrabiarazi ondoren**, zaintza ez da berez hasten (Android 15-ek ez du `dataSync` `BOOT_COMPLETED`-etik hasten uzten): aplikazioa irekitzean berreskuratzen da.

**Erabiltzaileak hasten du?** Bai. Zerbitzua erabiltzaileak A06 pantailatik lehen abisua sortu ondoren bakarrik hasten da. Erabiltzaileak abisuak edozein unetan pausatu edo ezabatu ditzake A04-tik.

## Kokapena

`ACCESS_COARSE_LOCATION` eta `ACCESS_FINE_LOCATION` deklaratzen dira, baina:
- «Hurbilekoak» pantailako geltokiak oinezko distantziaren arabera ordenatzeko bakarrik erabiltzen dira.
- Ez dira inoiz diskoan gordetzen ez zerbitzari batera bidaltzen.
- Erabiltzailearen keinu baten bidez bakarrik eskatzen dira, eta ukatu daitezke aplikazioaren gainerakoa blokeatu gabe.

## OpenStreetMap atribuzioa (ODbL)

Geltokien koordenatuak OpenStreetMap-en indize lokala sortzen dira eta `catalog.json` asset-ean paketatzen dira. Atribuzioa aplikazioan ikusgai dago (`osm_attribution`): «Geltokien koordenatuak: © OpenStreetMap contributors, ODbL».

## Pribatutasun-politika

**Ostatatzeko zain (Iker).** Play-k URL publiko bat eskatzen du; laburpen hau
prest dago Ikerrek aukeratzen duen hosting-ean hitzez hitz argitaratzeko:

> Bus Donostia-k ez du datu pertsonalik biltzen ez gordetzen. Gailuan ukitzen diren datu bakarrak hauek dira:
> - Geltoki eta lineen katalogoa (aplikazioan txertatuta, eguneragarria).
> - Zure gogokoak eta abisuak (zure gailuan bakarrik gordeta, aplikazioaren tokiko datu-basean).
> - Zure uneko kokapena (memorian bakarrik, Hurbilekoak ordenatzeko; ez da inoiz gordetzen ez bidaltzen).
>
> Ez dago erabiltzaile-konturik, ez saio-hasierarik, ez zerbitzari propiorik.

## Play Store-ko politika garrantzitsuak

- `FOREGROUND_SERVICE_DATA_SYNC` deklarazioa goian justifikatuta, bideoaren zain.
- Kokapen zehatzik ez atzeko planoan etengabe (erabiltzaileak eskatuta bakarrik, pantailan).
- Iragarkirik ez eta hirugarren SDK-rik ez, Android/Compose liburutegi estandarrak eta OkHttp izan ezik (`app.dbus.eus`-era zuzenean).
- Ordezkatze-politika: izenean «(ez-ofiziala)» eta ohar argia A07-n («Aplikazio ez-ofiziala: ez du loturarik Dbus-ekin ez Donostiako Udalarekin»).
