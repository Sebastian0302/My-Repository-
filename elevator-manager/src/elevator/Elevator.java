package elevator;

import core.PauseControl;
import core.SimulationConfig;
import core.SimulationProcess;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import logging.LogFactory;
import model.Direction;
import protocol.Command;
import protocol.CommandParser;
import protocol.CommandTarget;
import protocol.GotoCommand;
import protocol.PickupCommand;
import protocol.ProtocolException;
import protocol.ResetCommand;

/**
 * Un elevador: es un PROCESO INDEPENDIENTE (su propio hilo).
 *
 * Como se comunica con el resto:
 *  - Recibe ordenes en su COLA DE COMANDOS (inbox). Cualquiera (manager,
 *    botones internos, reset) puede meter comandos, pero solo el hilo del
 *    elevador los saca y los ejecuta. Una BlockingQueue ya es thread-safe.
 *  - Publica su estado mediante snapshot() (copia protegida con synchronized).
 *
 * Ciclo de vida (metodo step(), llamado una y otra vez por SimulationProcess):
 *  1. Ejecutar todos los comandos que llegaron a la cola.
 *  2. Preguntarle al LookAlgorithm que hacer.
 *  3. Hacerlo: esperar / moverse un piso / parar / cambiar de direccion.
 */
public class Elevator extends SimulationProcess implements CommandTarget {

    private static final long IDLE_POLL_MS = 100;

    private final int id;
    private final SimulationConfig config;
    private final Logger log;
    private final BlockingQueue<Command> inbox = new LinkedBlockingQueue<>();

    // ---- Estado protegido por "synchronized(this)" ----
    private final StopSet stops;
    private int currentFloor = 1;                 // si se mueve: el piso que acaba de dejar
    private Direction direction = Direction.UP;   // inicia "hacia arriba"
    private ElevatorStatus status = ElevatorStatus.IDLE;
    private boolean inMotion = false;
    // volatile: lo escribe este hilo y la GUI lo lee para animar.
    private volatile double progress = 0;

    public Elevator(int id, SimulationConfig config, PauseControl pauseControl) {
        super("Elevador-" + id, pauseControl);
        this.id = id;
        this.config = config;
        this.stops = new StopSet(config.floorCount());
        this.log = LogFactory.elevatorLogger(id);
    }

    public int elevatorId() {
        return id;
    }

    // =====================================================================
    //  Entrada de comandos (la llaman OTROS hilos)
    // =====================================================================

    /** Recibe un comando en texto (protocolo), lo interpreta y lo encola. */
    public void send(String protocolText) throws ProtocolException {
        send(CommandParser.parse(protocolText));
    }

    public void send(Command command) {
        inbox.add(command);
    }

    /** Hay una llamada (piso, direccion) ya asignada a este elevador? */
    public synchronized boolean isHandlingHallCall(int floor, Direction dir) {
        return inbox.contains(new PickupCommand(floor, dir)) || stops.hasHall(floor, dir);
    }

    /**
     * Fotografia del estado. Las paradas incluyen tambien los comandos que
     * todavia estan en la cola (asi el manager estima con datos al dia).
     */
    public synchronized ElevatorSnapshot snapshot() {
        StopSet projected = stops.copy();
        for (Command c : inbox) {
            if (c instanceof GotoCommand g && projected.isValidFloor(g.floor())) {
                projected.addCabin(g.floor());
            } else if (c instanceof PickupCommand p && projected.isValidFloor(p.floor())) {
                projected.addHall(p.floor(), p.direction());
            } else if (c instanceof ResetCommand) {
                projected.clear();
            }
        }
        return new ElevatorSnapshot(id, currentFloor, direction, status, inMotion, progress, projected, inbox.size());
    }

    // =====================================================================
    //  Ciclo del hilo
    // =====================================================================

    @Override
    protected void onStart() {
        log.info("Proceso iniciado: piso 1, direccion hacia arriba");
    }

    @Override
    protected void onStop() {
        log.info("Proceso detenido en piso " + currentFloor);
    }

    @Override
    protected void step() throws InterruptedException {
        // 1. Procesar la cola de comandos
        Command c;
        while ((c = inbox.poll()) != null) {
            execute(c);
        }
        // 2. Decidir
        LookAlgorithm.Decision d;
        synchronized (this) {
            d = LookAlgorithm.decide(currentFloor, direction, stops);
        }
        // 3. Actuar
        switch (d.action()) {
            case IDLE -> waitForCommand();
            case TURN -> changeDirection(d.direction());
            case STOP -> stopAtCurrentFloor(d.direction());
            case MOVE -> moveOneFloor();
        }
    }

    private void execute(Command c) throws InterruptedException {
        log.fine("Ejecutando comando: " + c.toProtocol());
        c.execute(this); // polimorfismo: cada comando sabe que hacer
    }

    /** Sin trabajo: espera (hasta 100 ms) a que llegue un comando. */
    private void waitForCommand() throws InterruptedException {
        boolean becameIdle;
        synchronized (this) {
            becameIdle = status != ElevatorStatus.IDLE;
            status = ElevatorStatus.IDLE;
        }
        if (becameIdle) {
            log.info("En espera en piso " + currentFloor + " (sin requerimientos)");
        }
        Command c = inbox.poll(IDLE_POLL_MS, TimeUnit.MILLISECONDS);
        if (c != null) {
            execute(c);
        }
    }

    private void changeDirection(Direction newDirection) {
        Direction old;
        synchronized (this) {
            old = direction;
            direction = newDirection;
        }
        log.info("Cambio de direccion en piso " + currentFloor + ": " + old.label() + " -> " + newDirection.label());
    }

    /** Abre puertas en el piso actual para dejar/recoger personas. */
    private void stopAtCurrentFloor(Direction dir) throws InterruptedException {
        if (dir != direction) {
            changeDirection(dir);
        }
        String reason;
        synchronized (this) {
            boolean cabin = stops.hasCabin(currentFloor);
            boolean hall = stops.hasHall(currentFloor, dir);
            if (cabin && hall) {
                reason = "dejar y recoger personas";
            } else if (cabin) {
                reason = "dejar personas (boton interno)";
            } else {
                reason = "recoger personas (llamada para " + dir.spanishCode() + ")";
            }
            stops.clearAt(currentFloor, dir); // se apagan los botones atendidos
            status = ElevatorStatus.DOORS_OPEN;
            progress = 0;
        }
        log.info("Parada en piso " + currentFloor + ": abre puertas para " + reason);
        simSleep(config.stopTimeMs(), p -> progress = p);
        log.info("Cierra puertas en piso " + currentFloor);
    }

    /** Viaja UN piso en la direccion actual. */
    private void moveOneFloor() throws InterruptedException {
        int from;
        int to;
        synchronized (this) {
            from = currentFloor;
            to = from + direction.delta();
            if (!stops.isValidFloor(to)) {
                return; // no deberia pasar con LOOK, pero por seguridad
            }
            if (status != ElevatorStatus.RESETTING) {
                status = ElevatorStatus.MOVING;
            }
            inMotion = true;
            progress = 0;
        }
        log.info((direction == Direction.UP ? "Sube" : "Baja") + " del piso " + from + " al piso " + to);
        simSleep(config.travelTimeMs(), p -> progress = p);
        synchronized (this) {
            currentFloor = to;
            inMotion = false;
            progress = 0;
        }
        log.info("Llega al piso " + to);
    }

    // =====================================================================
    //  CommandTarget: lo que los comandos pueden hacer (hilo del elevador)
    // =====================================================================

    @Override
    public synchronized void addCabinStop(int floor) {
        if (!stops.isValidFloor(floor)) {
            log.warning("Boton interno invalido: piso " + floor);
            return;
        }
        stops.addCabin(floor);
        log.info("Boton interno presionado: piso " + floor + " marcado para visitar");
    }

    @Override
    public synchronized void addHallCall(int floor, Direction dir) {
        if (!stops.isValidFloor(floor)) {
            log.warning("Llamada invalida: piso " + floor);
            return;
        }
        stops.addHall(floor, dir);
        log.info("Requerimiento del manager: recoger en piso " + floor + " para " + dir.spanishCode());
    }

    /**
     * RESET: borra todos los comandos y regresa al estado inicial
     * (piso 1, hacia arriba), simulando el movimiento hasta el piso 1.
     * Los comandos que lleguen mientras tanto se atienden al terminar.
     */
    @Override
    public void reset() throws InterruptedException {
        synchronized (this) {
            stops.clear();
            status = ElevatorStatus.RESETTING;
        }
        log.info("RESET: se borraron todos los comandos. Regresando al piso 1");
        if (currentFloor > 1 && direction != Direction.DOWN) {
            changeDirection(Direction.DOWN);
        }
        while (currentFloor > 1) {
            moveOneFloor();
        }
        if (direction != Direction.UP) {
            changeDirection(Direction.UP);
        }
        synchronized (this) {
            status = ElevatorStatus.IDLE;
        }
        log.info("RESET completado: piso 1, direccion hacia arriba");
    }
}
