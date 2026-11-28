# Ticket con identificación de terminal — diseño

**Fecha:** 2026-11-27
**Versión objetivo:** 2.8.1 (build 39)
**Estado:** aprobado en conversación (el usuario pidió la opción más práctica y con menos código)

## Intención

Cada vale impreso debe mostrar de qué terminal salió. Pueden operar dos o
más terminales al mismo tiempo y hoy el ticket no permite atribuir el vale a
su origen ni saber qué información generó cada terminal.

## Alcance

- Vale de despacho, entrada a polvorín, accesorios/salida, devolución y
  conteo en caja.
- Se imprime una línea `TERMINAL: <deviceId>` al inicio del bloque de datos
  del vale (después del divisor de cabecera y antes del primer dato).
- Aplica a impresión real, vista previa en pantalla y vista previa del
  dashboard web (`/vale`), porque todas pasan por `ValePrintHelper`.
- Fuera de alcance: ticket de prueba, exportación `.txt`, registros de
  movimiento y sincronización.

## Enfoque aprobado

1. `TicketGenerator`: nuevo parámetro `terminalId: String = ""` en los
   generators de los cinco tipos de vale (EscPos, Plain y Cpcl). La línea se
   omite cuando el parámetro está vacío, así los tests y llamadas existentes
   siguen igual.
2. `ValePrintHelper`: resuelve `LicenseManager.getFormattedDeviceId(context)`
   y lo pasa a cada generator (impresión y previsualización).
3. Identificador: el device ID técnico existente (ej. `ZBR-POLV-01`), el mismo
   que ya usan los eventos de sincronización.

## Alternativas descartadas

- Meter `deviceId` en `DespachoRecord`/etc.: toca viewmodels y tests, y la
  identidad ya es a nivel dispositivo.
- Cambiar solo el servidor o solo la impresora: incompleto; el preview debe
  coincidir con lo impreso y el dashboard se alimenta del mismo funnel.

## Archivos afectados

- `app/src/main/java/com/ttexplo/mobile/printer/TicketGenerator.kt`
- `app/src/main/java/com/ttexplo/mobile/printer/ValePrintHelper.kt`
- `app/src/test/java/com/ttexplo/mobile/printer/TicketGeneratorTest.kt` (una
  verificación pequeña)

## Verificación

- Tests existentes de ticket en verde.
- Test nuevo: la línea aparece con id y se omite con cadena vacía.
- Compilar, instalar y confirmar en la vista previa del vale.
