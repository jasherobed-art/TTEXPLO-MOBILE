# Margen inferior adicional en tickets térmicos — diseño corto

## Intento
El responsable reportó que, en la impresora física MUNBYN, la firma del ticket queda demasiado justa en el borde inferior. Se quiere ~3 cm más de papel después de la firma antes del corte.

## Objetivos
- Añadir ~3 cm de espacio en blanco **después de la última firma**, sin alterar contenido, fuentes ni comandos de corte.
- Aplicar a **todos** los tickets: Entrada, Despacho, Accesorios y Devolución, en sus 3 formatos (ESC/POS, texto plano, CPCL) y a los 3 tickets de prueba.

## No objetivos
- No cambiar el texto de las firmas ni las líneas de firma.
- No tocar la configuración de impresora ni el `BluetoothPrinterManager`.
- No cambiar el corte del papel (no está en `TicketGenerator`).

## Decisiones (opción 2, elegida por el humano)
- Una sola constante `BOTTOM_MARGIN_ESC_LINES = 7` en `TicketGenerator`, documentada como ~3 cm en papel térmico de 80 mm (≈4 mm/línea).
- ESC/POS: mantener el `ESC_FEED_6_LINES` actual (5 usos) y **añadir** 7 líneas nuevas de margen. Total al final: 13 líneas de avance.
- Texto plano: 7 saltos `\r\n` adicionales tras los 6 actuales.
- CPCL: `totalHeight += 220` dots (≈3 cm a ~7,2 dots/mm en 80 mm). No se añade línea `TEXT` vacía: la altura de página ya reserva el papel en blanco y una línea vacía podría imprimir un carácter visible.
- Un único helper `writeEscPosBottomMargin(out)` / `appendPlainBottomMargin(sb)` para no repetir literales; CPCL solo cambia `totalHeight`.

## Restricciones
- `CHARSET` sigue siendo US-ASCII; el margen son solo bytes de control/saltos.
- Cero cambios en firma digital, licencias ni QR.
- Misma firma de APK: se recompilará `app2.5.0-ttexplo.apk` y se reinstalará en el MUNBYN con `adb install -r` (sin borrar datos).

## Ajuste fino
La conversión mm←líneas/dots es aproximada. Tras instalar, imprimir un ticket de prueba en el MUNBYN; si queda corto o largo, ajustar solo la constante.

## Enfoques considerados
1. Solo ESC/POS (mínimo) — descartado: el problema reaparecería en texto plano y CPCL.
2. **Todos los tickets y formatos con una constante única** — elegido.
3. Configurable por ajustes de impresora — descartado: sobre-ingeniería para un valor fijo.

## Estado
Pendiente de aprobación del humano.
