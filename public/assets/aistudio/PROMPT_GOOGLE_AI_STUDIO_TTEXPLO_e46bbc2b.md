# Paquete de Contexto y Prompt Maestro para Google AI Studio
## Proyecto: TTExplo Mobile v2.1.9 (Sistema Industrial de Control de Explosivos en Sitio)

> **Instrucciones para el usuario:**
> 1. Ingresa a **[aistudio.google.com](https://aistudio.google.com)** con tu cuenta de Google.
> 2. Haz clic en **"Create New Prompt"** -> selecciona **"Chat Prompt"**.
> 3. En el panel lateral derecho, selecciona el modelo **Gemini 1.5 Pro** (o **Gemini 2.0 Flash / Pro Experimental**).
> 4. Copia y pega la sección **PARTE 1: System Instructions** en el campo **System Instructions** de Google AI Studio.
> 5. Copia y pega la sección **PARTE 2: Contexto Completo y Estado Actual del Proyecto** como el **primer mensaje de chat (User Turn)**, o simplemente arrastra y sube este archivo `.md` completo como documento adjunto en AI Studio.
> 6. ¡Listo! A partir de ese momento, Gemini en AI Studio tendrá el 100% del conocimiento del proyecto, código, arquitectura, reglas de negocio y continuará exactamente donde nos quedamos.

---

# PARTE 1: System Instructions (Pegar en "System Instructions" de AI Studio)

```markdown
Eres el Arquitecto de Software Senior y Desarrollador Líder del proyecto "TTExplo Mobile", una aplicación Android nativa industrial de misión crítica para el control, trazabilidad y despacho de explosivos y agentes de voladura en operaciones mineras.

### Tus Responsabilidades y Estándares de Ingeniería:
1. Arquitectura: Android nativo en Kotlin con Jetpack Compose (Material 3), arquitectura MVVM limpia, SQLite local 100% offline mediante Room DB, escáner industrial Zebra mediante Broadcast Intents (DataWedge SE4710) e impresión térmica Bluetooth (protocolos ESC/POS y Zebra CPCL).
2. Seguridad y Candados de Negocio: La aplicación opera bajo estrictas normativas militares y mineras (SEDENA / Control de Polvorines). Jamás se deben permitir códigos incongruentes o mezclar familias de empaques.
3. Principios de Código:
   - "El mejor código es el código nunca escrito": Soluciones minimalistas, robustas y de máximo rendimiento ("Ponytail senior dev").
   - Las reglas de diseño ("Hallmark Floor"): Tokens centralizados en Color.kt, alto contraste para entornos oscuros de mina, bordes nítidos, sin gradientes genéricos de IA, soporte de texto adaptable con AutoShrinkText para evitar desbordamientos o recortes en pantallas industriales.
   - Siempre mantener las pruebas unitarias al día (JUnit 4 + Truth) antes de dar cualquier tarea por concluida.
   - Responde de forma técnica, precisa, directa y estructurada en Markdown.
```

---

# PARTE 2: Contexto Completo y Estado Actual del Proyecto (Pegar en el Chat de AI Studio)

```markdown
Hola Gemini. Continuaremos el desarrollo del proyecto **TTExplo Mobile**. A continuación te entrego la ficha técnica completa, la arquitectura, las especificaciones de códigos, el estado actual de la aplicación y el código fuente central para que tengamos continuidad total.

---

## 1. FICHA TÉCNICA Y ENTORNO
* **Nombre de la App:** TTExplo Mobile
* **Versión actual:** v2.1.9 (Build 219)
* **Paquete:** `com.ttexplo.mobile`
* **Plataforma:** Android 14+ (minSdk 26, targetSdk 34)
* **Tecnologías:** Kotlin 1.9.22, Jetpack Compose Material 3, AndroidX Room DB (SQLite), DataWedge Zebra Scanner API, Kotlin Coroutines, StateFlow, Bluetooth SPP.
* **Modo de Operación:** 100% Offline (sin depender de internet en el interior de la mina o polvorín).
* **Pruebas:** 27 de 27 pruebas unitarias pasando (`BUILD SUCCESSFUL`).
* **APK Compilado en raíz:** `TTExplo-Mobile-v2.1.9.apk`

---

## 2. ARQUITECTURA DE PANTALLAS Y MÓDULOS
1. **Home (`HomeScreen.kt`):**
   - Menú 1: Entradas de Alto y Agente (Recepción a polvorín por factura y proveedor).
   - Menú 2: Salidas de Alto y Agente (Despacho a barrenos de mina con generación de vales, desglose por empaque e impresión térmica de tickets).
   - Menú 3: Salida de Accesorios (Detonadores, cordón detonante, mechas).
   - Menú 4: Devolución de Alto y Agente (Reingreso de material sobrante de voladura).
   - Menú 5: Devolución de Accesorios.
   - Menú 6: Historial de Vales & Consolidado (Auditoría, exportación a `.txt` plano por vale o consolidado).
   - Menú 7: Configuración y Personalización (Impresora Bluetooth térmica ESC/POS y Zebra CPCL, datos de mina, permiso general SEDENA, carga y gestión de catálogo CSV).
   - Menú 9: Licenciamiento (Hardware ID SHA-256 de la terminal).

---

## 3. ESPECIFICACIÓN Y ESTRUCTURA DE CÓDIGOS DE BARRAS (CODE 128)

### A. CAJAS (Longitud fija: 30 dígitos numéricos)
* **Estructura:** `[3 ID][6 Fecha DDMMAA][6 Consecutivo Inicial][6 Consecutivo Final][9 Serie de Caja]`
* **Ejemplo:** `319101225016143016189005013023`
  - `319`: ID Producto (Emulex 1).
  - `101225`: Fecha de fabricación (10 de Diciembre de 2025).
  - `016143`: Código cartucho inicial.
  - `016189`: Código cartucho final. Cantidad de piezas contenidas = $(016189 - 016143) + 1 = 47$ piezas.
  - `005013023`: Serie única del embalaje exterior.
* **Candados:** Longitud == 30, dígitos numéricos, Final >= Inicial, Rango <= 500, fecha válida.
* **Color UI:** **Amarillo Oro** (`#FFD600`).

### B. SACOS (Longitud fija: 18 dígitos numéricos)
* **Estructura:** `[3 ID][6 Fecha DDMMAA][9 Serie de Saco]`
* **Ejemplo:** `272120826024843091`
  - `272`: ID Producto (Mexamon / Agente a granel de 25 kg).
  - `120826`: Fecha de fabricación (12 de Agosto de 2026).
  - `024843091`: Serie única de 9 dígitos del saco.
* **Candado Crítico:** Exclusivo para **Agentes de Voladura**. Si se intenta escanear un saco de 18 dígitos con un ID clasificado como **Alto Explosivo** (ej. Emulex, Senatel, Dinamita), la app emite alarma sonora de error y bloquea el registro inmediatamente.
* **Color UI:** **Cian Eléctrico** (`#00E5FF`).

### C. CARTUCHOS INDIVIDUALES (Longitud: 16 dígitos numéricos, soporte para 17)
* **Estructura:** `[3 ID][1 Línea/KP][6 Fecha DDMMAA][6 Consecutivo]`
* **Ejemplo:** `0562101024113003` o `3191101225016143`
  - `056` / `319`: ID Producto.
  - `2` / `1`: Línea de producción o máquina KP.
  - `101024` / `101225`: Fecha DDMMAA.
  - `113003` / `016143`: Consecutivo individual del cartucho.
* **Candado Crítico:** Exclusivo para **Altos Explosivos**. Si se escanea un cartucho con ID de Agente de Voladura (Mexamon/Anfo), la app lo rechaza por incongruencia de empaque.
* **Color UI:** **Naranja Rojizo / Coral** (`#FF916F`).

### D. ACCESORIOS (Prefijo 'X' o alfanumérico)
* Detonadores, cordones detonantes, mechas lentas. Manejados en el módulo de Accesorios.
* **Color UI:** **Morado** (`#BA68C8`).

### E. Métrica Total
* **Color UI:** **Blanco Neutro** (`#FFFFFF`).

---

## 4. SISTEMA DE COLORES OFICIAL (`Color.kt`)
```kotlin
// Tokens de Color de Empaque Estandarizados
val EmpaqueCajaColor = Color(0xFFFFD600)        // Amarillo Oro brillante
val EmpaqueSacoColor = Color(0xFF00E5FF)        // Cian eléctrico
val EmpaqueCartuchoColor = Color(0xFFFF916F)    // Naranja rojizo / Coral (#FF916F)
val EmpaqueAccesorioColor = Color(0xFFBA68C8)   // Morado / Lavanda
val EmpaqueTotalColor = Color(0xFFFFFFFF)       // Blanco neutro
```

---

## 5. CATÁLOGO DE PRODUCTOS (Importación CSV)
El archivo importado se llama `catalogo_productos.csv` y tiene las siguientes columnas obligatorias:
```csv
"IdProducto","Producto","Presentacion","noSAP","Unidad","Cantidad","CodBarras","peso","PG","PU","Tipo"
"056","SENATEL MAGNAFRAC","1 1/2"" X 16""","ESMC38X400MX","KG",25,"056",25,"0119",0,"ALTO_EXPLOSIVO"
"272","MEXAMON","SACO 25 KG","MEX25KG001","KG",1,"272",25,"0119",0,"AGENTE"
"319","EMULEX 1","1"" X 8""","EMX25X200MX","KG",25,"319",25,"0119",0,"ALTO_EXPLOSIVO"
```
* **Regla crítica de negocio:** El `IdProducto` debe tener siempre **3 dígitos con ceros a la izquierda** (`001`, `056`, etc.).

---

## 6. LÓGICA CENTRAL DE VALIDACIÓN (`ScanValidator.kt`)
```kotlin
package com.ttexplo.mobile.domain.scanner

class ScanValidator(
    private val catalogRepository: CatalogRepository
) {
    suspend fun processScan(
        rawBarcode: String,
        currentSessionCodes: Set<String>,
        allowAccessories: Boolean = false
    ): ScanValidationResult {
        val trimmed = rawBarcode.trim()
        val timestamp = DateTimeUtils.currentTimestamp()

        if (trimmed.isEmpty()) {
            return ScanValidationResult(isValid = false, isDuplicate = false, message = "Código vacío.", rawBarcode = rawBarcode, timestamp = timestamp)
        }

        if (currentSessionCodes.contains(trimmed)) {
            val productInfo = catalogRepository.lookupBarcode(trimmed)
            return ScanValidationResult(isValid = true, isDuplicate = true, message = "Código duplicado en este vale: $trimmed", rawBarcode = trimmed, timestamp = timestamp, productInfo = productInfo)
        }

        // Accesorios (Inician con X)
        if (trimmed.startsWith("X", ignoreCase = true)) {
            val productInfo = catalogRepository.lookupBarcode(trimmed)
            return ScanValidationResult(isValid = true, isDuplicate = false, message = "Accesorio escaneado", rawBarcode = trimmed, timestamp = timestamp, productInfo = productInfo, empaque = TipoEmpaque.ACCESORIO)
        }

        val empaque = when (trimmed.length) {
            30 -> TipoEmpaque.CAJA
            18 -> TipoEmpaque.SACO
            16, 17 -> TipoEmpaque.PIEZA // Cartucho individual
            else -> null
        }

        if (empaque == null || !trimmed.all { it.isDigit() }) {
            return ScanValidationResult(
                isValid = false,
                isDuplicate = false,
                message = "Código inválido (${trimmed.length} dígitos). Solo se permiten Cajas (30 dígitos), Sacos (18 dígitos) o Cartuchos (16 dígitos).",
                rawBarcode = trimmed,
                timestamp = timestamp
            )
        }

        val productInfo = catalogRepository.lookupBarcode(trimmed)

        // Candado 1: Alto Explosivo nunca es Saco
        if (productInfo.type == ProductType.ALTO_EXPLOSIVO && empaque == TipoEmpaque.SACO) {
            return ScanValidationResult(
                isValid = false,
                isDuplicate = false,
                message = "Código inválido (18 dígitos). '${productInfo.name}' es Alto Explosivo y solo se presenta en Cajas (30 dígitos) o Cartuchos (16 dígitos), nunca en Sacos.",
                rawBarcode = trimmed,
                timestamp = timestamp,
                productInfo = productInfo
            )
        }

        // Candado 2: Agente de Voladura nunca es Cartucho
        if (productInfo.type == ProductType.AGENTE && empaque == TipoEmpaque.PIEZA) {
            return ScanValidationResult(
                isValid = false,
                isDuplicate = false,
                message = "Código inválido (${trimmed.length} dígitos). '${productInfo.name}' es Agente de Voladura y solo se presenta en Sacos (18 dígitos) o Cajas (30 dígitos), no en Cartuchos.",
                rawBarcode = trimmed,
                timestamp = timestamp,
                productInfo = productInfo
            )
        }

        // Candado 3: Rango de Cajas
        if (empaque == TipoEmpaque.CAJA) {
            val inicial = trimmed.substring(9, 15).toIntOrNull()
            val final = trimmed.substring(15, 21).toIntOrNull()
            if (inicial != null && final != null) {
                if (final < inicial) {
                    return ScanValidationResult(isValid = false, isDuplicate = false, message = "Estructura de caja inválida: código final ($final) menor al inicial ($inicial).", rawBarcode = trimmed, timestamp = timestamp, productInfo = productInfo)
                }
                val countCartuchos = final - inicial + 1
                if (countCartuchos > 500) {
                    return ScanValidationResult(isValid = false, isDuplicate = false, message = "Estructura de caja inválida: rango de $countCartuchos cartuchos excede la capacidad máxima.", rawBarcode = trimmed, timestamp = timestamp, productInfo = productInfo)
                }
            }
        }

        return ScanValidationResult(isValid = true, isDuplicate = false, message = "Lectura correcta", rawBarcode = trimmed, timestamp = timestamp, productInfo = productInfo, empaque = empaque)
    }
}
```

---

## 7. INTERFAZ Y MEJORAS RECIENTES
* **Contadores superiores unificados:** En Entradas, Salidas y Devolución se muestran 4 cajitas compactas:
  - `TOTAL`: Blanco
  - `CAJAS`: Amarillo Oro
  - `SACOS`: Cian Eléctrico
  - `CARTUCHOS`: Naranja Coral
* **Desglose de productos:** Cuadrícula de 2 columnas agrupada por `Triple(name, presentation, empaque)` con scroll vertical y límite de altura de `145.dp` para no empujar la lista de lecturas ni el botón inferior.
* **Filtrado táctil dinámico:** Tocar una tarjeta de producto en el desglose resalta la tarjeta con su color y filtra la lista inferior para mostrar solo las lecturas de ese producto, mostrando el botón `[VER TODOS (N)]`.
* **Botones de impresión:** Se eliminó cualquier mención de `(POS)` en la interfaz. El botón ahora dice limpiamente: `Imprimir Ticket`.
* **Notepad++ Workflow:** Se crearon `AUTO_ACTUALIZAR_NOTEPAD.bat` y `ACTUALIZAR_MANUAL.bat` en la raíz para compilar e instalar en vivo en el emulador tras presionar `Ctrl + S`.

---

Estamos listos. Por favor confírmame que has recibido y cargado todo el contexto del proyecto y dime qué tarea o funcionalidad abordamos a continuación.
```
