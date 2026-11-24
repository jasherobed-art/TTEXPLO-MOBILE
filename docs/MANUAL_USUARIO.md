# Manual del usuario — T&T Explo Mobile

T&T Explo Mobile 2.6.0 (build 21) es una aplicación para controlar explosivos en polvorín, capturar vales, consultar existencias y sincronizar datos entre terminales de forma offline. Este manual explica las tareas diarias de un operador, desde activar la licencia hasta imprimir un vale.

La aplicación funciona sin internet. Los datos se guardan en la terminal y se comparten con otras terminales mediante QR o archivos JSON.

## Antes de empezar

Cada terminal tiene un identificador único de hardware (Device ID). La licencia se genera fuera de la app, en License Master Pro, y se emite para ese Device ID. No use una licencia de otra terminal.

### Activar la licencia

1. En la pantalla de inicio, abra **8. Configuración y Personalización**.
2. En la sección de licenciamiento, copie el **Device ID** con **COPIAR ID**.
3. En License Master Pro, genere una licencia para ese Device ID.
4. En la app, pegue la clave corta `TTEX-...`, el token `TTEX:...`, importe un archivo `.lic` o escanee el QR de la licencia.
5. Pulse **ACTIVAR LICENCIA**.

Cuando la licencia es válida, la app muestra **AUTORIZADA**. Si aparece **PRÓXIMA A VENCER**, renueva la licencia antes de la fecha indicada.

> [!IMPORTANT]
> La APK se entrega con una licencia temporal de 1 día. Mientras esa licencia esté activa, los paneles **MODO PRUEBA** están visibles para capacitación. Al instalar una licencia real, esos paneles desaparecen. Ese comportamiento es normal.

## Pantalla de inicio

La pantalla de inicio muestra la versión `v2.6.0` y ocho módulos. Cada módulo se abre con un toque.

| Módulo | Uso principal |
|---|---|
| **1. Entradas de Alto y Agente** | Recibir material y darlo de alta en el polvorín. |
| **2. Salidas de Alto y Agente** | Despachar material a barrenos e imprimir el vale. |
| **3. Salida de Accesorios** | Despachar detonadores, cordón y artificios. |
| **4. Devolución de Alto y Agente** | Reingresar explosivos o agentes sobrantes. |
| **5. Devolución de Accesorios** | Reingresar accesorios con cantidad negativa. |
| **6. Inventario en Polvorín (En Vivo)** | Consultar existencias, ajustar físico y ver Kardex. |
| **7. Historial de Vales & Consolidado** | Consultar vales guardados y exportarlos. |
| **8. Configuración y Personalización** | Impresora, datos de mina, catálogo y licencia. |

El botón de engranaje abre la configuración. Si la licencia está por vencer, aparece un aviso en la parte superior.

## Registrar una entrada

1. Abra **1. Entradas de Alto y Agente**.
2. Complete los campos del encabezado: número de proveedor, número de polvorín y factura o remisión.
3. Pulse **CONTINUAR A ESCANEO**.
4. Escanee cada código SEDENA con el lector integrado o escriba el código en el campo de captura.
5. Revise la cinta superior: muestra total de ítems y conteo de cajas, sacos y cartuchos.
6. Si necesita corregir un ítem, ábralo desde la lista y elimínelo o ajústelo.
7. Pulse **GUARDAR** para guardar el vale. Después puede imprimir el ticket térmico desde la pantalla del vale.

La app recupera automáticamente un vale en progreso si la pantalla se cierra antes de guardar. Verá el aviso **Vale en progreso recuperado**.

## Registrar una salida de alto y agente

1. Abra **2. Salidas de Alto y Agente**.
2. Complete número de vale, empresa, lugar, transportista, turno y voladura o mina.
3. Pulse **CONTINUAR A DESPACHO**.
4. Escanee los códigos SEDENA de los sacos o cajas que salen.
5. Revise el total de materiales y el desglose por presentación.
6. Guarde el vale e imprima el ticket con la impresora activa.

El ticket incluye las líneas de entrega y recepción. El margen inferior del ticket deja espacio suficiente para la firma.

## Registrar salida o devolución de accesorios

1. Abra **3. Salida de Accesorios** o **5. Devolución de Accesorios**.
2. Complete número de vale, empresa, lugar, transportista, quién recibe y quién entrega, turno y voladura o referencia.
3. Continúe a la pantalla de escaneo.
4. Escriba o escanee el código del accesorio y capture la cantidad.
5. La unidad puede ser **PZA** (piezas) o **MTS** (metros). Seleccione la correcta antes de guardar.
6. Guarde el vale e imprima el ticket si corresponde.

En las devoluciones de accesorios, la cantidad se registra como negativa para descontar del inventario.

## Registrar una devolución de alto y agente

1. Abra **4. Devolución de Alto y Agente**.
2. Indique quién entrega el material y quién lo recibe en el polvorín.
3. Escanee los códigos que regresan.
4. Revise el total y guarde el vale.

La devolución genera eventos compensatorios en el libro de inventario, de modo que el saldo vuelve a entrar al polvorín.

## Usar el simulador de escaneo

Antes de instalar una licencia real, cada pantalla de escaneo muestra un panel plegable **MODO PRUEBA**. Sirve para practicar sin escáner físico.

1. Abra el panel **MODO PRUEBA**.
2. Elija un caso de prueba: código válido, duplicado, error de coherencia SEDENA u otros.
3. Envíe el código de prueba al flujo de escaneo.
4. Cierre el panel cuando termine.

No use este panel para capturar material real. Después de instalar una licencia real, el panel desaparece.

## Inventario en polvorín

El módulo **6. Inventario en Polvorín (En Vivo)** muestra el saldo por familia SEDENA, existencias en KG, PZA y MTS, y el estado de sincronización.

### Consultar existencias

1. Abra **Inventario en Polvorín**.
2. Use las cinco tarjetas superiores para filtrar por familia: Alto Explosivo, Agente Explosivo, Cordón, Cañuelas o Detonadores.
3. Escriba un ID, producto, familia o código `X...` en el buscador.
4. Toque **Todas** para quitar un filtro activo.
5. En la lista de productos, toque **Kardex** para ver movimientos o **Códigos SEDENA** para ver series.

### Hacer carga rápida o ajuste físico

El botón fijo **CARGA RÁPIDA / AJUSTE** está en la parte inferior de la pantalla. No tapa la lista.

1. Pulse **CARGA RÁPIDA / AJUSTE**.
2. Escanee un código de producto o seleccione un producto del catálogo.
3. Elija el modo: carga inicial o ajuste físico.
4. Capture cantidades de cajas, sacos, cartuchos, piezas o metros, según la familia.
5. Si corresponde, capture peso de caja, peso de saco y cartuchos por caja.
6. Agregue una observación de turno o auditoría si es necesaria.
7. Pulse **GUARDAR**.

También puede abrir el diálogo desde la tarjeta de un producto, con la acción **Ajustar** de esa tarjeta.

### Abrir la bóveda de licencias

1. En Inventario, pulse el menú de tres puntos de la barra superior.
2. Seleccione **Licencias**.
3. La pantalla **Licencias guardadas** muestra el Device ID de la terminal y las licencias guardadas.

## Bóveda de licencias

La bóveda guarda licencias emitidas en License Master Pro. No genera licencias dentro de la app.

### Agregar una licencia

1. En **Licencias guardadas**, pulse **Agregar**.
2. Escriba un alias fácil de recordar, por ejemplo `Polvorín Norte`.
3. Pegue el token `TTEX:...` o la clave corta `TTEX-...`.
4. Pulse **Guardar**. La app valida la firma antes de guardar.
5. Si la licencia pertenece a otra terminal, la tarjeta muestra **OTRA TERMINAL**. No se puede instalar en esta terminal.

### Instalar una licencia guardada

1. En la tarjeta de la licencia, compruebe que el estado sea **VIGENTE** y la terminal destino sea esta.
2. Pulse **Instalar**.
3. Si la licencia no corresponde al Device ID de la terminal, la app muestra un error de dispositivo no autorizado.
4. Después de instalar, los modos de prueba se ocultan.

### Copiar, compartir y borrar

- El icono de copiar copia el token al portapapeles.
- El icono de compartir abre el menú de compartir de Android.
- El icono de eliminar quita la licencia de la bóveda. No elimina la licencia activa instalada.

### Exportar e importar la bóveda

Use estos botones para llevar licencias a otra terminal.

1. Pulse **Exportar** y elija una ubicación. Se crea un archivo JSON con las licencias guardadas.
2. En la otra terminal, abra **Licencias guardadas** y pulse **Importar**.
3. Seleccione el archivo JSON.
4. Compruebe las tarjetas importadas antes de instalar una licencia.

La importación reemplaza la bóveda actual. Exporte una copia antes de importar si quiere conservar las licencias existentes.

## Sincronización entre terminales

La sincronización se abre desde el icono de sincronización del Inventario o desde el módulo correspondiente. La pantalla tiene tres pestañas: **Enviar**, **Recibir** e **Historial**.

La app emite QR firmados `TTEX4` y también acepta QR o JSON de versiones anteriores durante la transición. Un QR V4 incluye una firma HMAC-SHA256; no modifique el texto del QR.

### Enviar datos

1. Abra la pestaña **Enviar**.
2. Elija el alcance del paquete:
   - **Pendientes**: eventos nuevos sin sincronizar.
   - **24 horas**: eventos del último día.
   - **Todo**: historial completo, útil para cargar una terminal nueva.
3. Elija la densidad del QR:
   - **Alta (1,400)**: menos QRs, más datos por imagen.
   - **Fácil (600)**: más QRs, más fáciles de leer.
4. Muestre cada QR a la otra terminal o exporte el archivo JSON con **EXPORTAR ARCHIVO .JSON (RESPALDO / USB)**.
5. Si el lote tiene varias partes, muestre todos los QRs. La terminal receptora puede leerlos en cualquier orden.

### Recibir datos

1. Abra la pestaña **Recibir**.
2. Escanee los QRs o pegue la cadena `TTEX4:...`, `TTEX3:...` o `TTEXSYNC|V2|...`.
3. También puede pegar el JSON completo.
4. Pulse **PROCESAR BLOQUE / JSON**.
5. Revise el informe de fusión. La app detecta duplicados, doble despacho de un mismo código SEDENA y saldos negativos.

La recepción es idempotente: si vuelve a enviar el mismo lote, no se duplican los eventos.

### Historial y conflictos

La pestaña **Historial** muestra las sesiones de sincronización y los conflictos detectados. Toque una fila para ver el detalle o saltar al Kardex del producto afectado.

## Historial de vales

1. Abra **7. Historial de Vales & Consolidado**.
2. Busque un vale por folio, fecha, empresa o transportista.
3. Toque un vale para ver el detalle, reimprimirlo o eliminarlo.
4. Para exportar, use **EXPORTAR CONSOLIDADO** o seleccione varios vales y use **EXPORTAR SELECCIONADOS**.

La eliminación de vales pide una confirmación en dos pasos con un código de 4 dígitos.

## Configuración

El módulo **8. Configuración y Personalización** reúne datos de la mina, catálogo, impresoras y licencia.

### Datos de la mina

Complete estos campos porque aparecen en los tickets:

- **Nombre de la Mina / Empresa**.
- **Número de Permiso General (SEDENA)**.
- **Encabezado Adicional / Nota Polvorín**.

Pulse **GUARDAR ARCHIVO PLANO (CARPETA / USB)** para exportar un respaldo legible.

### Catálogo de productos

Puede ver el catálogo completo, recargar la base y importar un CSV con **Importar CSV**. Después de importar, verifique las calibraciones de peso unitario y cartuchos por caja.

### Impresora térmica

En la sección de impresoras puede crear perfiles con:

- Alias o nombre en el polvorín.
- Dirección MAC Bluetooth.
- Protocolo: ESC/POS, CPCL o texto plano.
- Ancho de papel: 58 mm, 80 mm o 104 mm.
- Botón para usarla como impresora activa.

Antes de imprimir, active Bluetooth en Android y conceda los permisos de dispositivos cercanos cuando la app los pida.

### Estado de licencia y versión

La configuración muestra el Device ID, el cliente autorizado, la unidad minera, la vigencia, la versión instalada y los tipos de salida y lector soportados.

## Diagramas de flujo y guía visual de controles

Esta sección resume los caminos más comunes dentro de la app y explica la función de cada control importante. Úsela como referencia rápida antes de capacitar a un operador nuevo.

### Flujo de una entrada

El objetivo es recibir material y dejarlo registrado en el polvorín.

1. Encabezado: proveedor, polvorín y factura o remisión.
2. Escaneo: códigos SEDENA de cajas, sacos o cartuchos.
3. Resumen: total de ítems y conteo por presentación.
4. Guardado: el vale queda en el historial y genera eventos UUID.
5. Impresión: ticket térmico con líneas de firma y margen inferior.

Secuencia abreviada: Encabezado → Escaneo → Totales → Guardar → Imprimir.

### Flujo de un despacho

El objetivo es sacar material del polvorín hacia un barreno.

1. Encabezado: vale, empresa, lugar, transportista, turno y voladura.
2. Escaneo: códigos SEDENA de lo que se entrega.
3. Validación: la app detecta duplicados o saldos negativos.
4. Guardado: se generan eventos compensatorios en el inventario.
5. Impresión: el vale queda listo para firma del responsable.

Secuencia abreviada: Encabezado → Escaneo → Validación → Guardar → Imprimir.

### Flujo de ajuste físico

El objetivo es corregir el saldo real de un producto en el polvorín.

1. Inventario en Polvorín: pulse CARGA RÁPIDA / AJUSTE o la acción Ajustar de una tarjeta.
2. Producto: escanee un código o seleccione un producto del catálogo.
3. Modo: elija carga inicial o ajuste físico.
4. Cantidades: capture cajas, sacos, cartuchos, piezas o metros.
5. Guardado: la app calcula el nuevo saldo y deja el evento listo para sincronizar.

Secuencia abreviada: Inventario → Producto → Modo → Cantidades → Guardar.

### Flujo de sincronización

El objetivo es llevar los eventos de una terminal a otra sin internet.

1. Enviar: elija alcance Pendientes, 24 horas o Todo.
2. Densidad: elija Alta (1,400) o Fácil (600).
3. Paquete: la app crea un QR V4 firmado o un JSON.
4. Recibir: la otra terminal escanea el QR o pega el JSON.
5. Informe: revise eventos fusionados, duplicados y conflictos.

Secuencia abreviada: Alcance → Densidad → QR/JSON → Recepción → Informe.

### Flujo de licenciamiento

El objetivo es autorizar la terminal con una licencia generada fuera de la app.

1. Device ID: copie el identificador de la terminal.
2. License Master Pro: genere una licencia para ese Device ID.
3. Activación: pegue la clave, importe el .lic o escanee el QR.
4. Bóveda: guarde la licencia con un alias para reutilizarla.
5. Instalación: instale la licencia en la terminal correcta.

Secuencia abreviada: Device ID → Generación externa → Activación → Bóveda → Instalación.

### Qué es cada control importante

Use esta tabla para explicar rápidamente los elementos que aparecen en pantalla.

| Control | Dónde aparece | Para qué sirve |
|---|---|---|
| Device ID | Configuración y Licencias guardadas | Identifica la terminal. La licencia se genera para ese ID. |
| Barra inferior CARGA RÁPIDA / AJUSTE | Inventario en Polvorín | Abre el diálogo para cargar o corregir existencias. |
| Cinta de métricas | Pantallas de escaneo | Muestra totales de ítems, cajas, sacos y cartuchos. |
| Filtros de familia | Inventario en Polvorín | Muestran solo Alto, Agente, Cordón, Cañuelas o Detonadores. |
| Kardex | Tarjeta de producto | Muestra el historial de movimientos de ese producto. |
| Códigos SEDENA | Tarjeta de producto | Muestra las series escaneadas de ese producto. |
| QR V4 | Sincronización | Transporta eventos firmados con HMAC-SHA256. |
| Perfil de impresora | Configuración | Define protocolo, ancho de papel y dirección Bluetooth. |
| Modo prueba | Pantallas de escaneo | Simula lecturas para capacitación. Desaparece con licencia real. |
| Bóveda de licencias | Menú de Inventario | Guarda, instala, exporta e importa licencias por terminal. |
| Informe de fusión | Sincronización | Explica cuántos eventos se fusionaron y qué conflictos hay. |

## Buenos hábitos

- Guarde el vale al terminar cada turno; no deje vales en borrador.
- Verifique el Device ID antes de pedir una licencia nueva.
- Exporte la bóveda de licencias si va a migrar a otra terminal.
- Revise los conflictos de sincronización antes de borrar vales.
- No use los paneles **MODO PRUEBA** con material real.
- Si imprime tickets, compruebe que la impresora activa tenga papel y suficiente batería.

## Problemas comunes

### La app pide permisos de Bluetooth

Acepte el permiso de dispositivos cercanos. La app lo necesita para encontrar la impresora térmica y para algunas funciones de lectura.

### La impresora no imprime

Compruebe que esté encendida, vinculada en Android y seleccionada como activa. Si usa CPCL o ESC/POS, confirme que el perfil tenga el protocolo y ancho de papel correctos.

### Un QR no se reconoce

Use la cadena completa, sin espacios ni saltos de línea. Si viene de una terminal antigua, la app acepta `TTEX3` y `TTEXSYNC|V2|`, pero una terminal antigua no podrá leer un QR `TTEX4`.

### La licencia aparece vencida

Genere una licencia nueva en License Master Pro para el Device ID actual y actívela desde la pantalla de licencias o desde la bóveda.

### Desaparecieron los paneles de prueba

Es correcto: significa que hay una licencia real instalada. Los paneles de prueba solo están disponibles con la licencia temporal de fábrica.

## Soporte

Cuando reporte un problema, incluya:

- Device ID de la terminal.
- Versión instalada, por ejemplo `v2.6.0 (Build 21)`.
- Número de vale o ID de producto relacionado.
- Hora del evento y nombre de la mina.
- Captura del mensaje de error, si aparece.
