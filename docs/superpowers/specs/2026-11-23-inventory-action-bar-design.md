# Barra inferior para Carga Rápida en Inventario — diseño corto

## Intento
El botón «CARGA RÁPIDA / AJUSTE» flota sobre el contenido y tapa lo que hay abajo, especialmente en el estado vacío. El usuario eligió la opción 1: barra inferior fija.

## Objetivos
- Que el botón deje de flotar y no tape ningún contenido.
- Que siga siempre visible y con la misma acción.

## Decisiones
1. Sustituir `floatingActionButton = { ExtendedFloatingActionButton(...) }` por `bottomBar = { ... }` en el `Scaffold` de `InventoryScreen`.
2. La barra será un `Surface` con un `Button` de ancho completo, mismo color amarillo, icono `QrCodeScanner`, texto «CARGA RÁPIDA / AJUSTE» y `Modifier.navigationBarsPadding()`.
3. El `Scaffold` reserva automáticamente el espacio del `bottomBar` en `padding`, así que se elimina el `contentPadding = PaddingValues(bottom = 88.dp)` del `LazyColumn` para no dejar un hueco doble.
4. Se conservan el `testTag("inventory_quick_load_button")` y todos los callbacks (incluido `onOpenQuickLoad` del estado vacío).

## No objetivos
- No cambiar el diálogo `QuickInventoryDialog` ni la lógica de carga rápida.
- No tocar el menú de licencias ni el dashboard superior.

## Verificación
- Test de fuente: `InventoryScreen.kt` contiene `bottomBar = {`, ya no contiene `floatingActionButton = {`, y mantiene el testTag.
- Compilación `assembleDebug`.
- Captura visual en emulador y en MUNBYN: botón abajo, lista/estado vacío completamente visible.

## Estado
Pendiente de aprobación.
