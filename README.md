<p align="center">
  <img src="fastlane/metadata/android/es-ES/images/icon.png" width="128" alt="Icono de Pardela: una pardela vuela rasante sobre el mar al atardecer">
</p>

<h1 align="center">Pardela</h1>

<p align="center"><b>Español</b> · <a href="README.en.md">English</a></p>

<p align="center">Siga sus vuelos como en el tablón del aeropuerto.<br>
Letra grande, botones grandes y avisos claros. Sin cuentas: todo se queda en su móvil.</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/licencia-MIT-blue" alt="Licencia MIT"></a>
  <img src="https://img.shields.io/badge/Android-8.0%2B-3ddc84" alt="Android 8.0 o superior">
  <img src="https://img.shields.io/badge/idioma-español%20%7C%20English-orange" alt="En español e inglés">
</p>

> **Estado:** primera versión (1.0.0). Si encuentra un fallo, abra un [issue](https://github.com/cruzpazv/pardela/issues).

La pardela es un ave marina de Canarias que cruza el océano y siempre vuelve a casa.

## Instalar
**Con [Obtainium](https://github.com/ImranR98/Obtainium) (recomendado):** instale Obtainium, pulse *Añadir app* y pegue `https://github.com/cruzpazv/pardela`. Le avisará de cada versión nueva y la instalará por usted.

**A mano:** descargue `pardela.apk` de [Releases](https://github.com/cruzpazv/pardela/releases) y ábralo en el móvil. Android le pedirá permiso para instalar apps de esa fuente.

Todas las versiones van firmadas con la misma clave, así que cada una se instala encima de la anterior sin perder sus datos.

## Qué hace
- **Buscar un vuelo** por el número del billete (`IB3166`): estado, horarios, puerta, cinta de equipaje y avance en directo.
- **Seguir vuelos:** avisos de embarque, despegue, aterrizaje, retrasos y cambios de puerta o de cinta.
- **Vigilar ahora:** un aviso fijo en el móvil que se actualiza cada minuto, con la altura, la velocidad y el tiempo que falta.
- **Aeropuertos:** tablón de salidas y llegadas. Guarde los que mira a menudo.
- **Trayectos:** un aviso de cada vuelo entre dos ciudades (por ejemplo, Gran Canaria → Madrid).
- **El avión:** foto, fabricante, modelo, matrícula y logotipo de la aerolínea.
- **Mapa:** por dónde va el avión, la ruta prevista y el recorrido real que ha hecho.
- **En el destino:** la hora local, el tiempo ahora y la previsión para cuando aterrice.
- **Compartir, calendario y voz:** envíe el vuelo por WhatsApp, guárdelo en el calendario u oiga su estado en voz alta.
- **Recordatorio** unas horas antes de salir.
- **Widget** para la pantalla de inicio con el próximo vuelo, su estado y sus horas.
- **Modo pantalla** para dejar una tablet fija (por ejemplo, una Amazon Fire): aviones cerca (en radar, tabla, letrero LED, el más cercano con su foto, su ruta o la pista del aeropuerto), el tablón de un aeropuerto o sus vuelos, siempre encendida y actualizándose sola.
- **¿Qué avión pasa por aquí?** Mapa y lista de los aviones en el aire cerca de usted o de un aeropuerto, con su ruta.
- **Buscar por trayecto:** todos los vuelos directos de un día entre dos aeropuertos, hasta un año vista.
- **Historial del avión:** sus vuelos de los últimos 7 días.
- **Puntualidad:** cuánto suele retrasarse ese vuelo al salir y al llegar (con clave).

## Pensada para que la use cualquiera
- Letra grande por defecto y botones de al menos 68 dp.
- **Modo sencillo** (opcional): solo buscar, seguir y escuchar vuelos.
- Guía rápida al empezar, que se puede volver a ver desde Ajustes.
- Cada vuelo indica cuándo se actualizó, y la app avisa si no hay conexión.
- Los avisos traen botones para **escuchar** o **compartir** el vuelo sin abrir la app.
- En español o en inglés: el del móvil o el que elija en Ajustes → Aspecto.
- Modo claro, oscuro o automático.
- **Colores para daltonismo** (paleta Okabe-Ito). Además, cada estado lleva siempre su símbolo y su texto, nunca solo color: ✔ aterrizado, ⚠ retrasado, ✕ cancelado, ✈ en vuelo.
- Animaciones que se pueden desactivar.
- Horas de silencio: por la noche los avisos llegan sin sonido, salvo las cancelaciones.
- Unidades a elegir: metros y km/h, o pies y nudos.

## Privacidad
- Sin cuentas, sin registros, sin publicidad y sin rastreadores.
- Todo se guarda en un único fichero dentro del móvil. La copia en la nube de Android está desactivada.
- Para pasar sus datos a otro móvil: Ajustes → **Guardar copia** y **Recuperar copia**. La copia no incluye la clave API.
- La ubicación (solo aproximada) se pide únicamente para «¿Qué avión pasa por aquí?», no se guarda y no sale del móvil: solo se usa para pedir al radar los aviones de la zona.
- La app solo se conecta a los servicios de datos de la tabla siguiente.

## De dónde salen los datos
| Fuente | ¿Necesita clave? | Para qué |
|---|---|---|
| [adsb.fi](https://adsb.fi) / [adsb.lol](https://adsb.lol) | no | posición de los aviones en directo y recorrido de las últimas 24 h (radar ADS-B abierto; datos de adsb.lol bajo licencia ODbL) |
| [adsbdb.com](https://www.adsbdb.com) | no | ruta de cada vuelo y ficha del avión |
| [planespotters.net](https://www.planespotters.net) | no | foto del avión, con su autor |
| pics.avs.io | no | logotipos de aerolíneas |
| [OurAirports](https://ourairports.com) | no | lista de 3245 aeropuertos, incluida en la app |
| [OpenStreetMap](https://www.openstreetmap.org/copyright) | no | mapa del vuelo (© colaboradores de OpenStreetMap) |
| [Open-Meteo](https://open-meteo.com) | no | tiempo y hora local en el destino (CC BY 4.0) |
| [AeroDataBox](https://rapidapi.com/aedbx-aedbx/api/aerodatabox) | **opcional** | horarios, retrasos, puertas y tablón completo |

**Sin clave**, Pardela funciona con radar: ve los vuelos que están en el aire, su ruta y su avión.

**Con una clave gratuita de AeroDataBox** verá además horarios, retrasos, puertas y el tablón completo de cada aeropuerto. Para conseguirla:
1. Cree una cuenta gratuita en rapidapi.com.
2. Suscríbase al plan BASIC de AeroDataBox.
3. Pegue la clave en la app.

La clave se guarda solo en su móvil. El plan gratuito tiene unas 600 consultas al mes, así que Pardela las espacia: cada 30 min cerca de la salida y cada 3 h el resto del tiempo.

---

## Para desarrolladores

Kotlin + Jetpack Compose, sin librerías privativas. Dependencias: OkHttp, Coil, kotlinx.serialization, WorkManager y osmdroid.

```
app/src/main/java/io/github/cruzpazv/pardela/
  data/   Modelos, Almacen (JSON local + copias), Catalogo (aeropuertos, aerolíneas, fabricantes), Fuentes (APIs), Repositorio
  notif/  Avisos (textos, filtros, horas de silencio), Comprobador (WorkManager, 15 min), VigilanciaService (directo),
          Widget (grande y pequeño), LecturaService (ESCUCHAR desde un aviso)
  ui/     Tablero (temas, paletas, logos), Bienvenida, Guia, Inicio, Buscar, Detalle, Tablon, Ajustes,
          Mapa, Cerca, Destino (tiempo), Historial, Acciones (compartir, calendario, voz)
  Idioma.kt  idioma de la app (texto, fechas, voz)
app/src/test/  pruebas automáticas (Robolectric)
tools/    aeropuertos.py (genera el CSV), icono/ (SVG maestro del icono)
fastlane/ descripción, icono y notas de cada versión
```

**Compilar:** JDK 17, Android SDK 35 y `./gradlew assembleRelease`. **Pruebas:** `./gradlew testDebugUnitTest`. GitHub Actions pasa las pruebas y compila en cada push (workflow **APK**).

**Textos:** inglés en `res/values/strings.xml` (por defecto) y español en `res/values-es/`. Las pruebas comprueban que los dos tienen las mismas claves.

**Si la app se cierra:** al volver a abrirla muestra el informe con un botón COPIAR. Cada build guarda en Actions el `mapping.txt` para traducirlo.

**Aeropuertos:** `python3 -I tools/aeropuertos.py airports.csv app/src/main/assets/aeropuertos.csv`, con el CSV de ourairports.com.

**Icono:** el maestro es `tools/icono/pardela.svg`, y sus capas vectoriales están en `res/drawable/ic_launcher_*` e `ic_logo_*`. Para regenerar el PNG de la tienda:

```
rsvg-convert -w 512 -h 512 tools/icono/pardela-tienda.svg -o fastlane/metadata/android/es-ES/images/icon.png
```

### Mantenimiento

**Primera puesta en marcha** (solo una vez, con el repo aún privado):
1. Actions → **Generar Gradle wrapper** → Run.
2. Actions → **Crear firma** → Run. Guarda el contenido de los dos `.txt` del artefacto como los secretos `PARDELA_KEYSTORE_B64` y `PARDELA_KEYSTORE_PASS`.
3. Borra esa ejecución y guarda una copia de los dos ficheros en un lugar seguro. Sin la clave no se puede actualizar la app encima de la anterior.
4. Haz público el repo.

**Publicar una versión:**
1. Apunta los cambios en [CHANGELOG.md](CHANGELOG.md).
2. Ejecuta `git tag vX.Y.Z && git push --tags`. La versión sale de la etiqueta (no hay que editar nada más). Actions pasa las pruebas, compila, firma y crea la Release con `pardela.apk`. Obtainium la detecta sola.

Entre versiones, cada build de `main` se llama `X.Y.Z-devN` y se instala encima de la última versión publicada.

## Cómo se ha hecho
Pardela se ha desarrollado con la ayuda de [Claude](https://claude.com/claude-code), un asistente de IA de Anthropic. El diseño, las decisiones y las pruebas son de [cruzpazv](https://github.com/cruzpazv). Los commits en los que ha participado lo indican con la línea `Co-Authored-By`.

## Licencia
[MIT](LICENSE). Algunos iconos son de Material Icons (Google, licencia Apache 2.0). Datos de aeropuertos: OurAirports (dominio público). Las fotos son de sus autores en planespotters.net y los logotipos son marcas de sus aerolíneas.
