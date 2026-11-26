# Manual del usuario — T&T Explo Mobile

T&T Explo Mobile 2.6.0 (build 27) es la app del polvorín. Sirve para lo de todos los días: activar la terminal, capturar vales, ver existencias y pasar datos a otra terminal.

Funciona sin internet. Lo que capturas queda guardado en la terminal y se comparte con QR o con un archivo JSON.

## Antes de empezar

Cada terminal tiene su ID (Device ID). Anótalo bien: la licencia se hace para ese ID y no sirve en otra terminal.

### Activar la licencia

1. En el inicio, abre **9. Configuración y Personalización**.
2. Copia el **Device ID** con **COPIAR ID**.
3. En License Master Pro genera la licencia para ese ID.
4. En la app pega la clave corta `TTEX-...`, el token `TTEX:...`, importa el `.lic` o escanea el QR.
5. Toca **ACTIVAR LICENCIA**.

Si salió bien, verás **AUTORIZADA**. Si dice **PRÓXIMA A VENCER**, renueva antes de la fecha que marca.

> [!IMPORTANTE]
> La APK viene con una licencia temporal de 1 día. Activa la licencia definitiva antes de operar de verdad.

## Pantalla de inicio

Arriba verás `v2.6.0`. Debajo están los nueve módulos. Tocas y entras. La app se abre siempre en vertical; no gira a horizontal.

| Módulo | Para qué lo usas |
|---|---|
| **1. Entradas de Alto y Agente** | Recepción de alto y agente. |
| **2. Salidas de Alto y Agente** | Despacho de alto y agente. |
| **3. Salida de Accesorios** | Despachar detonadores, cordón y artificios. |
| **4. CONTEO EN CAJA** | Conteo de cartuchos. |
| **5. Devolución de Alto y Agente** | Meter de vuelta lo que sobró. |
| **6. Devolución de Accesorios** | Devolución de detonantes y accesorios. |
| **7. Inventario en Polvorín (En Vivo)** | Ver saldos, ajustar y consultar Kardex. |
| **8. Historial de Vales & Consolidado** | Buscar vales y exportarlos. |
| **9. Configuración y Personalización** | Impresora, mina, catálogo y licencia. |

El engranaje de arriba abre Configuración. Si la licencia está por vencer, verás un aviso arriba.

## Registrar una entrada

1. Abre **1. Entradas de Alto y Agente**.
2. Llena proveedor, polvorín y factura o remisión.
3. Toca **CONTINUAR A ESCANEO**.
4. Escanea cada código. Si el lector falla, escribe el código a mano en el campo de captura.
5. Mira la cinta de arriba: ahí ves cuántos ítems llevas y cuántas cajas, sacos y cartuchos.
6. Si te equivocaste en uno, abre el ítem en la lista y corrigelo o bórralo.
7. Toca **GUARDAR**. Después puedes imprimir el ticket desde la pantalla del vale.

Si se te cierra la app a mitad de vale, no perdiste lo capturado. Al volver verás **Vale en progreso recuperado**.

### Si el código trae la fecha mal

Si el lector captura un código con una fecha de fabricación fuera del rango permitido o con un día que no existe, la app no lo da de alta. Verás **FECHA INCONSISTENTE EN EL CÓDIGO** con el código leído y la fecha que detectó, por ejemplo **19/08/96**.

Si dudas de la lectura, toca **VOLVER A LEER EL CÓDIGO** y escanea de nuevo la caja o el saco.

IMPORTANTE: escribe los 6 dígitos de la fecha correcta en formato `DDMMYY` y toca **CORREGIR**. La app sustituye solo esos dígitos y captura el código corregido. Si tocas **CANCELAR**, la lectura no se agrega al vale.

## Registrar una salida de alto y agente

1. Abre **2. Salidas de Alto y Agente**.
2. Llena vale, empresa, lugar, transportista, turno y voladura o mina.
3. Toca **CONTINUAR A DESPACHO**.
4. Escanea los sacos o cajas que salen.
5. Revisa el total y el desglose por presentación.
6. Guarda e imprime con la impresora activa.

El encabezado trae quién entrega y quién recibe. Al final quedan solo **ENTREGO:** y **RECIBIO:** con una línea para firmar a mano, sin nombres impresos. La línea se ajusta al ancho del papel de la impresora activa.

## Registrar salida o devolución de accesorios

1. Abre **3. Salida de Accesorios** o **5. Devolución de Accesorios**.
2. Llena vale, empresa, lugar, transportista, quién recibe, quién entrega, turno y voladura o referencia.
3. Pasa a la pantalla de escaneo.
4. Escanea o escribe el código del accesorio y pon la cantidad.
5. Elige bien la unidad: **PZA** para piezas, **MTS** para metros.
6. Guarda e imprime si corresponde.

En las devoluciones la cantidad sale negativa. Así el inventario entiende que ese material volvió.

## Registrar una devolución de alto y agente

1. Abre **4. Devolución de Alto y Agente**.
2. Di quién entrega y quién recibe en el polvorín.
3. Escanea los códigos que regresan.
4. Revisa el total y guarda.

Con eso el saldo vuelve a entrar al polvorín. No hace falta el QR de salida otra vez.

## Inventario en polvorín

Aquí ves lo que hay en el polvorín: saldo por familia, KG, PZA, MTS y si hay eventos pendientes de pasar a otra terminal.

### Consultar existencias

1. Abre **Inventario en Polvorín**.
2. Toca una de las cinco tarjetas para filtrar: Alto, Agente, Cordón, Cañuelas o Detonadores.
3. Escribe un ID, producto, familia o código `X...` en el buscador.
4. Toca **Todas** para quitar el filtro.
5. En una tarjeta de producto, **Kardex** te muestra los movimientos y **Códigos** las series.

### Hacer carga rápida o ajuste físico

El botón **CARGA RÁPIDA / AJUSTE** va fijo abajo. Ya no tapa la lista.

1. Toca **CARGA RÁPIDA / AJUSTE**.
2. Escanea el producto o elígelo del catálogo.
3. Elige: carga inicial o ajuste físico.
4. Pon cantidades de cajas, sacos, cartuchos, piezas o metros, según el caso.
5. Si aplica, pon peso de caja, peso de saco y cartuchos por caja.
6. Agrega una observación de turno o auditoría si hace falta.
7. Guarda.

También puedes abrirlo desde la tarjeta del producto, con **Ajustar**.

### Abrir la bóveda de licencias

1. En Inventario, toca los tres puntos de arriba.
2. Elige **Licencias**.
3. Verás el Device ID de esta terminal y las licencias guardadas.

## Contar cajas

1. Abre **4. CONTEO EN CAJA** desde la pantalla de inicio.
2. Escanea el código de la caja o escríbelo en el campo de captura. El código debe tener 30 dígitos.
3. La app muestra el código y cuántas piezas indica el rango. Ese número es solo una referencia.
4. Cuenta físicamente las piezas. Escribe la línea o KP (un solo número) y las piezas contadas (hasta 4 dígitos, entre 1 y 500).
5. Toca **AGREGAR CÓDIGO**. Si tu número no coincide con el del rango, la app te pregunta si es correcto antes de agregarlo. Lo que tecleaste manda.
6. En el listado ves el código con su descripción del catálogo. Puedes corregir el KP y las piezas en cada renglón, o borrar el renglón completo; el borrado pide confirmación.
7. Si lees la misma caja dos veces, verás **CÓDIGO REPETIDO** y la segunda lectura no se agrega.
8. Toca **CONFIRMAR CONTEO**. El conteo se guarda solo en el historial con un folio `CC-AAAAAMMDD-NNN`.
9. Toca **IMPRIMIR** para el ticket de conteo. Trae folio, cada código con su descripción, KP, piezas, fecha y hora, y el total de piezas. No lleva firmas.

IMPORTANTE: el peso por pieza se calcula con 25 kg entre las piezas que contaste. Si te equivocas al contar, corrige el número antes de confirmar. Ya no hay botón de TXT: los datos salen en el ticket y en el consolidado del historial.

## Bóveda de licencias

Aquí guardas las licencias que generaste en License Master Pro. La app no fabrica licencias.

### Agregar una licencia

1. En **Licencias guardadas**, toca **Agregar**.
2. Pon un alias que te recuerde dónde va, por ejemplo `Polvorín Norte`.
3. Pega el token `TTEX:...` o la clave corta `TTEX-...`.
4. Toca **Guardar**. La app revisa la firma antes de guardarla.
5. Si es para otra terminal, la tarjeta dice **OTRA TERMINAL**. Ahí no la instales.

### Instalar una licencia guardada

1. Revisa que la tarjeta diga **VIGENTE** y que el destino sea esta terminal.
2. Toca **Instalar**.
3. Si el ID no coincide, la app avisa que el dispositivo no está autorizado. Estás en la terminal equivocada.

### Copiar, compartir y borrar

- Copiar: manda el token al portapapeles.
- Compartir: abre el menú de compartir de Android.
- Eliminar: saca la licencia de la bóveda. No toca la licencia ya instalada.

### Exportar e importar la bóveda

Para llevar licencias a otra terminal:

1. Toca **Exportar** y elige dónde guardar. Sale un JSON con tus licencias.
2. En la otra terminal, abre **Licencias guardadas** y toca **Importar**.
3. Selecciona el JSON.
4. Revisa las tarjetas antes de instalar.

Ojo: importar reemplaza la bóveda actual. Si quieres conservar la tuya, exporta primero.

## Sincronización entre terminales

Para pasar datos a otra terminal, toca el icono de sincronización en Inventario. Verás tres pestañas: **Enviar**, **Recibir** e **Historial**.

Los QR salen firmados (`TTEX4`). No les cambies nada: cópialos tal cual. La app también acepta QR y JSON de versiones anteriores mientras hacemos la transición.

### Enviar datos

1. Abre **Enviar**.
2. Elige alcance:
   - **Pendientes**: lo nuevo que aún no se pasó.
   - **24 horas**: lo del último día.
   - **Todo**: el historial completo. Útil para cargar una terminal nueva.
3. Elige densidad:
   - **Alta (1,400)**: menos QR, más cargados.
   - **Fácil (600)**: más QR, pero se leen mejor.
4. Muestra los QR a la otra terminal o saca el JSON con **EXPORTAR ARCHIVO .JSON (RESPALDO / USB)**.
5. Si el lote trae varias partes, muestra todas. La otra terminal puede leerlas en cualquier orden.

### Recibir datos

1. Abre **Recibir**.
2. Escanea los QR o pega la cadena `TTEX4:...`, `TTEX3:...` o `TTEXSYNC|V2|...`.
3. También puedes pegar el JSON completo.
4. Toca **PROCESAR BLOQUE / JSON**.
5. Lee el informe. Ahí salen duplicados, doble despacho del mismo código y saldos negativos.

Si envías el mismo lote dos veces, no se duplica nada. Ya está probado.

### Historial y conflictos

En **Historial** ves las sesiones y los conflictos. Toca una fila para ver el detalle o saltar al Kardex del producto.

## Historial de vales

1. Abre **8. Historial de Vales & Consolidado**.
2. Filtra por tipo. Los conteos de caja aparecen con el folio `CC-AAAAAMMDD-NNN` y el filtro **Conteos**.
3. Toca un vale para verlo, reimprimirlo o borrarlo. En los conteos no hay botón de editar: corrige los renglones dentro del conteo antes de confirmarlo.
4. Para exportar, usa **EXPORTAR CONSOLIDADO** o selecciona varios y usa **EXPORTAR SELECCIONADOS**. Los conteos salen con encabezado `CAJ` y renglones `codigo,KP,piezas,fechaHora`.

Borrar no es de un toque: pide un código de 4 dígitos. Esa molestia es a propósito.

## Configuración

En **9. Configuración y Personalización** está lo de siempre: mina, catálogo, impresoras y licencia.

### Datos de la mina

Llena estos campos porque salen en los tickets:

- **Nombre de la Mina / Empresa**.
- **Número de Permiso General (SEDENA)**.
- **Encabezado Adicional / Nota Polvorín**.

Si quieres un respaldo legible, toca **GUARDAR ARCHIVO PLANO (CARPETA / USB)**.

### Catálogo de productos

Puedes ver el catálogo, recargar la base e importar CSV con **Importar CSV**. Después de importar, revisa pesos unitarios y cartuchos por caja.

### Impresora térmica

Aquí creas perfiles con:

- Alias o nombre en el polvorín.
- Dirección MAC Bluetooth.
- Protocolo: ESC/POS, CPCL o texto plano.
- Papel: 2, 3 o 4 pulgadas.
- El botón para dejarla como impresora activa.

En **CONTENIDO DEL TICKET TÉRMICO** decides qué lleva el vale. **Incluir líneas de firmas** pone o quita el bloque de **ENTREGO:** y **RECIBIO:**. **Listado de códigos con fecha/hora** incluye u omite el detalle de códigos; cuando va, el encabezado dice **LISTADO DE CODIGOS**. La elección aplica a los cuatro vales.

Antes de imprimir, enciende Bluetooth y acepta el permiso de dispositivos cercanos cuando la app lo pida.

### Estado de licencia y versión

Aquí ves el Device ID, el cliente autorizado, la mina, la vigencia, la versión instalada y qué lector y salida de ticket soporta la terminal.

## Recorrido visual por todas las pantallas

Aquí ves cada pantalla con sus contadores y filtros. Es para reconocerla rápido cuando estés frente a la terminal.

| Ventana | Dónde se abre |
|---|---|
| Inicio | Al abrir la app. |
| Encabezado de entrada | Módulo 1. |
| Escaneo de entrada | Botón CONTINUAR A ESCANEO del módulo 1. |
| Encabezado de despacho | Módulo 2. |
| Escaneo de despacho | Botón CONTINUAR A DESPACHO del módulo 2. |
| Accesorios | Módulo 3 y módulo 5. |
| Devolución de alto | Módulo 4. |
| Devolución de accesorios | Módulo 5. |
| Inventario y filtros | Módulo 6. |
| Estado vacío | Módulo 6 sin existencias. |
| Diálogo de carga rápida | Botón fijo de Inventario o Ajustar en una tarjeta. |
| Menú de Inventario | Tres puntos en Inventario. |
| Licencias guardadas | Menú de Inventario → Licencias. |
| Sincronización | Icono de sincronización en Inventario. |
| Historial | Módulo 7. |
| Detalle de vale | Al tocar un vale en Historial. |
| Configuración general | Módulo 8. |
| Impresoras | Configuración → Impresoras Bluetooth. |
| Catálogo de productos | Configuración → Ver Catálogo Completo. |
| Hoja de producto | Al tocar una tarjeta en Inventario. |
| Activación de licencia | Configuración o aviso de vencimiento. |
| Diálogos y avisos | Según la acción que estés haciendo. |

### Inicio

La pantalla de arriba muestra la versión y los ocho módulos. Cada tarjeta tiene un ícono, un título y una línea de texto que dice qué hace. Si la licencia está por vencer, aparece un aviso amarillo arriba.

### Encabezado de entrada

Antes de escanear pides proveedor, polvorín y factura. Este encabezado es el que luego aparece en el ticket.

### Escaneo de entrada

La cinta de arriba trae cuatro contadores: **TOTAL**, **CAJAS**, **SACOS** y **CARTUCHOS**. Se actualizan con cada código que entra. Debajo está el encabezado plegable y la lista de códigos capturados. Lo mismo aplica en despacho, accesorios y devolución.

### Encabezado de despacho

Aquí llenas vale, empresa, lugar, transportista, turno y voladura. Esos datos salen en el vale impreso.

### Escaneo de despacho

Igual que en entrada: cinta con **TOTAL**, **CAJAS**, **SACOS** y **CARTUCHOS**. La lista de abajo muestra cada código con su hora. El total de materiales aparece en negro al final.

### Accesorios

La pantalla de accesorios pide vale, empresa, lugar, transportista, quién recibe, quién entrega, turno y voladura. En el escaneo, la cantidad se cuenta en **PZA** o **MTS**.

### Devolución de alto

Aquí solo indicas quién entrega y quién recibe en el polvorín. Después escaneas lo que regresa. El saldo se suma de nuevo al inventario.

### Inventario y filtros

Arriba ves cinco tarjetas: Alto, Agente, Cordón, Cañuelas y Detonadores. Tocas una y se filtra la lista. Debajo dice cuántos SKUs hay, cuántos están calibrados y cuántos movimientos UUID hay pendientes.

El buscador acepta ID, producto, familia o código `X...`. Los chips de filtro son **Todas**, **Alto Exp.**, **Agente**, **Cordón**, **Cañuelas** y **Detonadores**. Si quitas un filtro, tocas **Todas**.

### Estado vacío

Cuando no hay existencias, sale un recuadro con el mensaje «Polvorín sin existencias registradas» y un botón para cargar el inventario inicial.

### Diálogo de carga rápida

Se abre desde el botón fijo de abajo o desde **Ajustar** en una tarjeta. Pides el producto, eliges carga inicial o ajuste, y capturas cajas, sacos, cartuchos, piezas o metros. Si aplica, también peso y cartuchos por caja.

### Menú de Inventario

Los tres puntos de arriba abren el menú. Ahí está **Licencias**, que lleva a la bóveda. El botón de flechas abre el Centro de Sincronización.

### Licencias guardadas

Muestra el Device ID de la terminal y las tarjetas de licencia. Cada tarjeta dice estado, tipo, vigencia y terminal destino. Las acciones son Instalar, Copiar, Compartir y Eliminar.

### Sincronización

Tiene tres pestañas: **Enviar**, **Recibir** e **Historial**. En Enviar ves los chips de alcance (**Pendientes**, **24 horas**, **Todo**) y los de densidad (**Alta**, **Fácil**). El QR se muestra en pantalla con su número de parte.

### Historial

Lista de vales con folio, fecha y empresa. Abajo está **EXPORTAR CONSOLIDADO**. Si seleccionas varios, el botón cambia a **EXPORTAR SELECCIONADOS**.

### Configuración general

Trae el dispositivo, la licencia, datos de la mina, catálogo, impresoras y versión. Los interruptores de sonido, vibración y destello están arriba.

### Impresoras

Cada perfil muestra alias, MAC, protocolo y ancho en pulgadas. El botón **+ Agregar** crea uno nuevo. El perfil activo es el que se usa al imprimir.

### Devolución de accesorios

La misma pantalla de accesorios, pero la cantidad se guarda en negativo. El encabezado pide quién entrega y quién recibe en el polvorín. Después capturas cada accesorio con su cantidad.

### Catálogo de productos

Se abre desde Configuración con **Ver Catálogo Completo y Consultar Códigos**. Aquí ves los productos activos, puedes buscar por ID o nombre, editar uno y ver sus códigos. Si el catálogo cambió, se recarga con **Recargar Base** o se trae un CSV con **Importar CSV**.

### Hoja de producto

Al tocar una tarjeta en Inventario se abre esta hoja. Trae dos pestañas: **Kardex** con los movimientos y **Códigos** con las series escaneadas. Arriba muestra el producto, su familia, la existencia actual y el peso unitario. El botón **Ajustar** abre la carga rápida para ese producto.

### Detalle de vale

Desde el Historial puedes abrir un vale. La vista muestra el encabezado, el listado de códigos y los totales. Desde ahí se reimprime o se borra. El botón de imprimir usa la impresora activa; no necesitas volver a capturar el vale.

### Activación de licencia

La pantalla de licencia muestra el Device ID, el estado de la licencia, el cliente, la mina, la modalidad y la vigencia. Abajo está el campo para pegar una clave o token, con botones para pegar, importar `.lic` y **ACTIVAR LICENCIA**.

### Diálogos y avisos

Estas ventanas no son pantallas completas, pero también aparecen:

- **Salir de escaneo**: pregunta si quieres salir con lecturas capturadas. Elige cancelar para seguir capturando o confirma para perder lo que no guardaste.
- **Eliminar en dos pasos**: pide un código de 4 dígitos antes de borrar un vale o un producto.
- **Seleccionar impresora**: aparece si hay varias impresoras guardadas. Elige una y queda como activa para ese trabajo.
- **Reimprimir vale**: lista los vales guardados para volver a imprimir uno.
- **Editar producto**: abre desde el catálogo para corregir nombre, familia, presentación o calibraciones.
- **Destello de escaneo**: un borde verde, ámbar o rojo confirma lectura válida, duplicado o error. No es una pantalla, solo un aviso visual.
- **Código repetido**: si lees dos veces el mismo código, aparece una fila roja con la insignia **DUPLICADO** en la lista. No cuenta para el total, los kilos ni el vale; el aviso se quita solo cuando capturas un código nuevo válido. Puedes borrar esa fila con el ícono de basura.

## Qué significa cada elemento dentro de las pantallas

Estas notas usan los datos de prueba de las capturas: proveedor `11`, polvorín `2`, factura `FAC-DEMO-001` y tres lecturas capturadas.

### Encabezado de entrada

- **Número de Proveedor**: quién entrega el material. En el ejemplo es `11`.
- **Número de Polvorín**: dónde entra. En el ejemplo es `2`.
- **Factura o Remisión**: documento de soporte. En el ejemplo es `FAC-DEMO-001`.
- **CONTINUAR A ESCANEO**: pasa a la pantalla de lecturas. Si falta un campo, aparece el aviso en rojo.

### Escaneo de entrada

- **TOT 3**: total de códigos capturados en este vale.
- **C 2**: cajas. En el ejemplo hay dos cajas.
- **S 1**: sacos. En el ejemplo hay un saco.
- **U 0**: cartuchos sueltos.
- **Info**: despliega o esconde el encabezado con proveedor y factura.
- **Campo Escanear o escribir código**: ahí cae lo que lee el Zebra o lo que teclees.
- **Lectura correcta**: confirma qué código entró y con qué presentación.
- **Filtros Todos (3), Alto Exp. (2), Agente (1)**: el número entre paréntesis es cuántos códigos hay en esa familia.
- **FILTRADOS: 1 DE 3**: estás viendo una parte de las tres lecturas.
- **Ver todos**: quita el filtro activo.
- **Tarjeta del código**: muestra tipo (Saco), producto (AUSTINITE 15 CA-1), medida, hora y el UUID del evento.
- **Papelera roja**: borra esa lectura del vale.
- **Imprimir**: manda el ticket a la impresora activa sin cerrar el vale.
- **Guardar Entrada (3)**: cierra el vale con las tres lecturas. El número cambia con cada código.

### Inventario y filtros

- **Cinco tarjetas superiores**: cada una es una familia SEDENA. Se tocan para filtrar.
- **0 SKUs · 0 calibrados**: cuántos productos hay y cuántos tienen peso unitario configurado.
- **UUID: 0 mov. · 0 cód.**: eventos y códigos listos para sincronizar con otra terminal.
- **Buscador**: acepta ID, producto, familia o código `X...`.
- **Chips Todas / Alto Exp. / Agente / Cordón / Cañuelas / Detonadores**: filtran la lista.
- **Botón fijo CARGA RÁPIDA / AJUSTE**: abre el diálogo para cargar o corregir existencias.
- **Tres puntos**: abre el menú con la opción **Licencias**.

### Diálogo de carga rápida

- **Campo de producto**: recibe el código escaneado o permite elegir del catálogo.
- **Modo carga inicial / ajuste físico**: indica si estás abriendo existencias o corrigiendo un saldo.
- **Cajas completas, sacos completos, cartuchos sueltos**: cantidades por presentación.
- **Peso caja / peso saco / cartuchos por caja**: calibraciones para convertir a kilogramos.
- **Observación**: turno, auditoría o nota de por qué se ajustó.

### Sincronización

- **Enviar / Recibir / Historial**: pestañas del centro de sincronización.
- **Pendientes / 24 horas / Todo**: alcance de los eventos que se van a empaquetar.
- **Alta (1,400) / Fácil (600)**: densidad del QR. Alta = menos QRs; Fácil = QRs más grandes.
- **QR con número de parte**: si el lote es grande, salen varios. Se pueden leer en cualquier orden.
- **EXPORTAR ARCHIVO .JSON**: respaldo para USB o para pasar a otra terminal sin cámara.
- **PROCESAR BLOQUE / JSON**: botón de la pestaña Recibir que fusiona lo leído.

### Historial

- **Folio y fecha**: identifican el vale.
- **Empresa / transportista**: aparecen en la tarjeta del vale.
- **EXPORTAR CONSOLIDADO**: junta todos los vales en un archivo.
- **EXPORTAR SELECCIONADOS**: aparece al marcar varios vales.
- **Código de 4 dígitos**: se pide al borrar para evitar borrados accidentales.

### Configuración e impresora

- **Interruptores de audio, vibración y destello**: avisos industriales para lectura válida, duplicado y error.
- **Nombre de la Mina / Empresa y Permiso SEDENA**: salen impresos en el ticket.
- **Ver Catálogo Completo**: abre la lista de productos.
- **Importar CSV**: carga un catálogo nuevo.
- **Alias / Nombre en polvorín**: nombre que le pones a la impresora.
- **Dirección MAC Bluetooth**: dirección de la impresora vinculada.
- **Protocolo y papel**: ESC/POS, CPCL o texto plano; 2, 3 o 4 pulgadas.
- **Usar como impresora activa**: define cuál se usa al imprimir.

## Ejemplo de ticket

Así se ve un vale de entrada impreso en papel térmico de 3 pulgadas. El margen de abajo deja espacio para las firmas.

Los datos son de ejemplo: vale `E-2026-118`, factura `FAC-2026-118`, 12 cajas de AUSTINITE y 3 sacos de EMULEX. El total es 15 piezas. No copies estos datos en un vale real.

## Diagramas de flujo y guía visual de controles

Aquí va lo que más se usa, dibujado. Si estás capacitando a alguien nuevo, empieza por aquí.

### Flujo de una entrada

Recibir material y dejarlo registrado.

1. Encabezado: proveedor, polvorín y factura.
2. Escaneo: códigos de cajas, sacos o cartuchos.
3. Resumen: totales por presentación.
4. Guardado: el vale queda en el historial.
5. Impresión: ticket con firma.

En corto: Encabezado → Escaneo → Totales → Guardar → Imprimir.

### Flujo de un despacho

Sacar material del polvorín hacia un barreno.

1. Vale: empresa, lugar, transportista, turno.
2. Escaneo: lo que se entrega.
3. Validación: duplicados y saldos.
4. Guardado: eventos UUID.
5. Impresión: entrega y recepción.

En corto: Vale → Escaneo → Validación → Guardar → Imprimir.

### Flujo de ajuste físico

Corregir el saldo real de un producto.

1. Inventario: botón de abajo o **Ajustar** en la tarjeta.
2. Producto: escaneas o eliges del catálogo.
3. Modo: inicial o ajuste.
4. Cantidades: unidades y pesos.
5. Guardado: sale el nuevo saldo.

En corto: Inventario → Producto → Modo → Cantidades → Guardar.

### Flujo de sincronización

Pasar eventos a otra terminal sin internet.

1. Alcance: Pendientes, 24 horas o Todo.
2. Densidad: Alta o Fácil.
3. Paquete: QR V4 o JSON.
4. Recepción: la otra terminal lee.
5. Informe: fusiones y conflictos.

En corto: Alcance → Densidad → QR/JSON → Recepción → Informe.

### Flujo de licenciamiento

Autorizar la terminal con una licencia hecha fuera de la app.

1. Copiar el Device ID.
2. Generar en License Master Pro.
3. Activar con clave, token o QR.
4. Guardar en la bóveda con un alias.
5. Instalar en la terminal correcta.

En corto: Device ID → Generación → Activación → Bóveda → Instalación.

### Qué es cada control importante

Si no sabes para qué sirve un botón, mira aquí.

| Control | Dónde aparece | Para qué sirve |
|---|---|---|
| Device ID | Configuración y Licencias guardadas | Identifica la terminal. La licencia se hace para ese ID. |
| Barra CARGA RÁPIDA / AJUSTE | Inventario | Abre el diálogo para cargar o corregir existencias. |
| Cinta de métricas | Escaneo | Totales de ítems, cajas, sacos y cartuchos. |
| Filtros de familia | Inventario | Muestran Alto, Agente, Cordón, Cañuelas o Detonadores. |
| Kardex | Tarjeta de producto | Historial de movimientos de ese producto. |
| Códigos | Tarjeta de producto | Series escaneadas de ese producto. |
| QR V4 | Sincronización | Eventos firmados con HMAC-SHA256. |
| Perfil de impresora | Configuración | Protocolo, ancho de papel y MAC Bluetooth. |
| Bóveda de licencias | Menú de Inventario | Guarda, instala, exporta e importa licencias. |
| Informe de fusión | Sincronización | Cuántos eventos entraron y qué conflictos hay. |

## Estructura del programa

Esta parte no es para operar la app, sino para entender cómo está armada. Sirve cuando hay que dar soporte o pedir un cambio.

La app es nativa en Kotlin con Jetpack Compose. Todo se guarda en el celular con Room (SQLite). No hay servidor. Las terminales se pasan datos con QR o JSON.

### Puntos de entrada

- `MainActivity.kt`: abre la app, arma la navegación, pide permisos y conecta las pantallas.
- `TTExploApp.kt`: crea la base de datos y el repositorio principal cuando arranca la app.
- `ui/navigation/AppNavigation.kt`: define las rutas. Cada ruta lleva a una pantalla.

### Capa de pantallas

- `ui/screens/home`: menú principal de ocho módulos.
- `ui/screens/entradas`, `despacho`, `accesorios`, `devolucion`: captura de vales. Cada uno tiene pantalla de encabezado, pantalla de escaneo y su ViewModel.
- `ui/screens/inventory`: inventario, Kardex, carga rápida y sincronización.
- `ui/screens/license`: activación de licencia y bóveda.
- `ui/screens/history`: consulta y exportación de vales.
- `ui/screens/settings`: mina, catálogo, impresoras, audio y versión.
- `ui/common`: piezas reutilizables: textos que se ajustan, tarjetas de escaneo, diálogos, sonido y destello.
- `ui/theme`: colores, fuentes y formas de Material 3.

### Capa de datos

- `data/local/AppDatabase.kt`: base Room con sus migraciones.
- `data/local/dao`: consultas a eventos, códigos, productos, vales y sesiones.
- `data/local/entity`: tablas de la base.
- `data/repository/InventoryRepository.kt`: reúne inventario, vales, eventos y sincronización.
- `data/repository/SettingsRepository.kt`: preferencias de mina, impresora y sonido.
- `data/repository/CatalogRepository.kt`: catálogo de productos.

### Reglas de negocio

- `domain/model`: modelos de movimientos y clasificación de productos.
- `domain/inventory`: cálculo de peso unitario, análisis de conflictos y códecs de sincronización.
- `domain/export`: formato de archivo plano.
- `domain/scanner`: validación de códigos.

### Impresión, licencia y escáner

- `printer/TicketGenerator.kt`: arma tickets en ESC/POS, CPCL y texto plano.
- `printer/BluetoothPrinterManager.kt`: conexión y envío a la impresora.
- `printer/ValePrintHelper.kt`: une el vale con el perfil de impresora.
- `license/LicenseManager.kt`: valida firmas, activa licencias y revisa vigencia.
- `license/LicenseVault.kt`: guarda licencias por terminal.
- `scanner/ZebraScannerReceiver.kt`: recibe los broadcast del lector Zebra.

### Cómo fluye la información

La pantalla llama al ViewModel. El ViewModel llama al repositorio. El repositorio escribe o lee Room. Para imprimir, el repositorio usa el paquete `printer`. Para sincronizar, usa `domain/inventory` y genera QR o JSON. La licencia se valida en `license` antes de habilitar la operación.

En corto: Pantalla → ViewModel → Repositorio → Room. De ahí sale a Impresión, Licencia o Sincronización.

## Buenos hábitos

- Cierra el vale antes de irte. No dejes cosas en borrador.
- Revisa el Device ID antes de pedir otra licencia.
- Exporta la bóveda si vas a cambiar de terminal.
- Mira los conflictos antes de borrar vales.
- Si imprimes, revisa que haya papel y batería.

## Problemas comunes

### La app pide permisos de Bluetooth

Acepta el permiso de dispositivos cercanos. Lo necesita para encontrar la impresora y para algunas lecturas.

### La impresora no imprime

Revisa que esté encendida, vinculada en Android y activa en la app. Si usas CPCL o ESC/POS, confirma protocolo y ancho de papel en el perfil.

### Un QR no se reconoce

Pega la cadena completa, sin espacios ni saltos. Si viene de una terminal vieja, aceptamos `TTEX3` y `TTEXSYNC|V2|`. Una terminal vieja, en cambio, no sabe leer `TTEX4`.

### La licencia aparece vencida

Genera otra en License Master Pro para el Device ID actual y actívala desde Licencias o desde la bóveda.

## Soporte

Si algo falla, manda esto:

- Device ID de la terminal.
- Versión, por ejemplo `v2.6.0 (Build 27)`.
- Número de vale o ID de producto.
- Hora del evento y nombre de la mina.
- Captura del error, si salió.
