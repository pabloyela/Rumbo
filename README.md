# Rumbo: diagnóstico y reinicio del GPS para el radio del carro

App pequeña e independiente de Carola. Sirve para tres cosas:

1. **Ver qué le pasa al GPS**: satélites visibles y en uso, fuerza de la señal, última posición y si el reloj del radio está desfasado.
2. **Reiniciar el GPS con un botón**, sin reiniciar el radio. Borra los datos de asistencia y obliga al chip a buscar satélites desde cero.
3. **Reinicio automático**: un vigilante en segundo plano que, si el GPS pasa cierto tiempo sin posición, lo reinicia solo. Anota en un registro cuándo se pierde la señal y si se recuperó sola o con el reinicio.

Si el radio tiene root, aparece además **Reinicio profundo**. Ese botón apaga la ubicación, detiene el servicio del chip GPS (Android lo vuelve a levantar) y la enciende de nuevo.

---

## 1. Compilar con GitHub (igual que Carola)

1. En GitHub: **+ → New repository**, nombre `rumbo`, **Private** → **Create repository**.
2. Toca **uploading an existing file**, descomprime `Rumbo.zip` y arrastra **todo el contenido** de la carpeta `Rumbo`, incluida la carpeta oculta `.github`. Presiona **Commit changes**.
   - Si la carpeta `.github` no se sube y Actions no arranca: usa **Actions → set up a workflow yourself**, nombra el archivo `build.yml`, pega el contenido de `.github/workflows/build.yml` y haz commit.
3. En **Actions** espera la palomita verde (5–8 min). En **Artifacts** descarga **Rumbo-apk** → adentro está `app-debug.apk`.

Si sale en rojo, copia las líneas con `e:` o `error:` y pégaselas a Claude.

## 2. Instalar

Copia `app-debug.apk` a una USB e instálalo desde el explorador de archivos del radio, igual que hiciste con Carola. Al abrirla, acepta el permiso de **ubicación**.

## 3. Uso

- **Reiniciar GPS**: tócalo cuando se pierda la señal. Un arranque en frío tarda de 30 s a 2 min en volver a fijar posición, menos si el carro está a cielo abierto.
- **Reinicio automático**: déjalo encendido. Aparece una notificación fija "Rumbo vigilando el GPS". Si el radio manda el aviso de encendido, se reactiva solo al arrancar el carro.
- **Reiniciar tras N s sin señal**: cada toque cambia el tiempo de espera (30, 60, 90, 120, 180 s). Si el reinicio no ayuda, la app espera más entre intentos (2, 4, 8 y 10 min) para no ciclar.

## 4. Cómo leer el diagnóstico (lo más útil)

Cuando se le vaya la señal, abre Rumbo y mira la tarjeta de satélites:

| Lo que ves | Qué significa | Qué hacer |
|---|---|---|
| **0 visibles** | El chip no recibe nada: antena, cable o el servicio del GPS se trabó | Revisar antena y conector; probar *Reinicio profundo* o reiniciar el radio |
| Varios visibles con **señal buena** pero **0 en uso** | Problema de software o de datos de asistencia | **Reiniciar GPS** debería resolverlo |
| Visibles pero con **señal débil** (menos de 20 dB) | Antena mal ubicada o interferencia | Ver consejos abajo |
| Aviso de **reloj desfasado** | Hora o fecha del radio incorrecta, cosa que al GPS le cuesta mucho | Activar hora automática en Ajustes |

El **Registro** dice si la señal se recupera sola o solo con el reinicio. Con unos días de uso sabrás si es antena o software.

## 5. Causas comunes en radios chinos (no son de software)

- **Antena GPS**: casi siempre es un cubito externo pegado detrás del tablero. Debe quedar con vista al cielo, por ejemplo bajo la parte alta del tablero cerca del parabrisas, y no debajo de metal. Revisa que el conector esté bien apretado en el radio. Las antenas que traen estos radios son muy baratas y fallan seguido; reemplazarla cuesta poco.
- **Interferencia**: cargadores USB baratos, cámaras de tablero y algunos LED meten ruido justo en la frecuencia del GPS. Prueba desconectarlos un rato y observa si cambia la señal.
- **Parabrisas con película metalizada o polarizado atérmico**: bloquea bastante la señal.
- **Fecha u hora mal** al encender, porque el radio pierde la hora al apagar el carro.
