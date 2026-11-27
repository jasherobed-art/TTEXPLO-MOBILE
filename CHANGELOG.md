# Historial de Versiones — TTExplo Mobile

Este archivo registra todas las versiones del proyecto usando **Versionado Semántico (`MAYOR.MENOR.PARCHE`)** sincronizado con `versionCode` y `versionName` en `app/build.gradle.kts` (visible en la pantalla de **Inicio** y en **Ajustes**).

---

## [v2.7.2] — Build 37 (Actual)
### Mejorado (UI, revisión del campo)
- **Piso de legibilidad:** `AutoShrinkText` ya no reduce texto por debajo de 12 sp, para que códigos y etiquetas se lean con poca luz y EPI.
- **Colores de estado:** los mensajes de licencia y exportación usan tokens de texto (`MineErrorText` / `MineSuccessText`) en lugar de colores de relleno, con mejor contraste.
- **Barra superior consistente:** Historial y detalle de vale usan el mismo color que Inicio y Ajustes; ya no hay salto de tono al navegar.

---

## [v2.7.1] — Build 36
### Mejorado
- **Orientación de pantalla controlable:** la app mantiene la pantalla en vertical por defecto para evitar que gire sola durante el trabajo. Un interruptor en Configuración permite desactivar el bloqueo y ver en horizontal cuando se necesite. El cambio se aplica al instante, sin reiniciar.

---

## [v2.7.0] — Build 35
### Mejorado
- **Menús según licencia:** la pantalla de inicio muestra solo los módulos incluidos en la licencia. Los módulos no cubiertos ya no aparecen bloqueados ni con candado. Si una sección se queda sin tarjetas, tampoco se muestra su título.
- **Sincronización generador-app:** el generador emite tokens con `mv: 2` y los perfiles Básico, Operativo y Completo incluyen Historial de vales. Prueba permanente de paridad con tokens reales del generador.
- **Sin licencia de prueba:** se quitó la licencia temporal de 1 día que venía en la APK.

### Técnico
- `HomeScreen` filtra las tarjetas con `LicenseModules.isAllowed`. La guarda de ruta en `MainActivity` sigue como respaldo.
- Nueva prueba `HomeScreenLicenseVisibilityTest` (3 casos) y `LicenseGeneratorParityTest` (4 casos).

---

## [v2.5.0] — Build 20
### Mejorado (Optimización en campo para Munbyn AceScan AS01)
- **Teclado Gboard sin bloqueo de campos (`imePadding` + `navigationBarsPadding`):**
  - Todos los formularios de encabezados (**Despacho**, **Entradas**, **Accesorios**), pantallas de escaneo (**Devoluciones** incluida), **Configuración**, **Licencia** y diálogos de captura ahora desplazan su contenido libremente arriba del teclado Gboard sin ocultar campos ni botones de guardado.
- **UI de Escaneo Compacta para Pantallas Handheld (`CompactScanMetricsRibbon` + `HorizontalProductFilterRow`):**
  - Encabezado colapsable en **Despacho**, **Entradas**, **Accesorios** y **Devoluciones** con franja ultra-compacta de 1 línea (`TOT | C | S | U` + botón de ingreso manual `[⌨]` + botón `[▾ Panel]`).
  - Filtros rápidos por producto en una sola fila horizontal deslizable (`LazyRow`), liberando más del **65%–70%** del alto útil para ver el listado de códigos capturados en vivo.
- **Reimpresión Inmediata al Guardar y desde Historial (`SavedValeReprintDialog` + `ValePrintHelper`):**
  - Al guardar cualquier vale (**Despacho**, **Entradas**, **Accesorios**, **Devolución de Accesorios** y **Devoluciones**), aparece un diálogo de confirmación que permite imprimir 1 o múltiples copias consecutivas y cambiar de impresora en el momento.
  - Botón directo de impresión (`Print`) en cada tarjeta del **Historial de Vales** con selector rápido de impresora térmica para los 5 tipos de vale.
- **Bloque "TOTAL DE MATERIALES" en Fondo Negro con Letras Blancas (ESC/POS y CPCL):**
  - Aplicado en **ESC/POS** mediante comandos nativos `GS B 1` / `GS B 0` con relleno exacto al ancho del papel (`32`, `42` o `56` columnas) y en **CPCL** mediante `INVERSE-LINE` cubriendo el bloque total y desglose de bultos.
  - Nuevos generadores térmicos completos para vales de **Entrada** y **Devolución** en ESC/POS, CPCL y texto plano.
- **Gestión de Múltiples Impresoras Bluetooth en Configuración (`PrinterProfile` + `PrinterProfilesSettingsCard`):**
  - Permite agregar, editar, activar como principal, probar y eliminar múltiples impresoras Bluetooth (guardando alias, MAC, protocolo `CPCL` / `ESC/POS` / `RAW` y ancho `58mm` / `80mm` / `104mm` de forma independiente), con migración automática de la impresora previa y selector rápido al imprimir.
- Nueva suite de pruebas unitarias/Robolectric `PrinterProfilesAndTicketInverseTest` validando modo inverso ESC/POS y CPCL en 58/80/104mm y CRUD/migración multi-impresora.

---

## [v2.4.1] — Build 19
### Mejorado
- **QR de Alta Capacidad + Protocolo Ultra-Compacto V3 (`SyncTabularV3Codec` + `Base45Codec`):**
  - Multiplica entre **3× y 5×** la capacidad por código QR para que un turno operativo completo (30 a 80+ movimientos y series SEDENA) quepa en **1 solo código QR** con un único disparo del lector Zebra 2D.
  - **Sobre Binario Tabular Sin Pérdida (`V3`):** Empaqueta los UUID v4 en binario puro (16 bytes exactos en vez de 36 caracteres hexadecimales), deduplica encabezado de terminal/polvorín y diccionario de productos (`k`), agrupa lotes homogéneos de códigos SEDENA por prefijo común y usa códigos posicionales de 1 carácter preservando el 100% de los datos.
  - **DEFLATE Nivel 9 (`BEST_COMPRESSION`) + Base45 (RFC 9285):** Todos los caracteres del encabezado `TTEX3:<SESIÓN>:<i/N>:<CRC32>:<CARGA>` pertenecen estrictamente al alfabeto **QR Alfanumérico** (`0-9A-Z $%*+-./:`), habilitando compresión óptica nativa de 5.5 bits/carácter en lugar de 8 bits/carácter.
  - **Capacidad por defecto de `1,400` caracteres por QR (Corrección `L` + validación `CRC32`)**, con selector rápido en pantalla entre **Alta capacidad (1,400)** y **Lectura fácil (600)**.
  - **Retrocompatibilidad 100% transparente:** El receptor decodifica automáticamente paquetes **`TTEX3:` (V3)**, **`TTEXSYNC|V2|` (V2)** y archivos **`.JSON` (`v1`/`v2`)**.
- Pruebas automatizadas ampliadas en `InventoryMultiScannerSyncTest` (vectores RFC 9285 Base45, verificación de alfabeto QR Alfanumérico, compuerta de 25+ eventos en 1 solo QR V3 y retrocompatibilidad V2/V3 sin pérdida).

---

## [v2.4.0] — Build 18
### Agregado
- **Centro de Sincronización Multi-Escáner 100% Offline (`InventorySyncScreen` + `InventorySyncViewModel`):**
  - **Protocolo QR Multi-Parte Zebra (`QrSyncCodec` + `QrSyncReassembler` + `QrCodeCanvas`):** Comprime el doble libro mayor de eventos UUID (`events` + `code_events`) con `GZIP + Base64URL + CRC32` en bloques `TTEXSYNC|V2|<SESIÓN>|<i/N>|<CRC32>|<CARGA>`, renderizados con matriz QR ISO de alto contraste (`#000000` sobre `#FFFFFF` con quiet zone de 4 módulos) y carrusel automático/manual (`1.5s` / `2.5s`) para lectura óptica en cualquier orden.
  - **Alcances de envío inteligente (`SyncExportScope`):** `Pendientes nuevos` (cruces rápidos incrementales de turno), `Últimas 24 horas` y `Historial completo` (carga de terminal nueva).
  - **Recepción nativa en vivo con lector Zebra 2D + Importación/Exportación `.JSON`:** Captura automática mediante `ZebraScannerReceiver` con fichas visuales de progreso por bloque (`[✓ 1] [✓ 2] [• 3]`), validación de integridad `CRC32`, respaldo manual/pegado, importación `.JSON` compatible con `v2`/`v1` y simulador integrado **`MODO PRUEBA`** (`ZBR-POLV-02`).
- **Motor de Fusión Idempotente por UUID v4 y Propagación de Calibraciones (`InventoryRepository.importSyncJson`):**
  - Inserción idempotente (`OnConflictStrategy.IGNORE`) en `inventory_events` e `inventory_code_events` con conteo exacto de eventos nuevos vs duplicados omitidos (**cero duplicados** al re-escanear o re-importar).
  - Propaga automáticamente productos nuevos y calibraciones de `pesoUnitarioKg` / `cartuchosPorCaja` entre terminales.
- **Analizador Automático de Conflictos Multi-Escáner (`InventoryConflictAnalyzer`):**
  - Detecta y alerta sin perder datos de campo: **(1) Doble despacho de serie SEDENA** entre terminales (`CRÍTICO`), **(2) Conteos físicos concurrentes** en menos de 24h (`ADVERTENCIA`) y **(3) Saldos negativos resultantes** (`ALERTA`), con salto directo al Kardex del producto y marcado de revisión persistente.
- **Bitácora de Sincronización en Room v5 (`SyncSessionEntity` + `SyncSessionDao` + `MIGRATION_4_5`):**
  - Registro cronológico persistente de cada sesión `EXPORT_QR`, `EXPORT_JSON`, `IMPORT_QR` e `IMPORT_JSON`, más badges e indicadores de estado en vivo en `InventoryScreen`.
- Suite de pruebas automatizadas en `InventoryMultiScannerSyncTest` (codificación/reensamblado QR fuera de orden, rechazo CRC32/sesión mezclada, convergencia idempotente entre 2 escáneres en las 5 familias y detección de los 3 tipos de conflicto).

---

## [v2.3.0] — Build 17
### Agregado
- **Separación estricta en 5 Familias Regulatorias SEDENA (`InventoryCategory`):**
  - **Alto Explosivo (`KG`)**, **Agente Explosivo (`KG`)**, **Cordón Detonante (`MTS`)**, **Cañuelas (`PZA`)** y **Detonadores (`PZA`)** controlados en totales independientes, secciones agrupadas con subtotal y filtros rápidos en la pantalla de **Inventario en Polvorín**.
  - Clasificación automática editable al escanear códigos `X...` nuevos (sugiere familia y unidad por defecto `MTS`/`PZA` según prefijo o descripción, permitiendo al operador modificarla en Carga Rápida o Catálogo).
- **Inventario Individual de Códigos SEDENA para Alto Explosivo y Agente Explosivo (`InventoryCodeEventEntity` + `InventoryCodeEventDao`):**
  - Libro mayor *append-only* con `event_uuid` (UUID v4) y `reverses_event_uuid` que rastrea cada código de barras escaneado (Caja 30d, Saco 18d, Cartucho 16/17d) como **`EN POLVORÍN`** (Entradas / Devoluciones) o **`DESPACHADO`** (Despacho), revirtiendo su estado automáticamente al editar o eliminar vales en Historial.
  - Nueva pestaña **Códigos SEDENA** en el detalle por producto (`ProductKardexSheet`) con contadores rápidos (`En Polvorín`, `Despachados`, `Trazados`), filtro por estado y buscador por serie o folio.
- **Cálculo Automático de Peso Unitario (`BoxUnitWeightCalculator` + `ScanValidator`):**
  - Al escanear cualquier Caja SEDENA de 30 dígitos (`[3 ID][6 Fecha][6 Inicial][6 Final][9 Serie]`), calcula automáticamente `cartuchos = (Final - Inicial) + 1` y `pesoUnitario = pesoCajaKg / cartuchos` (ej. `25 kg ÷ 100 = 0.250 kg`), persistiendo en catálogo (`peso_unitario_kg`, `cartuchos_por_caja`).
- **Módulo de Inventario en Polvorín en Vivo (`InventoryScreen` + `InventoryViewModel` + `QuickInventoryDialog`):**
  - Tablero superior de **5 tarjetas independientes** (tocables para filtrar), tarjetas de producto con **equivalencia visual automática** (`1,003.00 kg · 40 cajas + 12 cart.`, `625.00 kg · 25 sacos`, `350 PZA`, `500.00 MTS`), **Carga Rápida / Ajuste Físico**, **Kardex por producto** y exportación JSON `ttexplo-inventory-events-v2` (incluye `events` y `code_events` con UUIDs) lista para sincronización offline multi-escáner.
- **Afectación automática desde toda la operación diaria:**
  - **Entradas**, **Despacho**, **Devoluciones**, **Salida/Devolución de Accesorios** y **Historial** actualizan automáticamente tanto el libro de cantidades en su familia como el inventario individual de códigos SEDENA.
- **Licencia Temporal de 1 Día (`LicenseManager.ensureOneDayTemporaryLicenseIfNeeded`):**
  - En instalación limpia sin licencia previa, aprovisiona por única vez una clave corta oficial `TEMPORAL` firmada con HMAC-SHA256 válida por 1 día (no auto-renovable al vencer o borrarse).
- Migraciones no destructivas de Room (`MIGRATION_2_3`, `MIGRATION_3_4`) y suite de pruebas unitarias/Robolectric ampliada (`InventoryAndUnitWeightTest`, `LicenseProvisioningTest`).


---

## [v2.2.4] — Build 16
### Ajustado
- **Calibración de firmas acústicas industriales (`IndustrialToneSynthesizer`):**
  - **Válido:** Se mantiene sin cambios (`1760 Hz → 2349 Hz`, ~142 ms).
  - **Duplicado (`HI-LO` estilo Zebra):** Recalibrado a contraste marcado agudo-a-grave (`2400 Hz` → pausa → `550 Hz`, ~335 ms) para distinción inmediata.
  - **Error SEDENA / Inválido (`LOW-LOW`):** Recalibrado a doble pulso grave idéntico y potente (`260 Hz` → pausa → `260 Hz`, ~480 ms).
- **Panel de Audio en Ajustes:** Etiquetas de firma acústica (`AGUDO OK`, `HI-LO`, `LOW-LOW`) en los botones de prueba en vivo.
- Pruebas unitarias ampliadas en `IndustrialToneSynthesizerTest` con verificación de frecuencia por segmento (`HI-LO`, `LOW-LOW` y confirmación ascendente).

---

## [v2.2.3] — Build 15
### Estabilidad y Endurecimiento de Producción
- **Navegación anti-crash (`RouteParamCodec`):** Codificación URL-safe Base64 (`b64~`) en `AppNavigation` y decodificación segura en `MainActivity` para evitar cierres inesperados cuando los encabezados incluyen `%`, `/`, `+`, `?`, `#`, acentos o comillas (por ejemplo `MINA 100%`, `1 1/4"`).
- **Protección contra ráfagas rápidas de escaneo (`Mutex`):** Serialización con `Mutex` en `EntradasViewModel`, `DespachoViewModel`, `AccesoriosViewModel` y `DevolucionViewModel` para eliminar condiciones de carrera y evitar borradores duplicados al disparar el láser Zebra en ráfaga.
- **Protección contra salida accidental (`BackHandler` + `ExitScanConfirmDialog`):** Confirmación al presionar **Atrás** con códigos escaneados en **Entradas**, **Despacho**, **Accesorios** y **Devoluciones** (incluyendo el botón superior de regreso en Devoluciones, advirtiendo si existen lecturas sin guardar).
- **Higiene moderna de Jetpack Compose:** Migración a `collectAsStateWithLifecycle()` en las pantallas activas de escaneo y ajustes, y sustitución del ícono obsoleto `Icons.Default.ArrowBack` por `Icons.AutoMirrored.Filled.ArrowBack`.
- Pruebas unitarias en `RouteParamCodecTest`.

---

## [v2.2.2] — Build 14
### Agregado
- **Sintetizador de tonos industriales PCM 16-bit (`IndustrialToneSynthesizer`)** en `SoundFeedbackManager`, con 3 señales de alto contraste 100% offline:
  - **Válido:** Doble confirmación aguda ascendente (`1760 Hz → 2349 Hz`, ~140 ms) + vibración corta firme.
  - **Duplicado:** Doble pulso medio-grave descendente (`880 Hz → 659 Hz`, ~335 ms) + doble vibración media.
  - **Error SEDENA / Inválido:** Zumbido industrial grave de doble golpe (`320 Hz → 240 Hz`, ~480 ms) + doble vibración intensa.
- **Panel "Audio, Vibración y Alertas Visuales" en Ajustes:** Interruptores independientes para sonido, vibración y destello visual, deslizador de volumen (`10%–100%`), selector de canal (**Multimedia** vs **Alarma / Alta Potencia**) y 3 botones de prueba en vivo (**Válido**, **Duplicado**, **Error**).
- **Destello visual perimetral no bloqueante (`ScanFlashOverlay`)** en verde, ámbar y rojo integrado en **Entradas**, **Despacho**, **Accesorios**, **Devoluciones** y vista previa en **Ajustes**.
- Pruebas unitarias en `IndustrialToneSynthesizerTest`.

---

## [v2.2.1] — Build 13
### Agregado
- **Simulador de Escaneo (`MODO PRUEBA`)** plegable en los 4 módulos de escaneo: **Entradas**, **Despacho**, **Accesorios** y **Devoluciones**.
- Generador determinista `DummyBarcodeGenerator` con códigos SEDENA válidos (Cajas 30 dígitos, Sacos 18 dígitos, Cartuchos 16 dígitos, Accesorios `X...` y lote mixto rápido).
- Botones de prueba para validar candados SEDENA en vivo: **Duplicado**, **Error SEDENA** (coherencia Alto Explosivo vs Saco), **ID Nuevo (999)** y **Error Acc (X)** / **Error sin X**.
- Banner visual de resultado de escaneo y botón **`+ Agregar ID`** al catálogo en vivo dentro de **Devoluciones**.
- Pruebas unitarias en `ScanValidatorTest` validando todas las salidas válidas y negativas del generador dummy.

### Corregido
- Etiqueta de empaque en resumen de Devoluciones para mostrar `Acces.` en lugar de `Cajas` al devolver accesorios.
- Color de mensaje de error en rojo dentro del módulo de **Accesorios** cuando el código no inicia con `X`.

---

## [v2.2.0] — Build 12
### Agregado
- Soporte completo de **claves cortas firmadas criptográficamente (HMAC-SHA256)** en `LicenseManager` (`TTEX-PERP-ALL-XXXXXXXX` y `TTEX-A271122-F020-XXXXXXXX`).
- Botón **Importar archivo `.LIC`** en `LicenseScreen` con extracción automática de token/clave desde JSON.
- Generador web **License Master Pro v2.2** (`/license_generator/index.html`) con soporte offline en `file://` (SHA-256/HMAC-SHA256 en JS puro), exportación `.lic` y código QR.
- Suite de pruebas unitarias ampliada en `LicenseManagerTest`.

### Corregido
- Limpieza automática de espacios, saltos de línea y comillas al pegar licencias.
- Cálculo de días restantes en el último día de vigencia (`getDaysRemaining`).
- Eliminación de archivos prototipo obsoletos bajo `com/example/`.

---

## [v2.1.9] — Build 11 (Base original)
### Base migrada
- Migración completa de **TTExplo Mobile v2.1.9** (32 archivos Kotlin, 6 suites de pruebas unitarias, catálogo base `catalogo_productos.csv`, Room SQLite, Zebra DataWedge, impresión térmica Bluetooth CPCL/ESC-POS/PLAIN y licenciamiento offline).
- Configuración de `applicationId` único (`com.aistudio.ttexplo.mxbvqr`) e ícono adaptativo personalizado.
