# Bóveda de licencias + ocultación de modos de prueba — diseño corto

## Intento
El humano genera licencias con **License Master Pro** (fuera de la app), **una por terminal** (atadas al Device ID de esa terminal), y necesita una herramienta dentro de la APK para **guardarlas, llevarlas entre terminales e instalarlas en la terminal correcta**. Además, la APK se entrega con una licencia temporal de 1 día («modo prueba»); al instalar una licencia real deben desaparecer los paneles de prueba.

## Objetivos
- Guardar varias licencias (token largo `TTEX:`, clave corta `TTEX-`, `.lic`, QR) con alias, tipo, vigencia y terminal destino.
- Exportar/importar la bóveda para licenciar terminales distintas.
- Instalar una licencia guardada en la terminal actual con un toque.
- Ocultar los modos de prueba en cuanto se instale una licencia real.

## No objetivos
- **No generar licencias dentro de la app** (decisión del humano). `MASTER_SECRET` no se expone en la UI.
- No cambiar el formato criptográfico ni el `LicenseManager` verification.
- No modificar la pantalla de licencias existente más allo de lo necesario.
- No tocar QR/sync, impresión ni licencias del_build_ (la licencia temporal de prueba se mantiene para la primera instalación).

## Restricciones
- `LicenseManager.saveLicense()` ya valida y guarda la licencia activa; la bóveda es un almacén adicional, no sustituye ese flujo.
- La licencia temporal de fábrica se escribe directo en SharedPreferences desde `ensureOneDayTemporaryLicenseIfNeeded()`; no pasa por `saveLicense()`.
- **Cada licencia se genera para un Device ID concreto.** La bóveda es un maletín para varias terminales: muestra la terminal destino de cada tarjeta y si coincide con la actual. La validación real (firma + Device ID) la hace `verifyToken` / `saveLicense`.
- Sin dependencias nuevas; AndroidX + Compose + SAF ya disponibles.

## Decisiones del diseñador (delegadas por el humano)
1. **Punto de entrada:** menú de tres puntos en la `TopAppBar` de `InventoryScreen` (opción «Licencias»). El FAB de CARGA RÁPIDA / AJUSTE queda intacto.
2. **Almacén:** archivo privado `.json` en `filesDir` (`license_vault.json`), con una lista de entradas `{id, alias, token, createdAt}`. El tipo/vigencia/terminal se derivan con `verifyToken`, no se duplican.
3. **Portabilidad:** exportar/importar ese JSON con Android Storage Access Creator (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`). No se pide permiso de almacenamiento. También se puede copiar/compartir un token suelto.
4. **Instalar:** botón por tarjeta llama a `LicenseManager.saveLicense(context, token)`. Si la licencia fue generada para otro Device ID, la tarjeta se marca **«OTRA TERMINAL»** y `saveLicense` devuelve el error real (`DEVICE_MISMATCH`). La cabecera incluye **«Copiar ID de esta terminal»** para pedir esa licencia en License Master Pro.
5. **Modos de prueba:** se ocultan cuando se ha instalado una licencia real. Se añade un flag `KEY_REAL_LICENSE_INSTALLED` que `saveLicense()` marca al guardar (la licencia temporal de fábrica no lo marca). `isDemoModeAllowed()` devuelve `false` cuando el flag está activo.
6. **Paneles afectados:** 3 `DummyExplosivesScannerBar` (Entradas, Despacho, Devolución), 1 `DummyAccessoriesScannerBar` (Accesorios), 1 panel de simulador en `InventorySyncReceiveAndConflictsTabs`, 1 botón «Prueba» de `QuickInventoryDialog`. Los cinco se envuelven en la comprobación.
7. Si la licencia real vence o se borra, los modos de prueba **no reaparecen** (el flag es persistente). Si el humano quiere lo contrario, se cambia en una revisión.

## Enfoques considerados
1. Botón flotante junto al de Carga Rápida — descartado: es lo que estorba.
2. **Menú overflow de Inventario → pantalla de bóveda** — elegido: visible pero fuera del flujo operativo.
3.工具 independiente fuera de la app — descartado: el humano quiere licenciar desde la terminal.

## Pantalla (esquema)
```
Licencias guardadas                     [⋮]
Device actual: F020-F600-...   [+ Agregar licencia]

[Vigente • ANUAL • vence 2027-05-12 • F020-F600]  ESTA TERMINAL
 Alias: Polvorín Norte
 [Instalar] [Copiar] [Compartir] [Eliminar]

[Vigente • ANUAL • vence 2027-03-01 • A71C-9902]  OTRA TERMINAL
 Alias: Polvorín Sur
 [Instalar] [Copiar] [Compartir] [Eliminar]

[Exportar bóveda] [Importar bóveda]
```

## Verificación prevista
- Tests JVM: round-trip guardar/listar/borrar/exportar/importar; `saveLicense()` marca el flag; `isDemoModeAllowed()` cambia con el flag.
- Verificación en emulador: abrir desde el menú de Inventario, agregar una licencia de prueba válida, instalarla, confirmar que desaparecen los 5 paneles de prueba, exportar/importar el JSON.

## Estado
 pendiente de aprobación del humano.
