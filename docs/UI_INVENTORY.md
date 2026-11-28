# Inventario de Unidades de Interfaz (UE) — T&T Explo Mobile

Índice estable de las pantallas y componentes de UI. Sirve para referirse a una
pantalla con un ID corto y saber en qué build se tocó por última vez, sin tener
que leer el proyecto completo.

**Convención para referirse a una UE:** `UE-07 · v22` significa la unidad 7
(`Accesorios/Escaneo`), cuyo último cambio registrado fue en el build 22.
Cuando pidas trabajar en una UE, di el ID y el build; el agente va directo al
archivo indicado.

**Versión del índice:** build 38 (v2.8.0).
La columna **Build** es el `versionCode` vigente en el último commit que
modificó ese archivo (obtenido de `git log` + `git show`).

---

## Navegación y tema (base)

| ID | Elemento | Archivo | Composable principal | Build |
|----|----------|---------|----------------------|-------|
| UE-00 | Navegación / rutas | `ui/navigation/AppNavigation.kt` | `Screen`, `RouteParamCodec`, `requiredLicenseModule` | — |
| UE-00b | Tema | `ui/theme/Theme.kt`, `Color.kt`, `Type.kt` | `TTExploTheme` | — |

## Módulos operativos

| ID | Nombre | Archivo | Composable principal | Build |
|----|--------|---------|----------------------|-------|
| UE-01 | Inicio | `ui/screens/home/HomeScreen.kt` | `HomeScreen`, `MenuActionCard` | 38 |
| UE-02 | Entradas · Encabezado | `ui/screens/entradas/EntradasHeaderScreen.kt` | `EntradasHeaderScreen` | 20 |
| UE-03 | Entradas · Escaneo | `ui/screens/entradas/EntradasScanScreen.kt` | `EntradasScanScreen`, `CompactMetric`, `EntradaScannedItemCard` | 22 |
| UE-04 | Salidas · Encabezado | `ui/screens/despacho/DespachoHeaderScreen.kt` | `DespachoHeaderScreen` | 20 |
| UE-05 | Salidas · Escaneo | `ui/screens/despacho/DespachoScanScreen.kt` | `DespachoScanScreen`, `CompactDespachoMetric`, `DespachoScannedItemRow` | 22 |
| UE-06 | Accesorios · Encabezado | `ui/screens/accesorios/AccesoriosHeaderScreen.kt` | `AccesoriosHeaderScreen` | 20 |
| UE-07 | Accesorios · Escaneo | `ui/screens/accesorios/AccesoriosScanScreen.kt` | `AccesoriosScanScreen`, `AccessoryRowCard` | 21 |
| UE-08 | Devolución de alto/agente | `ui/screens/devolucion/DevolucionScreen.kt` | `DevolucionScreen` | 22 |
| UE-09 | Catálogo de productos | `ui/screens/catalog/CatalogManagementScreen.kt` | `CatalogManagementScreen`, `ProductCardItem` | 19 |

## Inventario y sincronización

| ID | Nombre | Archivo | Composable principal | Build |
|----|--------|---------|----------------------|-------|
| UE-10 | Inventario en polvorín | `ui/screens/inventory/InventoryScreen.kt` | `InventoryScreen`, `FiveFamilyTotalsDashboard`, `FamilyKpiCard`, `InventoryProductCard`, `InventoryEmptyState` | 21 |
| UE-11 | Conteo en caja | `ui/screens/inventory/BoxCountScreen.kt` | `BoxCountScreen`, `BoxCountMetric` | 31 |
| UE-12 | Carga rápida / ajuste | `ui/screens/inventory/QuickInventoryDialog.kt` | `QuickInventoryDialog` | 20 |
| UE-13 | Kardex y códigos | `ui/screens/inventory/ProductKardexSheet.kt` | `ProductKardexSheet`, `SedenaCodeRow`, `KardexEntryRow` | 21 |
| UE-14 | Lienzo de QR | `ui/screens/inventory/QrCodeCanvas.kt` | `QrCodeCanvas` | 19 |
| UE-15 | Centro de sincronización | `ui/screens/inventory/InventorySyncScreen.kt` | `InventorySyncScreen`, `SendSyncTab` | 20 |
| UE-16 | Recepción y conflictos | `ui/screens/inventory/InventorySyncReceiveAndConflictsTabs.kt` | `ReceiveSyncTab`, `SyncMergeReportCard`, `ConflictsAndHistoryTab`, `ConflictAlertCard`, `SyncSessionHistoryCard` | 20 |

## Historial

| ID | Nombre | Archivo | Composable principal | Build |
|----|--------|---------|----------------------|-------|
| UE-17 | Historial de vales | `ui/screens/history/ValesHistoryScreen.kt` | `ValesHistoryScreen` | 37 |
| UE-18 | Tarjeta de vale | `ui/screens/history/ValeHistoryCard.kt` | `ValeHistoryCard`, `HistoryTypeFilterRow`, `ValeCardActionCell` | 27 |
| UE-19 | Detalle de vale | `ui/screens/history/ValeHistoryDetailView.kt` | `ValeHistoryDetailView`, `ValeDetailCodeRow` | 37 |

## Licencia

| ID | Nombre | Archivo | Composable principal | Build |
|----|--------|---------|----------------------|-------|
| UE-20 | Activación de licencia | `ui/screens/license/LicenseScreen.kt` | `LicenseScreen` | 37 |
| UE-21 | Bóveda de licencias | `ui/screens/license/LicenseVaultScreen.kt` | `LicenseVaultScreen` | 20 |

## Configuración

| ID | Nombre | Archivo | Composable principal | Build |
|----|--------|---------|----------------------|-------|
| UE-22 | Configuración y Sistema | `ui/screens/settings/SettingsScreen.kt` | `SettingsScreen`, `CollapsibleSettingsSection` | 38 |
| UE-23 | Audio y alertas | `ui/screens/settings/AudioSettingsCard.kt` | `AudioSettingsCard` | 19 |
| UE-24 | Perfiles de impresora | `ui/screens/settings/PrinterProfilesSettingsCard.kt` | `PrinterProfilesSettingsCard`, `PrinterProfileEditorDialog` | 20 |
| UE-25 | Centro de descarga (WiFi) | `ui/screens/download/DownloadCenterScreen.kt` + `net/DownloadServer.kt` + `domain/download/DownloadData.kt` | `DownloadCenterScreen`, `DownloadServer`, `DownloadData` | 38 |

## Componentes compartidos (no son pantallas)

| Elemento | Archivo |
|----------|---------|
| Texto auto-escalable | `ui/common/AutoShrinkText.kt` |
| Componentes de escaneo compacto | `ui/common/CompactScanComponents.kt` |
| Overlay de destello | `ui/common/ScanFlashOverlay.kt` |
| Sonido / vibración / destello | `ui/common/SoundFeedbackManager.kt` |
| Simulador de escáner | `ui/common/DummyScannerSimulator.kt` |
| Diálogos reutilizables | `ui/common/TwoStepDeleteDialog.kt`, `ProductEditDialog.kt`, `PrinterDialogs.kt`, `ExitScanConfirmDialog.kt`, `DateCorrectionDialog.kt` |

## ViewModels

| Vista | ViewModel |
|-------|-----------|
| Entradas | `ui/screens/entradas/EntradasViewModel.kt` |
| Despacho | `ui/screens/despacho/DespachoViewModel.kt` |
| Accesorios | `ui/screens/accesorios/AccesoriosViewModel.kt` |
| Inventario | `ui/screens/inventory/InventoryViewModel.kt` |
| Sincronización | `ui/screens/inventory/InventorySyncViewModel.kt` |
| Configuración | `ui/screens/settings/SettingsScreen.kt` (`SettingsViewModel`) |
| Devolución | `ui/screens/devolucion/DevolucionScreen.kt` (`DevolucionViewModel`) |

## Reglas de uso

1. El agente lee **solo** el archivo de la UE indicada más sus dependencias
   directas (ViewModel, componentes comunes, tema). No relee el proyecto.
2. Al modificar una UE, se actualiza su columna **Build** al build nuevo y se
   incrementa `versionCode`/`versionName` del app.
3. Este índice se sincroniza en las tres ubicaciones (local, GitHub, NAS) como
   parte del flujo de versionado habitual.
4. Referencia corta: «UE-22 · v37» = `SettingsScreen.kt`, último cambio build 37.
