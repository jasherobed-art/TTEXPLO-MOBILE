# Diseño: líneas de firma en el ticket

**Estado:** aprobado por el usuario el 2026-11-24.

## Intento

El operador quiere que, en el vale impreso, los bloques **ENTREGO:** y **RECIBIO:** muestren solo la etiqueta y la línea para firmar a mano, sin el nombre capturado ni leyendas como "Firma Responsable Polvorin". Además, la línea debe ocupar el ancho útil de los tres papeles soportados (58, 80 y 104 mm).

## Lo que ya existe

- `SettingsRepository.getPrintCodeDetails()` / `setPrintCodeDetails()` ya exponen el interruptor **"Listado de códigos con fecha/hora"** en `ui/screens/settings/PrinterProfilesSettingsCard.kt`, bajo **CONTENIDO DEL TICKET TÉRMICO**. Ya gobierna los cuatro vales en CPCL, ESC/POS y texto plano. No se duplica ni se mueve.
- `TicketGenerator.kt` tiene 12 bloques de firmas: 4 vales (Entradas, Despacho, Accesorios, Devolución) × 3 protocolos. En ESC/POS y texto plano la línea es `"_".repeat((w - 4).coerceAtMost(27))`; en CPCL es un comando `LINE` cuyo extremo se limita a 350 dots. Ambos topes recortan la línea en papel de 80 y 104 mm.

## Decisiones

- En ESC/POS y texto plano: una sola expresión compartida para la línea, de longitud `paperWidth.lineLength - 4` (28 / 38 / 52 caracteres). Se eliminan las líneas de texto impresas debajo de los guiones. Queda `ENTREGO:`, guiones, `RECIBIO:`, guiones.
- En CPCL: el extremo de la línea de firma pasa a ser `lineEnd` (364 / 556 / 812 dots, igual que el separador). Se eliminan los comandos `TEXT` debajo de la línea.
- El encabezado del vale conserva sus datos (vale, empresa, proveedor, transportista, fecha). Solo cambia el bloque de firmas.
- El nombre sigue guardándose en la base de datos y en la exportación; solo deja de imprimirse en ese bloque.
- La opción de listado de códigos queda como está.
- El manual se ajusta en dos líneas para no describir un vale que ya no se imprime así.

## Alcance

Un archivo de código (`printer/TicketGenerator.kt`), un archivo de prueba (`TicketGeneratorTest.kt`) y el manual (`docs/MANUAL_USUARIO.md`). Sin cambios en ViewModels, BD, configuración ni firma de licencias.

## No objetivos

- No se agrega una opción nueva para elegir quién firma ni se cambia el interruptor existente.
- No se modifica el contenido del listado de códigos ni los totales.
- No se cambia el nombre de los campos en la pantalla de captura.
