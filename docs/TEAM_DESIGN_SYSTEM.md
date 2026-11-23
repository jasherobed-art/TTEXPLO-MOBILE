# Design System — TTExplo Mobile v2.5.0

Sistema único de diseño para la app de campo (polvorín / mina). Se conecta en
`app/src/main/java/com/ttexplo/mobile/ui/theme/` y sustituye el scaffold muerto
`com/example/ui/theme/**`, que se conserva solo como referencia histórica.

## Tokens

### Color (ui/theme/Color.kt)
| Uso | Token | Valor |
|---|---|---|
| Fondo / superficie | `MineDarkBackground` / `MineDarkSurface` / `MineDarkSurfaceElevated` | `#121212` / `#1E1E1E` / `#2C2C2C` |
| Acento de seguridad | `MineSafetyYellow` / `MineSafetyYellowDark` / `MineSafetyAmber` | `#FFB300` / `#C68400` / `#FF8F00` |
| Texto | `MineTextPrimary` / `MineTextSecondary` / `MineTextMuted` | `#FFFFFF` / `#B0B0B0` / `#9E9E9E` |
| Bordes | `MineOutline` (4,83:1) / `MineBorderGray` | `#8A8A8A` / `#383838` |
| Estado (texto AA) | `MineSuccessText` / `MineErrorText` / `MineInfoText` | `#81C784` / `#E57373` / `#64B5F6` |
| Estado (fill) | `MineSuccessGreen` / `MineErrorRed` / `MineAgentBlue` | `#2E7D32` / `#D32F2F` / `#1976D2` |

Los colores Fill no se usan para texto; los tokens `Mine*Text` son los únicos
aprobados para texto de estado (contraste AA sobre superficies oscuras, Fase 3).
Los tokens de empaque (`EmpaqueCajaColor` `#FFD600`, `EmpaqueSacoColor`
`#00E5FF`, `EmpaqueCartuchoColor` `#FF916F`, `EmpaqueAccesorioColor` `#BA68C8`,
`EmpaqueTotalColor` `#FFFFFF`) son códigos de producto y no deben reutilizarse
para estado.

### Forma (ui/theme/Theme.kt)
`Shapes`: extraSmall 4 dp · small 6 dp · medium 8 dp · large 10 dp · extraLarge 12 dp.
Los botones y diálogos de escaneo usan `RoundedCornerShape(8.dp)` / `12.dp`.

### Tipografía (ui/theme/Type.kt)
Familias reales: `SpaceGrotesk` (display/headline/titleLarge/labelLarge),
`IbmPlexSans` (titleMedium/titleSmall/body/labelMedium), `JetBrainsMono` (labelSmall).

| Rol | Family | sp | line-height sp |
|---|---|---|---|
| displayLarge | SpaceGrotesk Bold | 48 | 56 |
| displayMedium | SpaceGrotesk Bold | 36 | 44 |
| displaySmall | SpaceGrotesk Bold | 28 | 32 |
| headlineMedium | SpaceGrotesk SemiBold | 24 | 32 |
| headlineSmall | SpaceGrotesk Bold | 18 | 22 |
| titleLarge | SpaceGrotesk SemiBold | 20 | 28 |
| titleMedium | IbmPlexSans SemiBold | 15 | 20 |
| titleSmall | IbmPlexSans SemiBold | 13 | 18 |
| bodyLarge | IbmPlexSans Normal | 16 | 24 |
| bodyMedium | IbmPlexSans Normal | 14 | 20 |
| bodySmall | IbmPlexSans Normal | 11 | 15 |
| labelLarge | SpaceGrotesk Medium | 14 | 20 |
| labelMedium | IbmPlexSans SemiBold | 11 | 14 |
| labelSmall | JetBrainsMono Medium | 11 | 13 |

**Regla:** ningún rol baja de 11 sp. El scaffold traía `labelSmall` a 10 sp y se
subió a 11 sp al conectarlo. Si el texto se corta, se agranda el contenedor,
nunca se reduce la tipografía.

## Especificaciones de 6 componentes

1. **`TTExploTheme`** (`ui/theme/Theme.kt`). Único `MaterialTheme` de la app:
   `MineDarkColorScheme` + `Typography` + `MineShapes`. Fuerza status bar oscura
   y `Surface` con `MineDarkBackground`. Ninguna pantalla crea su propio
   `MaterialTheme`.

2. **`AutoShrinkText`** (`ui/common/AutoShrinkText.kt`). Texto que reduce su
   tamaño solo entre `targetTextSize` y `minTextSize` (mínimo 9–10 sp en
   botones compactos heredados). Para texto nuevo usar los roles de arriba;
   `minTextSize` nunca debe quedar por debajo de 11 sp en componentes nuevos.

3. **`CompactScanMetricsRibbon` / `HorizontalProductFilterRow`**
   (`ui/common/CompactScanComponents.kt`). Panel de métricas y filtros de las 4
   pantallas de escaneo. Chips: `Alto Exp.` `#F57C00`, `Agente` `#64B5F6`,
   `Accesorios` `#BA68C8`. El chip «Agente» usa `MineInfoText` para no colisionar
   con «Saco». Alto de fila de escaneo 48 dp (touch target).

4. **`ExitScanConfirmDialog`** (`ui/common/ExitScanConfirmDialog.kt`). Al salir
   con lecturas activas. Título, cuerpo y botones desde `strings.xml`
   (`scan_exit_*`). Confirmación con `MineSafetyYellow` si hay borrador,
   `MineErrorRed` si no lo hay; dismiss «Seguir escaneando».

5. **`TwoStepDeleteDialog`** (`ui/common/TwoStepDeleteDialog.kt`). Borrado en
   dos pasos para códigos escaneados. Cadenas en `strings.xml` (`delete_*`).
   Paso 1 amarillo / paso 2 rojo; el botón final exige el segundo clic. Nunca
   se puede eliminar en un solo toque.

6. **`PrinterDialogs`** (`ui/common/PrinterDialogs.kt`). Selector rápido de
   impresora y diálogos de impresión. Solo usa `bondedDevices` +
   `BLUETOOTH_CONNECT`; `BLUETOOTH_SCAN` se mantiene en el manifiesto porque el
   código vivo lo solicita y `cancelDiscovery()` lo requiere en Android 12+.

Los textos de botones de escaneo y de los diálogos de salida/borrado viven en
`app/src/main/res/values/strings.xml`; no se añaden literales nuevos en esos
componentes. Deuda pendiente: migrar los diálogos de impresora
(`PrinterDialogs.kt`) y los textos de catálogo, fuera del alcance de esta fase.

## Growth: propuesta, no implementación

El diseño propuso onboarding de 3 pasos, MODO PRUEBA, cierre de turno y tres
experimentos (demo de campo, partners de hardware, contenido técnico). Queda
**como propuesta de producto, no implementada** en esta entrega: esta fase es de
limpieza de deuda y conexión del design system, no de nuevas funcionalidades.
