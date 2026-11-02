package manager;

import core.PauseControl;
import core.SimulationConfig;
import core.SimulationProcess;
import elevator.Elevator;
import elevator.ElevatorSnapshot;
import elevator.ElevatorStatus;
import gui.ConfigDialog;
import gui.ControlConsole;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import logging.LogFactory;
import model.Direction;
import model.Request;
import protocol.ConsoleCommand;
import protocol.ConsoleCommandParser;
import protocol.GotoCommand;
import protocol.PickupCommand;
import protocol.ProtocolException;
import protocol.ResetCommand;

/**
 * ELEVATOR MANAGER: clase principal del programa (tiene el main) y tambien
 * el proceso independiente que decide que elevador atiende cada llamada.
 *
 * Funcionamiento:
 *  - La GUI / consola / archivo dejan Request en una cola (requests).
 *  - El hilo del manager las saca una por una:
 *      * CABIN (boton interno): se reenvia tal cual al elevador -> "GOTO piso"
 *      * HALL  (boton de piso): se estima el costo de cada elevador con
 *        CostEstimator y se le envia "PICKUP piso dir" al de menor costo.
 *  - Es ESCALABLE: funciona con 1..N elevadores porque todo es una lista.
 */
public class ElevatorManager extends SimulationProcess {

    private static final long POLL_MS = 100;

    private final SimulationConfig config;
    private final List<Elevator> elevators;
    private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
    private final Logger log;

    // =====================================================================
    //  Punto de entrada del programa
    // =====================================================================

    /**
     * Uso: java -cp out manager.ElevatorManager [elevadores] [pisos] [msEntrePisos] [msParada]
     * Los argumentos son opcionales: solo pre-llenan la ventana de configuracion.
     */
    public static void main(String[] args) {
        SimulationConfig defaults = SimulationConfig.fromArgsOrDefaults(args);
        // Todo lo de Swing debe crearse en el hilo de eventos (EDT).
        SwingUtilities.invokeLater(() -> {
            SimulationConfig cfg = ConfigDialog.ask(null, defaults);
            if (cfg == null) {
                System.exit(0);
            }
            new ControlConsole(cfg).setVisible(true);
        });
    }

    // =====================================================================

    public ElevatorManager(SimulationConfig config, PauseControl pauseControl) {
        super("ElevatorManager", pauseControl);
        this.config = config;
        this.log = LogFactory.managerLogger();
        List<Elevator> list = new ArrayList<>();
        for (int i = 1; i <= config.elevatorCount(); i++) {
            list.add(new Elevator(i, config, pauseControl));
        }
        this.elevators = Collections.unmodifiableList(list);
    }

    public SimulationConfig config() {
        return config;
    }

    public Logger logger() {
        return log;
    }

    /** Arranca el hilo del manager y el de cada elevador. */
    public void startSimulation() {
        log.info("Simulacion iniciada: " + config);
        for (Elevator e : elevators) {
            e.start();
        }
        start();
    }

    /** Detiene todos los hilos y cierra los archivos de log. */
    public void stopSimulation() {
        log.info("Simulacion detenida");
        shutdown();
        for (Elevator e : elevators) {
            e.shutdown();
        }
        try {
            join(1000);
            for (Elevator e : elevators) {
                e.join(1000);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        LogFactory.closeAll();
    }

    // =====================================================================
    //  Entrada de requerimientos (los llaman la GUI, la consola, el archivo)
    // =====================================================================

    public void submitHallCall(int floor, Direction dir) {
        Request r = Request.hall(floor, dir);
        log.info("Requerimiento recibido: " + r);
        requests.add(r);
    }

    public void submitCabinRequest(int elevatorId, int floor) {
        Request r = Request.cabin(elevatorId, floor);
        log.info("Requerimiento recibido: " + r);
        requests.add(r);
    }

    /** Reset de TODO el sistema: se descartan las llamadas no asignadas y se resetea cada elevador. */
    public void resetSystem() {
        requests.clear();
        log.info("RESET DEL SISTEMA: todos los elevadores ignoran sus requerimientos y regresan al piso 1");
        for (Elevator e : elevators) {
            e.send(new ResetCommand());
        }
    }

    public void resetElevator(int elevatorId) {
        log.info("Reset solicitado para el Elevador " + elevatorId);
        elevator(elevatorId).send(new ResetCommand());
    }

    // Nota: no se llaman pause()/resume() porque Thread ya tiene resume() (final y obsoleto).
    public void pauseSimulation() {
        pauseControl.pause();
        log.info("Simulacion PAUSADA");
    }

    public void resumeSimulation() {
        log.info("Simulacion REANUDADA");
        pauseControl.resume();
    }

    public boolean isPaused() {
        return pauseControl.isPaused();
    }

    /**
     * Ejecuta un comando de texto escrito por el usuario (consola o archivo).
     * Devuelve un mensaje de respuesta para mostrar.
     */
    public String executeConsoleCommand(String line) {
        try {
            ConsoleCommand c = ConsoleCommandParser.parse(line, config);
            switch (c.kind()) {
                case CALL:
                    submitHallCall(c.floor(), c.direction());
                    return "OK: llamada en piso " + c.floor() + " para " + c.direction().spanishCode();
                case GOTO:
                    submitCabinRequest(c.elevatorId(), c.floor());
                    return "OK: Elevador " + c.elevatorId() + " -> piso " + c.floor();
                case RESET:
                    if (c.elevatorId() == 0) {
                        resetSystem();
                    } else {
                        resetElevator(c.elevatorId());
                    }
                    return "OK: reset enviado";
                case PAUSE:
                    pauseSimulation();
                    return "OK: pausado";
                case RESUME:
                    resumeSimulation();
                    return "OK: reanudado";
                case STATUS:
                    return statusReport();
                case HELP:
                default:
                    return ConsoleCommand.HELP_TEXT;
            }
        } catch (ProtocolException e) {
            return "Error: " + e.getMessage();
        }
    }

    // =====================================================================
    //  Consultas
    // =====================================================================

    public List<ElevatorSnapshot> snapshots() {
        List<ElevatorSnapshot> list = new ArrayList<>();
        for (Elevator e : elevators) {
            list.add(e.snapshot());
        }
        return list;
    }

    /** Llamadas recibidas que el manager todavia no ha asignado. */
    public List<Request> queuedRequests() {
        return new ArrayList<>(requests);
    }

    /**
     * Estado de reset: todos en piso 1, hacia arriba, quietos y sin nada
     * pendiente. Solo en este estado se permite cargar un archivo.
     */
    public boolean isAtResetState() {
        if (!requests.isEmpty()) {
            return false;
        }
        for (ElevatorSnapshot s : snapshots()) {
            boolean clean = s.floor() == 1 && s.direction() == Direction.UP && !s.inMotion()
                    && s.status() == ElevatorStatus.IDLE && s.stops().isEmpty() && s.queuedCommands() == 0;
            if (!clean) {
                return false;
            }
        }
        return true;
    }

    public String statusReport() {
        StringBuilder sb = new StringBuilder("Estado de los elevadores:");
        for (ElevatorSnapshot s : snapshots()) {
            sb.append(String.format("%n  Elevador %d: piso %d, %s, %s, botones internos %s, llamadas %s",
                    s.id(), s.floor(), s.direction().label(), s.status().label(),
                    s.stops().cabinStops(), s.stops().describeHallCalls()));
        }
        return sb.toString();
    }

    // =====================================================================
    //  Hilo del manager
    // =====================================================================

    @Override
    protected void step() throws InterruptedException {
        Request r = requests.poll(POLL_MS, TimeUnit.MILLISECONDS);
        if (r == null) {
            return;
        }
        pauseControl.awaitIfPaused();
        if (r.type() == Request.Type.CABIN) {
            forwardCabinRequest(r);
        } else {
            dispatchHallCall(r.floor(), r.direction());
        }
    }

    private void forwardCabinRequest(Request r) {
        sendProtocol(elevator(r.elevatorId()), new GotoCommand(r.floor()).toProtocol());
    }

    /** El corazon del manager: elegir el mejor elevador para una llamada. */
    private void dispatchHallCall(int floor, Direction dir) {
        // 1. Botones compartidos: si alguien ya va a atender esa llamada, no se duplica.
        for (Elevator e : elevators) {
            if (e.isHandlingHallCall(floor, dir)) {
                log.info("Llamada piso " + floor + " " + dir.spanishCode()
                        + " ya esta asignada al Elevador " + e.elevatorId() + " (se ignora duplicado)");
                return;
            }
        }
        // 2. Estimar el costo de cada elevador y quedarse con el menor.
        Elevator best = null;
        long bestCost = Long.MAX_VALUE;
        int bestLoad = Integer.MAX_VALUE;
        StringBuilder detail = new StringBuilder();
        for (Elevator e : elevators) {
            ElevatorSnapshot s = e.snapshot();
            long cost = CostEstimator.estimate(s, floor, dir, config);
            int load = s.stops().count();
            detail.append(String.format(" E%d=%dms(%d pend.)", s.id(), cost, load));
            // Desempate: el menos ocupado; si sigue empatado, el de menor ID.
            if (cost < bestCost || (cost == bestCost && load < bestLoad)) {
                best = e;
                bestCost = cost;
                bestLoad = load;
            }
        }
        log.info("Llamada piso " + floor + " " + dir.spanishCode() + " asignada al Elevador "
                + best.elevatorId() + " | costos estimados:" + detail);
        // 3. Se le envia el comando por el protocolo de texto.
        sendProtocol(best, new PickupCommand(floor, dir).toProtocol());
    }

    private void sendProtocol(Elevator e, String text) {
        try {
            log.fine("-> Elevador " + e.elevatorId() + ": " + text);
            e.send(text);
        } catch (ProtocolException ex) {
            log.severe("Comando invalido para el Elevador " + e.elevatorId() + ": " + ex.getMessage());
        }
    }

    private Elevator elevator(int id) {
        return elevators.get(id - 1);
    }
}
