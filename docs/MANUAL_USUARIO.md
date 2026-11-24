# Manual del usuario — T&T Explo Mobile

T&T Explo Mobile 2.6.0 (build 21) es la app del polvorín. Sirve para lo de todos los días: activar la terminal, capturar vales, ver existencias y pasar datos a otra terminal.

Funciona sin internet. Lo que capturas queda guardado en la terminal y se comparte con QR o con un archivo JSON.

## Antes de empezar

Cada terminal tiene su ID (Device ID). Anótalo bien: la licencia se hace para ese ID y no sirve en otra terminal.

### Activar la licencia

1. En el inicio, abre **8. Configuración y Personalización**.
2. Copia el **Device ID** con **COPIAR ID**.
3. En License Master Pro genera la licencia para ese ID.
4. En la app pega la clave corta `TTEX-...`, el token `TTEX:...`, importa el `.lic` o escanea el QR.
5. Toca **ACTIVAR LICENCIA**.

Si salió bien, verás **AUTORIZADA**. Si dice **PRÓXIMA A VENCER**, renueva antes de la fecha que marca.

> [!IMPORTANT]
> La APK viene con una licencia de prueba de 1 día. Mientras esté activa, verás los paneles **MODO PRUEBA**. Cuando metas una licencia real, esos paneles desaparecen. Es normal, no es un error.

## Pantalla de inicio

Arriba verás `v2.6.0`. Debajo están los ocho módulos. Tocas y entras.

| Módulo | Para qué lo usas |
|---|---|
| **1. Entradas de Alto y Agente** | Recibir material y darlo de alta. |
| **2. Salidas de Alto y Agente** | Despachar a barreno e imprimir el vale. |
| **3. Salida de Accesorios** | Despachar detonadores, cordón y artificios. |
| **4. Devolución de Alto y Agente** | Meter de vuelta lo que sobró. |
| **5. Devolución de Accesorios** | Meter de vuelta accesorios con cantidad negativa. |
| **6. Inventario en Polvorín (En Vivo)** | Ver saldos, ajustar y consultar Kardex. |
| **7. Historial de Vales & Consolidado** | Buscar vales y exportarlos. |
| **8. Configuración y Personalización** | Impresora, mina, catálogo y licencia. |

El engranaje de arriba abre Configuración. Si la licencia está por vencer, verás un aviso arriba.

## Registrar una entrada

1. Abre **1. Entradas de Alto y Agente**.
2. Llena proveedor, polvorín y factura o remisión.
3. Toca **CONTINUAR A ESCANEO**.
4. Escanea cada código SEDENA. Si el lector falla, escrib el código a mano en el campo de captura.
5. Mira la cinta de arriba: ahí ves cuántos ítems llevas y cuántas cajas, sacos y cartuchos.
6. Si te equivocaste en uno, abre el ítem en la lista y corrigelo o bórralo.
7. Toca **GUARDAR**. Después puedes imprimir el ticket desde la pantalla del vale.

Si se te cierra la app a mitad de vale, no perdiste lo capturado. Al volver verás **Vale en progreso recuperado**.

## Registrar una salida de alto y agente

1. Abre **2. Salidas de Alto y Agente**.
2. Llena vale, empresa, lugar, transportista, turno y voladura o mina.
3. Toca **CONTINUAR A DESPACHO**.
4. Escanea los sacos o cajas que salen.
5. Revisa el total y el desglose por presentación.
6. Guarda e imprime con la impresora activa.

El ticket trae las dos líneas, quién entrega y quién recibe, y espacio de sobra para la firma.

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

## Usar el simulador de escaneo

Mientras la terminal siga con la licencia de prueba, verás un panel **MODO PRUEBA** en las pantallas de escaneo. Sirve para practicar sin escáner.

1. Despliega el panel **MODO PRUEBA**.
2. Elige el caso: válido, duplicado, error SEDENA u otro.
3. Manda el código al flujo de escaneo.
4. Cierra el panel cuando termines.

Esto es solo para capacitación. No lo uses para material real. Con licencia real, el panel ya no aparece.

## Inventario en polvorín

Aquí ves lo que hay en el polvorín: saldo por familia, KG, PZA, MTS y si hay eventos pendientes de pasar a otra terminal.

### Consultar existencias

1. Abre **Inventario en Polvorín**.
2. Toca una de las cinco tarjetas para filtrar: Alto, Agente, Cordón, Cañuelas o Detonadores.
3. Escribe un ID, producto, familia o código `X...` en el buscador.
4. Toca **Todas** para quitar el filtro.
5. En una tarjeta de producto, **Kardex** te muestra los movimientos y **Códigos SEDENA** las series.

### Hacer carga rápida o ajuste físico

El botón **CARGA RÁPIDA / AJUSTE** va fijo abajo. Ya no tapa la lista.

1. Toca **CARGA RÁPIDA / AJUSTE**.
2. Escanea el producto o elígelo del catálogo.
3. Elige: carga inicial o ajuste físico.
4. Pon cantidades de cajas, sacos, cartuchos, piezas o metros, según el caso.
5. Si aplica, pon peso de caja, peso de saco y cartuchos por caja.
6. Añade una observación de turno o auditoría si hace falta.
7. Guarda.

También puedes abrirlo desde la tarjeta del producto, con **Ajustar**.

### Abrir la bóveda de licencias

1. En Inventario, toca los tres puntos de arriba.
2. Elige **Licencias**.
3. Verás el Device ID de esta terminal y las licencias guardadas.

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
4. Después de instalar, los modos de prueba desaparecen.

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

1. Abre **7. Historial de Vales & Consolidado**.
2. Busca por folio, fecha, empresa o transportista.
3. Toca un vale para verlo, reimprimirlo o borrarlo.
4. Para exportar, usa **EXPORTAR CONSOLIDADO** o selecciona varios y usa **EXPORTAR SELECCIONADOS**.

Borrar no es de un toque: pide un código de 4 dígitos. Esa molestia es a propósito.

## Configuración

En **8. Configuración y Personalización** está lo de siempre: mina, catálogo, impresoras y licencia.

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
- Papel: 58 mm, 80 mm o 104 mm.
- El botón para dejarla como impresora activa.

Antes de imprimir, enciende Bluetooth y acepta el permiso de dispositivos cercanos cuando la app lo pida.

### Estado de licencia y versión

Aquí ves el Device ID, el cliente autorizado, la mina, la vigencia, la versión instalada y qué lector y salida de ticket soporta la terminal.

## Diagramas de flujo y guía visual de controles

Aquí va lo que más se usa, dibujado. Si estás capacitando a alguien nuevo, empieza por aquí.

### Flujo de una entrada

Recibir material y dejarlo registrado.

1. Encabezado: proveedor, polvorín y factura.
2. Escaneo: códigos SEDENA de cajas, sacos o cartuchos.
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
| Códigos SEDENA | Tarjeta de producto | Series escaneadas de ese producto. |
| QR V4 | Sincronización | Eventos firmados con HMAC-SHA256. |
| Perfil de impresora | Configuración | Protocolo, ancho de papel y MAC Bluetooth. |
| Modo prueba | Escaneo | Simula lecturas. Se va con la licencia real. |
| Bóveda de licencias | Menú de Inventario | Guarda, instala, exporta e importa licencias. |
| Informe de fusión | Sincronización | Cuántos eventos entraron y qué conflictos hay. |

## Buenos hábitos

- Cierra el vale antes de irte. No dejes cosas en borrador.
- Revisa el Device ID antes de pedir otra licencia.
- Exporta la bóveda si vas a cambiar de terminal.
- Mira los conflictos antes de borrar vales.
- No uses **MODO PRUEBA** con material real.
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

### Desaparecieron los paneles de prueba

Es lo correcto. Significa que ya hay una licencia real instalada. Los paneles solo estaban con la licencia de fábrica.

## Soporte

Si algo falla, manda esto:

- Device ID de la terminal.
- Versión, por ejemplo `v2.6.0 (Build 21)`.
- Número de vale o ID de producto.
- Hora del evento y nombre de la mina.
- Captura del error, si salió.
