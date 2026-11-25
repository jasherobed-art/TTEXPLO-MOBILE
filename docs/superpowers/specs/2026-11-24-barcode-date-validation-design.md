# Validación y corrección de fecha en códigos escaneados

## Intención

Un código real recibido hoy, `298190896004876376` (saco de 18 dígitos del ID 298), traía
`19/08/96` en el bloque de fecha. El validador actual solo revisa que el día esté entre 1 y
31 y el mes entre 1 y 12, así que lo aceptó. El operador necesita que una fecha imposible o
fuera de rango se detecte antes de dar el alta, y poder teclear la fecha correcta sin
reescribir los 18 dígitos.

## Objetivos

- Rechazar fechas con año fuera de 2020-2030, día inexistente para ese mes/año, o mes 00.
- Mostrar un aviso con la fecha detectada y permitir escribir la fecha correcta en el momento.
- Guardar y capturar el código ya corregido, para que el vale y la próxima lectura sean
  coherentes.

## No objetivos

- No cambiar el formato del vale ni agregar una leyenda de "fecha corregida" en el ticket.
- No tocar catálogo, exportación CSV ni la estructura de la base de datos.
- No corregir otros campos del código (ID, serie, rango de cartuchos).
- No agregar capturas nuevas al manual; solo actualizar el texto de la sección de captura.

## Restricciones

- El proyecto es Kotlin/Compose con ViewModel separado en Entradas y Despacho; Devolución
  valida directamente en la pantalla. Las tres comparten `ScanValidator` y
  `ScanResult`.
- Las tres pantallas ya tienen un flag `anyDialogOpen` que inhibe el gatillo del lector; el
  diálogo nuevo debe sumarse a ese flag para no apilar lecturas mientras está abierto.
- El APK pasa a `2.6.0 / Build 22` (sube solo `versionCode`, por petición del operador).
- Idioma del manual: español de México, con `IMPORTANTE:` (no `IMPORTANT`).

## Enfoques considerados

1. **Diálogo de corrección (elegido):** al detectar la fecha mala se detiene el alta, se
   muestra la fecha detectada y un campo para teclear 6 dígitos `DDMMYY`; la app sustituye
   solo esos 6 dígitos en el código y vuelve a procesarlo como una lectura normal. Lo eligió
   el operador porque evita reescribir el código completo y deja el registro correcto.
2. **Aviso rojo y relectura:** rechazar con mensaje claro y que el operador escanee o teclee
   el código completo ya corregido. Es el cambio más pequeño, pero le transfiere todo el
   trabajo manual y es propenso a capturar el código viejo otra vez.
3. **Aceptar y guardar la fecha aparte:** conservar el código original y anotar la fecha
   corregida en registro y vale. Requiere tocar entidad, exportación y generador de ticket,
   y el código original seguiría fallando en la próxima lectura.

## Diseño

### Dominio (`ScanValidator.kt`)

- `isValidPlausibleDate` pasa a interpretar los 6 dígitos siempre como `DDMMYY`: mes `01..12`
  y día válido para ese mes y año (`LocalDate.of(2000 + yy, mes, día)`), con `yy` en `20..30`.
  `29/02/24` pasa; `29/02/25`, `31/02/26`, `00/08/26` y `19/08/96` se rechazan.
  Se elimina la aceptación alternativa `YYMMDD`, que era ambigua con los ejemplos reales
  (`020926`, `120826`, `150826` son claramente `DDMMYY`).
- El rechazo conserva `isValid = false` y `message` indicando la fecha detectada, y además
  llena un campo nuevo `invalidDate: String?` en `ScanResult` con los 6 dígitos tal cual
  vinieron (`"190896"`). Los demás rechazos dejan ese campo en `null`.
- Se agrega una función pura en el validador para reconstruir el código:
  `correctBarcodeDate(rawBarcode, correctedDate6): String?`. Sustituye los 6 dígitos en la
  posición 4 (cajas de 30 y sacos de 18) o en la posición 5 (cartuchos de 16), y devuelve
  `null` si la fecha corregida no es válida o la longitud no corresponde. Los cartuchos de
  17 dígitos siguen sin validación de fecha, como hoy, porque su distribución interna no
  está confirmada.

### Interfaz

- Composable nuevo en `ui/common`: `DateCorrectionDialog(detectedDate, onConfirm, onDismiss)`.
  Muestra `Detectada: 19/08/96`, un campo de 6 dígitos con placeholder `DDMMYY`, el mensaje
  `Debe ser entre 2020 y 2030 y un día válido.`, y botones `Corregir` / `Cancelar`. El botón
  `Corregir` valida la fecha antes de habilitarse y devuelve los 6 dígitos.
- Se conecta en las tres pantallas que generan vales: `EntradasScanScreen`,
  `DespachoScanScreen` y `DevoluciónScreen`. Al aparecer un `latestScanResult` con
  `invalidDate != null`, se abre el diálogo. `Corregir` llama a `correctBarcodeDate` con el
  código original del resultado y vuelve a llamar al método de captura existente
  (`onBarcodeScanned` / `onScanned`) con el código corregido; `Cancelar` solo cierra y deja
  el aviso en el banner.
- La lectura de muestra de `Inventario` (`QuickInventoryDialog`) queda fuera: seguirá
  mostrando el mensaje de rechazo y el operador puede teclear el código corregido en ese
  mismo diálogo. Meter un segundo diálogo dentro del modal de carga rápida agregaría
  complejidad a un flujo que no genera vale. Si se quiere también ahí, se agrega en una
  segunda pasada.

### Pruebas

En `ScanValidatorTest`: el código reportado se rechaza con `invalidDate = "190896"`; la
corrección a `190826` produce `298190826004876376` y se acepta; `31/02/26` y `19/08/96` se
rechazan; `29/02/24` se acepta y `29/02/25` se rechaza; un cartucho de 17 dígitos sigue
pasando como hoy. Se verifica también que la corrección no altera el prefijo ni la serie.

### Documentación y entrega

- Actualizar `docs/MANUAL_USUARIO.md` en la sección de captura: qué significa el aviso de
  fecha, cómo teclear los 6 dígitos y que el código corregido es el que se guarda.
- Regenerar HTML y PDF con el procedimiento ya usado.
- Correr las pruebas del validador y la suite, commitear y pushear a `main`, reconstruir el
  APK como `app2.6.0-ttexplo.apk` con `versionCode = 22` e instalarlo en la terminal
  `b7411c57`.

## Lo que queda al implementador

La distribución exacta del diálogo y el color de los botones se toman de los `AlertDialog`
existentes en `ui/common`; no son decisiones del operador.
