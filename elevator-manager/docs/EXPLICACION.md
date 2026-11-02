# Explicación detallada del proyecto Elevator Manager

Este documento explica **cómo se resolvió** el proyecto, paso a paso, como lo haría un
programador senior revisándolo contigo. La idea es que entiendas cada decisión para que
puedas defenderla y modificarla tú mismo.

---

## Índice

1. [Cómo pensar el problema](#1-cómo-pensar-el-problema)
2. [Mapa: requisito del enunciado → dónde está en el código](#2-mapa-requisito--código)
3. [Arquitectura general](#3-arquitectura-general)
4. [Hilos: `SimulationProcess` y la herencia](#4-hilos-simulationprocess-y-la-herencia)
5. [El elevador (`Elevator`)](#5-el-elevador)
6. [El algoritmo de movimiento LOOK](#6-el-algoritmo-de-movimiento-look)
7. [El Elevator Manager y cómo elige elevador](#7-el-elevator-manager-y-cómo-elige-elevador)
8. [Protocolo de comunicación por texto (punto extra)](#8-protocolo-de-comunicación-punto-extra)
9. [Reset](#9-reset)
10. [Pausa y reanudación (punto extra)](#10-pausa-y-reanudación-punto-extra)
11. [Logging](#11-logging)
12. [Carga de archivo de requerimientos](#12-carga-de-archivo-de-requerimientos)
13. [Interfaz gráfica y por qué no se rompe con los hilos](#13-interfaz-gráfica)
14. [Concurrencia: los problemas que se evitaron](#14-concurrencia-problemas-evitados)
15. [Cómo se probó](#15-cómo-se-probó)
16. [Recorrido de un ejemplo completo](#16-recorrido-de-un-ejemplo)
17. [Ideas para mejorar / preguntas típicas del catedrático](#17-preguntas-típicas)

---

## 1. Cómo pensar el problema

Antes de escribir código conviene separar el problema en **actores**:

| Actor              | ¿Qué hace?                                                        | ¿Es un hilo? |
|--------------------|-------------------------------------------------------------------|--------------|
| Elevador (×N)      | Se mueve piso por piso, para, abre puertas, obedece su cola       | **Sí**       |
| Elevator Manager   | Recibe llamadas de los pisos y decide qué elevador va             | **Sí**       |
| Usuario            | Presiona botones, escribe comandos, carga archivos                | GUI (EDT)    |
| Lector de archivo  | Va metiendo los requerimientos del archivo según su tiempo        | **Sí**       |

El enunciado insiste en que los elevadores y el manager son **procesos independientes**.
En Java eso se traduce en **un `Thread` por cada uno**. Y como varios hilos van a tocar los
mismos datos, el diseño gira alrededor de una regla:

> **Cada hilo es dueño de su propio estado. Los demás solo le mandan mensajes (comandos a
> una cola) o le piden una copia de su estado (snapshot).**

Con esa regla casi desaparecen los errores típicos de concurrencia.

---

## 2. Mapa requisito → código

| Requisito del enunciado | Dónde / cómo |
|---|---|
| Varios elevadores, cada uno proceso independiente | `elevator/Elevator.java` extiende `SimulationProcess` (que extiende `Thread`). Uno por elevador. |
| ID, Dirección (inicia "hacia arriba"), Piso actual (inicia 1; si se mueve, el que acaba de dejar) | Campos `id`, `direction = UP`, `currentFloor = 1` en `Elevator`. `currentFloor` se actualiza **al terminar** el viaje (`moveOneFloor`). |
| Cola de comandos propia, modificable por (1) manager, (2) botón interno, (3) reset | `BlockingQueue<Command> inbox` en `Elevator`. Comandos `PickupCommand`, `GotoCommand`, `ResetCommand`. |
| Tiempo de movimiento y de espera en ms, ingresados al iniciar | `core/SimulationConfig` + `gui/ConfigDialog`. |
| Log de: sube/baja, llega, para a recoger/dejar, reset, cambio de dirección | `Elevator.moveOneFloor`, `stopAtCurrentFloor`, `reset`, `changeDirection`. |
| Un archivo de log por elevador (`elevador1.log`, …) con el ID | `logging/LogFactory` → `logs/elevadorN.log`; el formato incluye `[Elevador N]`. |
| Log también en la línea de comandos | `logging/StdoutHandler`. |
| Manager elige el elevador más eficiente | `manager/ElevatorManager.dispatchHallCall` + `manager/CostEstimator`. |
| Dos botones por piso compartidos (subir/bajar) | `gui/FloorCallPanel`; el manager evita asignar dos veces la misma llamada (`isHandlingHallCall`). |
| Escalable 1..N elevadores | Todo es `List<Elevator>`; no hay nada fijo a 2 o 3. |
| Si va hacia arriba no regresa por alguien; primero sube a donde iba | `elevator/LookAlgorithm` (algoritmo LOOK). Probado (ver §15). |
| Recoge en el camino a quien va en la misma dirección | `LookAlgorithm`: para si hay llamada en el piso **en la misma dirección**. |
| Manager es proceso independiente con acceso a los elevadores | `ElevatorManager` extiende `SimulationProcess`; tiene la lista de elevadores y lee sus `snapshot()`. |
| Consola: Reset del sistema | Botón **Reset sistema** / comando `RESET` → `ElevatorManager.resetSystem`. |
| Consola: requerimiento externo (piso+dirección) e interno (piso dentro del elevador) | Botones ▲▼ y botones numerados; comandos `CALL` y `GOTO`. |
| Consola: upload de lista de requerimientos solo en estado de reset | **Cargar archivo…** → verifica `ElevatorManager.isAtResetState()`; `io/RequestFileLoader` + `io/RequestFilePlayer`. |
| Consola: logs constantes | Panel de logs (`GuiLogHandler`). |
| Consola: Start y Stop con botón | **▶ Iniciar** / **■ Detener**. |
| Consola con interfaz gráfica | `gui/ControlConsole` (JFrame). |
| GUI de elevadores: piso, botones internos encendidos, requerimientos del manager | `gui/ElevatorCard` (+ dibujo en `BuildingPanel`). |
| Herencia (con lógica) | `SimulationProcess` ← `Elevator`, `ElevatorManager`, `RequestFilePlayer`. `Command` ← `GotoCommand`, `PickupCommand`, `ResetCommand`. Además `Formatter`, `Handler`, `StreamHandler`, `JPanel`. |
| Paquetes (con lógica) | 8 paquetes por responsabilidad (ver §3). |
| Threads | Ver §4. |
| Clase de Logs de Java | `java.util.logging` (`Logger`, `FileHandler`, `Formatter`, `Handler`). |
| Se ejecuta desde una clase llamada `ElevatorManager` | `manager.ElevatorManager.main`. |
| **Extra:** pausar y reanudar | `core/PauseControl` + botón **Pausar** / `PAUSE`, `RESUME`. |
| **Extra:** simulación gráfica | `gui/BuildingPanel` (cabinas animadas, puertas que se abren). |
| **Extra:** protocolo de comunicación de comandos en texto | Paquete `protocol` (§8). |

---

## 3. Arquitectura general

```
                ┌──────────────────────────── GUI (hilo EDT de Swing) ─────────────────────────┐
                │ ControlConsole: botones, BuildingPanel, ElevatorCard, logs, campo de comandos │
                └───────┬──────────────────────────────────────────────▲───────────────────────┘
       submitHallCall / │ submitCabinRequest / resetSystem              │ snapshot() cada 50 ms
       executeConsole…  ▼                                               │
                ┌───────────────────────────┐   "PICKUP 5 DOWN"   ┌─────┴──────────────┐
  archivo ──►   │  ElevatorManager (hilo)   │ ─── "GOTO 9" ─────► │ Elevator 1 (hilo)  │──► logs/elevador1.log
 (RequestFile   │  cola: BlockingQueue      │ ─── "RESET" ──────► │ Elevator 2 (hilo)  │──► logs/elevador2.log
  Player hilo)  │  CostEstimator            │                     │ Elevator N (hilo)  │──► ...
                └───────────┬───────────────┘                     └────────────────────┘
                            └──► logs/manager.log
```

**Paquetes** (cada uno con una responsabilidad clara):

| Paquete    | Responsabilidad |
|------------|-----------------|
| `core`     | Infraestructura de simulación reutilizable: clase base de hilos, pausa, configuración. No sabe nada de elevadores. |
| `model`    | Conceptos del dominio compartidos por todos: `Direction`, `Request`. |
| `elevator` | Todo lo del elevador: el hilo, su algoritmo de movimiento, su conjunto de paradas, su snapshot. |
| `manager`  | La toma de decisiones y el punto de entrada (`main`). |
| `protocol` | El "idioma" de texto con el que se dan órdenes (manager→elevador y usuario→manager). |
| `logging`  | Configuración de `java.util.logging`. |
| `io`       | Lectura y reproducción de archivos de requerimientos. |
| `gui`      | Todo lo de Swing. Ninguna otra capa depende de `gui` (excepto `main`, que la abre). |

Fíjate en la dirección de las dependencias: `gui` → `manager` → `elevator` → `core/model`.
La lógica **no depende** de la interfaz, por eso se pudo probar sin abrir ventanas (§15).

---

## 4. Hilos: `SimulationProcess` y la herencia

Archivo: `src/core/SimulationProcess.java`

Los tres procesos (elevador, manager, lector de archivo) hacen lo mismo a grandes rasgos:

```java
public final void run() {
    onStart();
    try {
        while (running && !isInterrupted()) {
            pauseControl.awaitIfPaused();   // si hay pausa, se queda dormido aquí
            step();                         // ← lo único distinto entre subclases
        }
    } catch (InterruptedException e) { /* forma normal de terminar */ }
    finally { onStop(); }
}
```

En vez de copiar ese ciclo tres veces, está **una sola vez en la clase padre** y cada hija
solo implementa `step()`. Esto es el patrón **Template Method** y es un uso de herencia
**con lógica** (no "herencia por usarla"): la clase padre aporta comportamiento real
(ciclo de vida, detener, pausa, dormir) que todas necesitan.

### ¿Cómo se detiene un hilo correctamente?

Nunca con `Thread.stop()` (obsoleto y peligroso). Se usa:

```java
public void shutdown() {
    running = false;   // la próxima vuelta del while sale
    interrupt();       // si estaba en sleep()/wait()/poll(), lo despierta con InterruptedException
}
```

`running` es `volatile` porque lo escribe un hilo (la GUI) y lo lee otro; `volatile`
garantiza que el cambio se vea inmediatamente.

### `simSleep`: dormir respetando la pausa

```java
protected void simSleep(long ms, DoubleConsumer progress) throws InterruptedException {
    long elapsed = 0;
    while (elapsed < ms) {
        pauseControl.awaitIfPaused();     // si pausan a la mitad, el tiempo se congela
        long chunk = Math.min(TICK_MS, ms - elapsed);
        Thread.sleep(chunk);
        elapsed += chunk;
        if (progress != null) progress.accept((double) elapsed / ms); // para la animación
    }
}
```

Duerme en pedacitos de 20 ms. Así (1) la pausa congela al elevador **a medio camino** entre
pisos y (2) la GUI sabe qué porcentaje del viaje lleva y lo puede dibujar suavemente.

> **Detalle curioso:** el método de reanudar se llama `resumeSimulation()` y no `resume()`
> porque `Thread` ya tiene un método `resume()` *final* (obsoleto) y no se puede
> sobrescribir. El compilador nos avisó durante el desarrollo.

---

## 5. El elevador

Archivo: `src/elevator/Elevator.java`

### Estado

```java
private final BlockingQueue<Command> inbox;   // cola de comandos
private final StopSet stops;                   // pisos pendientes
private int currentFloor = 1;                  // piso actual (o el que acaba de dejar)
private Direction direction = Direction.UP;    // inicia hacia arriba
private ElevatorStatus status = IDLE;          // En espera / En movimiento / Puertas abiertas / Reset
```

### La cola de comandos (`inbox`)

El enunciado dice que cada elevador tiene **su propia cola de comandos** que pueden
modificar el manager, los botones internos y el reset. Se implementó con
`LinkedBlockingQueue`, que ya es *thread-safe*: cualquier hilo puede hacer `inbox.add(...)`
sin `synchronized`, y solo el hilo del elevador saca y ejecuta.

### `StopSet`: los pisos pendientes

`src/elevator/StopSet.java` guarda tres arreglos `boolean` indexados por piso:

- `cabin[f]` → alguien adentro presionó el piso `f` (hay que **dejar** gente)
- `up[f]` → el manager asignó una llamada en `f` para **subir** (hay que **recoger**)
- `down[f]` → ídem para **bajar**

¿Por qué `boolean[]` y no una lista? Porque presionar dos veces el mismo botón no debe
generar dos paradas: un booleano ya es "encendido/apagado", igual que el botón real.

### Un paso del elevador (`step`)

```java
protected void step() throws InterruptedException {
    // 1. Ejecutar todo lo que llegó a la cola
    while ((c = inbox.poll()) != null) execute(c);
    // 2. Decidir (bajo candado, porque otros hilos leen el estado)
    synchronized (this) { d = LookAlgorithm.decide(currentFloor, direction, stops); }
    // 3. Actuar
    switch (d.action()) {
        case IDLE -> waitForCommand();          // espera hasta 100 ms por un comando
        case TURN -> changeDirection(...);      // log "Cambio de direccion"
        case STOP -> stopAtCurrentFloor(...);   // log "Parada ... abre puertas", espera stopTimeMs
        case MOVE -> moveOneFloor();            // log "Sube/Baja del piso X al Y", espera travelTimeMs, log "Llega"
    }
}
```

El elevador se mueve **de piso en piso**. Después de cada piso vuelve a leer su cola y a
decidir. Eso permite que una llamada nueva que aparece "en el camino" se atienda.

### Piso actual mientras se mueve

El enunciado dice: *"Si el elevador va en movimiento, el piso actual debería ser el piso que
acaba de dejar"*. Por eso en `moveOneFloor` se actualiza `currentFloor` **después** de
`simSleep(travelTimeMs)`. Mientras viaja, `inMotion = true` y `progress` va de 0 a 1.

### `snapshot()`

Los otros hilos nunca leen los campos directamente. Llaman a `snapshot()`, que dentro de
`synchronized` arma un objeto inmutable (`ElevatorSnapshot`, un `record`) con una **copia**
del estado. Además, el snapshot incluye los comandos que todavía están en la cola (para
que el manager no tome decisiones con información atrasada y para que el botón se encienda
en la GUI apenas lo presionas).

---

## 6. El algoritmo de movimiento LOOK

Archivo: `src/elevator/LookAlgorithm.java`

Es el algoritmo que usan los elevadores reales (variante del "algoritmo del ascensor" o
SCAN). Reglas en orden:

1. **Nada pendiente** → `IDLE`.
2. **Hay algo para mí en este piso** (botón interno, o llamada en la **misma** dirección
   en la que voy) → `STOP`.
3. **Hay algo más adelante** en mi dirección → `MOVE` (sigo).
4. **No hay nada adelante** → me toca dar la vuelta:
   - si en este mismo piso alguien quiere ir en la dirección contraria → `STOP` cambiando
     de dirección;
   - si no → `TURN` (solo cambio de dirección; en el siguiente paso me moveré).

Esto cumple literalmente las dos reglas del enunciado:

- *"Un elevador que se dirige hacia arriba no debe cambiar de dirección para traer una
  persona. Primero tiene que subir a donde se dirigía."* → la regla 3 tiene prioridad
  sobre la 4: mientras haya algo arriba, sigue subiendo.
- *"Puede pasar recogiendo en el camino personas que vayan hacia la misma dirección."*
  → regla 2: solo para por llamadas **en su misma dirección**. Si alguien en el piso 5
  quiere **bajar** y el elevador va **subiendo** hacia el 9, no para en el 5 al subir;
  lo recoge al bajar.

**Ejemplo** (elevador en piso 1, presionan 9 adentro; luego alguien llama en piso 2 para
subir cuando el elevador ya pasó el 3):

```
1 →2 →3 →4 ... →9  (STOP, deja gente)  TURN  9 →8 ... →2  TURN-STOP (recoge para subir)
```

`decide()` es una **función pura** (no tiene estado ni hilos). Gracias a eso la reutiliza el
manager para *simular* rutas (siguiente sección). Un solo algoritmo, dos usos.

---

## 7. El Elevator Manager y cómo elige elevador

Archivo: `src/manager/ElevatorManager.java` y `src/manager/CostEstimator.java`

### Flujo

1. La GUI (o el campo de comandos, o el archivo) llama a `submitHallCall(piso, dir)` o
   `submitCabinRequest(elevador, piso)`. Esto solo **mete un `Request` en una cola**, así la
   GUI nunca se bloquea.
2. El hilo del manager saca los `Request` uno por uno (`step()`):
   - **Botón interno** (`CABIN`): ya se sabe qué elevador es → le manda `"GOTO piso"`.
   - **Botón de piso** (`HALL`): hay que decidir → `dispatchHallCall`.

### `dispatchHallCall`: la decisión

```text
1. ¿Algún elevador ya tiene esa llamada (piso, dirección)?  → se ignora (botones compartidos).
2. Para cada elevador: costo = CostEstimator.estimate(snapshot, piso, dirección)
3. Se elige el de menor costo. Empate → el menos ocupado (menos paradas). Empate → menor ID.
4. Se le envía "PICKUP piso dirección" por el protocolo de texto.
```

### `CostEstimator`: ¿cuánto tardaría cada elevador?

En lugar de usar solo la distancia, el manager **simula mentalmente** lo que haría cada
elevador si se le asignara la llamada:

1. Copia las paradas del elevador y le agrega la llamada nueva.
2. Corre `LookAlgorithm.decide` en un ciclo (sin dormir — es instantáneo), sumando
   `travelTimeMs` por cada piso recorrido y `stopTimeMs` por cada parada **antes** de llegar.
3. Considera la situación actual: si el elevador va a medio viaje, suma lo que le falta; si
   tiene las puertas abiertas, suma lo que falta de la parada; si está en reset, primero
   debe llegar al piso 1.

¿Por qué es mejor que "el más cercano"? Ejemplo con 10 pisos, llamada en piso 4 para subir:

| Elevador | Situación                                  | Distancia | Costo real estimado |
|----------|--------------------------------------------|-----------|---------------------|
| E1       | piso 5, subiendo hacia el 10 con 3 paradas | 1 piso    | sube a 10, para 3 veces, baja a 4 → **alto** |
| E2       | piso 1, sin nada                           | 3 pisos   | 3 × travel → **bajo** |

El "más cercano" elegiría E1 (mal). El estimador elige E2. Así se cumple "el más cercano
**o** el menos ocupado" con un único número comparable. En el log del manager se ve el
detalle:

```
Llamada piso 4 SUBIR asignada al Elevador 3 | costos estimados: E1=240ms(0 pend.) E2=440ms(0 pend.) E3=80ms(0 pend.)
```

---

## 8. Protocolo de comunicación (punto extra)

Paquete: `src/protocol/`

El enunciado da puntos extra por *"un protocolo de comunicación a base de comandos y
argumentos que le dice a los elevadores qué hacer, enviado en forma de texto"*.

**Manager → Elevador** (`Command` y subclases):

| Texto              | Clase           | Efecto en el elevador                 |
|--------------------|-----------------|---------------------------------------|
| `GOTO 7`           | `GotoCommand`   | enciende el botón interno del piso 7  |
| `PICKUP 5 DOWN`    | `PickupCommand` | agrega la llamada piso 5 / bajar      |
| `RESET`            | `ResetCommand`  | borra todo y regresa al piso 1        |

El manager arma el texto (`new PickupCommand(5, DOWN).toProtocol()` → `"PICKUP 5 DOWN"`) y
lo envía con `elevator.send(String)`. El elevador lo **interpreta** con `CommandParser.parse`
y lo encola. Al ejecutarlo, simplemente llama `command.execute(this)` — cada subclase sabe
qué hacer (**polimorfismo**), así que no hay un `switch` gigante en el elevador.

**Usuario → Manager** (`ConsoleCommand` + `ConsoleCommandParser`):

```
CALL <piso> <UP|DOWN>     GOTO <elevador> <piso>     RESET [elevador]
PAUSE    RESUME    STATUS    HELP
```

Se aceptan también `SUBIR/BAJAR`. El parser **valida** rangos (no existe piso 0, en el
piso 1 no hay botón de bajar, en el último piso no hay botón de subir, etc.).

---

## 9. Reset

Hay dos niveles:

- **Reset de un elevador** (botón *Reset* de la tarjeta / `RESET 2`): el manager le envía
  `RESET` a ese elevador.
- **Reset del sistema** (botón *Reset sistema* / `RESET`): el manager **vacía su propia
  cola** de llamadas sin asignar y envía `RESET` a todos.

Lo que hace el elevador al ejecutar `RESET` (`Elevator.reset()`):

1. Borra todas sus paradas (los comandos que llegaron antes del reset quedan anulados).
2. Estado `RESETTING`. Log `RESET: se borraron todos los comandos…`.
3. Si no está en el piso 1, cambia a dirección abajo y **simula el movimiento** piso por
   piso hasta el 1 (con sus logs normales).
4. Cambia la dirección a **hacia arriba**. Log `RESET completado`.

Los comandos que lleguen *después* del reset se quedan en la cola y se atienden al terminar.

---

## 10. Pausa y reanudación (punto extra)

Archivo: `src/core/PauseControl.java`

Un único objeto compartido por todos los hilos. Es un **monitor** clásico:

```java
public synchronized void awaitIfPaused() throws InterruptedException {
    while (paused) wait();      // el hilo se duerme sin consumir CPU
}
public synchronized void resume() {
    paused = false;
    notifyAll();                // despierta a TODOS los que estaban esperando
}
```

Todos los hilos llaman `awaitIfPaused()` al inicio de cada paso **y** dentro de
`simSleep()`, así que la pausa es inmediata incluso a mitad de un viaje.

---

## 11. Logging

Paquete: `src/logging/`. Se usa **`java.util.logging`** (la librería estándar de Java).

Cada `Logger` (`"Elevador 1"`, `"Elevador 2"`, …, `"Manager"`) tiene **tres Handlers**:

| Handler           | Destino                        | Notas |
|-------------------|--------------------------------|-------|
| `FileHandler`     | `logs/elevadorN.log`, `logs/manager.log` | Un archivo por elevador, como pide el enunciado. Modo *append*. |
| `StdoutHandler`   | La ventana de línea de comandos | Hereda de `StreamHandler`; hace `flush` en cada línea y **no** cierra `System.out`. |
| `GuiLogHandler`   | El panel de logs de la ventana  | Hereda de `Handler`; reenvía cada línea a la GUI. |

Formato (`SimulationLogFormatter`, hereda de `Formatter`):

```
[2026-11-02 10:15:03.120] [Elevador 2] INFO    Sube del piso 3 al piso 4
[2026-11-02 10:15:04.121] [Elevador 2] INFO    Llega al piso 4
[2026-11-02 10:15:04.122] [Elevador 2] INFO    Parada en piso 4: abre puertas para recoger personas (llamada para SUBIR)
[2026-11-02 10:15:06.130] [Elevador 2] INFO    Cierra puertas en piso 4
[2026-11-02 10:15:06.131] [Elevador 2] INFO    Cambio de direccion en piso 9: hacia arriba -> hacia abajo
[2026-11-02 10:15:09.000] [Elevador 2] INFO    RESET: se borraron todos los comandos. Regresando al piso 1
```

Los eventos que pide el enunciado y el mensaje correspondiente:

| Evento requerido                     | Mensaje                                   |
|--------------------------------------|-------------------------------------------|
| Va subiendo o bajando a un piso      | `Sube del piso X al piso Y` / `Baja …`    |
| Ya llegó a un piso                   | `Llega al piso Y`                         |
| Paró a recoger/dejar personas        | `Parada en piso X: abre puertas para …`   |
| Se hace un reset                     | `RESET: …` y `RESET completado: …`        |
| Cambio de dirección                  | `Cambio de direccion en piso X: … -> …`   |

`setUseParentHandlers(false)` evita que cada línea salga duplicada por el logger raíz.
Además, los archivos reciben también mensajes `FINE` (por ejemplo cada comando de texto
recibido), útiles para depurar; la GUI y la consola solo muestran `INFO` o superior.

> Nota: los mensajes en el código no llevan tildes a propósito, para que se vean bien en la
> consola de Windows sin importar su codificación.

---

## 12. Carga de archivo de requerimientos

Archivos: `src/io/RequestFileLoader.java`, `src/io/RequestFilePlayer.java`,
ejemplo en `ejemplos/requerimientos-demo.txt`.

Formato (decisión nuestra, el enunciado lo deja libre):

```
# comentario
<ms desde que se carga>  <comando de consola>
0      CALL 5 DOWN
1500   GOTO 1 10
3000   CALL 1 UP
```

- **Solo se permite en estado de reset** (como dice el enunciado): al inicio o después de un
  *Reset sistema*, con todos los elevadores en piso 1, hacia arriba, quietos y sin nada
  pendiente. Lo verifica `ElevatorManager.isAtResetState()`.
- `RequestFileLoader` **valida el archivo completo** antes de ejecutar algo; si una línea
  está mal muestra `Linea N: …` y no ejecuta nada.
- `RequestFilePlayer` es otro hilo (`extends SimulationProcess`): espera el tiempo de cada
  línea (respetando la pausa) y se la pasa al manager como si el usuario la hubiera escrito.

---

## 13. Interfaz gráfica

Paquete: `src/gui/`

| Clase            | Qué es |
|------------------|--------|
| `ConfigDialog`   | Ventana inicial con `JSpinner` (solo acepta números válidos) para elevadores, pisos y tiempos. |
| `ControlConsole` | La **consola de comandos** (`JFrame`): barra de botones, paneles y logs. |
| `FloorCallPanel` | Botones ▲/▼ de cada piso (compartidos). Se encienden en amarillo mientras la llamada está pendiente. |
| `ElevatorCard`   | Por elevador: piso, dirección, estado, **botones internos** (encendidos en amarillo) y **requerimientos del manager** (ej. `7▼ 10▼`). |
| `BuildingPanel`  | **Simulación gráfica**: cubos, cabinas que se deslizan entre pisos, puertas que se abren, llamadas y paradas marcadas. Colores: gris = espera, azul = movimiento, verde = puertas abiertas, naranja = reset. |

### La regla de oro de Swing

Swing **no es thread-safe**: solo el *Event Dispatch Thread* (EDT) puede tocar componentes.
Nuestros elevadores son otros hilos, así que **nunca** tocan la GUI. En cambio:

- Un `javax.swing.Timer` (que corre en el EDT) cada 50 ms pide `snapshot()` a cada elevador y
  actualiza todo. Es *pull* (la GUI pregunta) en vez de *push* (los hilos avisan), y es la
  forma más simple de hacerlo seguro.
- Los logs llegan desde cualquier hilo al `GuiLogHandler`, que usa
  `SwingUtilities.invokeLater(...)` para que el `append` se haga en el EDT.
- El `main` crea la ventana dentro de `SwingUtilities.invokeLater`.

---

## 14. Concurrencia: problemas evitados

| Problema | Cómo se evitó |
|---|---|
| Dos hilos modifican la cola del elevador al mismo tiempo | `LinkedBlockingQueue` es thread-safe. |
| La GUI lee el piso mientras el elevador lo cambia (datos a medias) | Todo el estado se lee/escribe bajo `synchronized(this)`; afuera solo salen copias (`snapshot`). |
| Un hilo no "ve" un cambio hecho por otro | `volatile` en `running` y `progress`. |
| Dormir bloquea la pausa/detención | `simSleep` en trozos + `interrupt()` en `shutdown()`. |
| *Deadlock* | Nunca se toma un candado mientras se espera otro; las esperas largas (`simSleep`, `poll`) ocurren **fuera** de bloques `synchronized`. |
| Busy-waiting (gastar CPU esperando) | En espera se usa `inbox.poll(100 ms)` y `wait()`, que duermen al hilo. |
| La JVM no termina al cerrar la ventana | Los hilos son *daemon* y la ventana usa `EXIT_ON_CLOSE`. |
| Asignar dos elevadores a la misma llamada | `isHandlingHallCall` revisa la cola y las paradas de cada elevador. |

---

## 15. Cómo se probó

Además de probar a mano con la GUI, se escribió un arnés de pruebas sin interfaz (usa las
mismas clases, con tiempos cortos de 80–100 ms) que verificó:

- ✅ **Regla LOOK**: con 1 elevador yendo al piso 9, una llamada en piso 2 para subir se
  atiende **después** de llegar al 9 (no se regresa).
- ✅ **Asignación**: con E2 en piso 9 y E3 en piso 5, la llamada *9 BAJAR* va a E2 y la
  llamada *4 SUBIR* va a E3 (el más cercano/menos costoso).
- ✅ **Duplicados**: presionar dos veces el mismo botón de piso no asigna dos elevadores.
- ✅ **Pausa**: con la simulación pausada la posición del elevador no cambia.
- ✅ **Reset del sistema** a mitad de un viaje: todos terminan en piso 1, hacia arriba, y el
  sistema queda en "estado de reset".
- ✅ **Archivo de ejemplo**: se carga, se valida y se ejecuta completo.

Puedes repetir la prueba visual así: ejecuta con 3 elevadores y 10 pisos, y escribe en el
campo de comandos `CALL 7 DOWN`, `GOTO 2 9`, `CALL 4 UP`, `GOTO 3 6`, `CALL 10 DOWN`.

---

## 16. Recorrido de un ejemplo

Usuario presiona ▼ en el piso 7 (3 elevadores, todos en piso 1):

1. **EDT**: `FloorCallPanel` → `ControlConsole.onHallCall(7, DOWN)` →
   `manager.submitHallCall(7, DOWN)` → `requests.add(...)`. Log del manager:
   `Requerimiento recibido: llamada en piso 7 para BAJAR`. El botón se enciende.
2. **Hilo Manager**: `step()` saca el request → `dispatchHallCall(7, DOWN)`. Nadie la tiene.
   Costos: los tres valen 6×travel → empate → menos ocupado → empate → **E1**.
   Log: `Llamada piso 7 BAJAR asignada al Elevador 1 | costos …`.
   Envía `"PICKUP 7 DOWN"` → `elevator1.send(...)` → se parsea → `inbox.add(...)`.
3. **Hilo Elevador 1**: en su siguiente `step()` saca el comando y ejecuta
   `addHallCall(7, DOWN)`. `LookAlgorithm` → `MOVE` (hay algo arriba). Sube 1→2→…→7 (logs
   `Sube…`/`Llega…`).
4. En el piso 7: no hay nada más arriba y hay llamada para **bajar** en este piso → `STOP`
   con cambio de dirección. Logs: `Cambio de direccion … hacia arriba -> hacia abajo`,
   `Parada en piso 7: abre puertas para recoger personas (llamada para BAJAR)`. El botón ▼
   del piso 7 se apaga.
5. Alguien adentro presiona **3** en la tarjeta del E1 → `submitCabinRequest(1, 3)` → el
   manager reenvía `"GOTO 3"` → el E1 baja al 3 y deja a la persona.
6. Sin nada pendiente → `IDLE`. Log `En espera en piso 3`.

---

## 17. Preguntas típicas

**¿Dónde está la herencia y por qué tiene lógica?**
`SimulationProcess` concentra el ciclo de vida de un hilo, la pausa, el detener y el dormir
simulado. `Elevator`, `ElevatorManager` y `RequestFilePlayer` solo definen su `step()`.
`Command` define el formato de texto (`toProtocol`) y la igualdad; cada subclase define sus
argumentos y su efecto (`execute`).

**¿Por qué un elevador es un proceso independiente si el manager le da órdenes?**
El manager **no** mueve al elevador: solo le deja un mensaje en su cola. El elevador decide
cuándo leerlo y cómo moverse (LOOK), a su ritmo, en su propio hilo.

**¿Qué pasa si se presiona un botón interno del piso donde ya está el elevador?**
`LookAlgorithm` devuelve `STOP` y el elevador abre las puertas ahí mismo.

**¿Cómo lo hago más eficiente?**
Ideas: reasignar llamadas si aparece un elevador mejor después de asignar; considerar
capacidad máxima de personas; estacionar elevadores ociosos en pisos estratégicos.

**¿Cómo agrego un comando nuevo al protocolo?**
1) Crea `class XCommand extends Command` con `execute` y `arguments`;
2) agrégalo al `switch` de `CommandParser`; 3) si necesita algo nuevo del elevador, agrega el
método a `CommandTarget` e impleméntalo en `Elevator`.
