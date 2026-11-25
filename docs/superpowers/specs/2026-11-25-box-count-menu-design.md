# Diseño: menú de conteo de cajas

## Intención

El operador necesita contar las piezas que realmente hay dentro de una caja y registrar ese conteo, porque las personas se equivocan y el número calculado por el rango del código no siempre coincide. El conteo debe quedar ligado al código de la caja, al KP/línea de producción y a la fecha y hora del conteo. Al terminar se exporta un TXT con el formato que ya usa el operador.

Ejemplo real: `CAJ355071026008799008854005399631,2,55,2026-11-25 12:41:59`.

Formato del TXT exportado (archivo `ttexplo2026-11-25.txt`):

```
CAJ
355071026008799008854005399631,2,55,2026-11-25 12:41:56
```

Primera línea fija `CAJ`; una línea por caja con `codigo,KP,piezas,fechaHora`; línea en blanco al final.

## Approaches

- **A. Tarjeta nueva «9. Conteo de cajas» en el menú principal.** Flujo propio, visible desde el inicio. Es lo que el operador pidió (“falta un menú”). Costo: nueva ruta, nueva pantalla y un parser.
- **B. Botón dentro de «6. Inventario en Polvorín».** Menos navegación nueva, pero el conteo queda escondido dentro de otra función.
- **C. Pedir el conteo dentro de Entradas/Despacho.** Mezcla conteo de existencias con vales; se descarta.

## Decisión

**A**, con KP y piezas editables. La tarjeta nueva va después de la opción 8 en `HomeScreen.kt`.

## Comportamiento

1. El operador escanea el código (lector Zebra/Munbyn vía `ZebraScannerReceiver`) o lo escribe en el campo manual.
2. Se acepta el código de 30 dígitos solo, o con prefijo `CAJ` y sufijos opcionales `,KP,piezas,fechaHora`.
3. Se muestra:
   - código, producto y fecha de fabricación derivados de los 30 dígitos;
   - KP/línea: campo editable, prellenado con el valor leído si viene en el código;
   - piezas contadas: campo editable, prellenado con el valor leído si viene en el código;
   - rango calculado `final − inicial + 1` como referencia, no como verdad.
4. Validaciones pensadas para error humano:
   - **no se permiten códigos repetidos**: si el código ya está en la sesión, aviso «CÓDIGO REPETIDO» y la lectura no se agrega (mismo patrón que Entradas/Despacho);
   - piezas solo dígitos, entre 1 y 500 (`MAX_CARTRIDGES_PER_BOX`);
   - KP obligatorio;
   - si las piezas tecleadas difieren del rango calculado, aviso: «Tecleaste X pero el rango indica Y. ¿Es correcto?» con **Sí, son X** y **Corregir**;
   - botón **Volver a leer** para repetir la lectura.
5. **Confirmar conteo** agrega la línea `<code>,<KP>,<piezas>,<fechaHora>` a la lista de la sesión.
6. **Exportar TXT** escribe `CAJ` en la primera línea, una línea por caja contada y una línea en blanco final, con nombre `ttexplo<YYYY-MM-DD>.txt`. No incluye leyendas, totales ni encabezados extra.

## Archivos a tocar

- `ui/screens/home/HomeScreen.kt`: tarjeta «9. Conteo de cajas».
- `ui/navigation/AppNavigation.kt`: ruta nueva.
- `ui/screens/inventory/BoxCountScreen.kt` (nuevo): UI y campos editables.
- `domain/inventory/BoxCountParser.kt` (nuevo): parser del formato extendido y del rango.
- `domain/inventory/BoxCountTxtExporter.kt` (nuevo): genera el TXT con `CAJ`, líneas y salto final.
- Pruebas: parser (incluye el ejemplo real, KP y piezas editadas), validación de rango, discrepancia, rechazo de repetidos y exportación byte por byte.
- Manual: sección corta del nuevo menú sin capturas nuevas.

## Fuera de alcance

- No cambia la validación de fecha de fabricación ni el formato de sincronización `TTEX4`.
- No modifica vales de Entradas/Despacho/Devolución.
- No imprime ticket nuevo.
