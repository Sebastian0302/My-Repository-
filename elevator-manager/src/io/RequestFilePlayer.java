package io;

import core.PauseControl;
import core.SimulationProcess;
import java.util.List;
import java.util.logging.Logger;
import manager.ElevatorManager;

/**
 * Proceso que "reproduce" un archivo de requerimientos: espera el tiempo de
 * cada linea y se la entrega al manager, como si un usuario la escribiera.
 * Tambien hereda de SimulationProcess, asi que respeta pausa y detener.
 */
public class RequestFilePlayer extends SimulationProcess {

    private final List<ScheduledCommand> script;
    private final ElevatorManager manager;
    private final Logger log;
    private final String fileName;
    private int index = 0;
    private long elapsedMs = 0;

    public RequestFilePlayer(String fileName, List<ScheduledCommand> script, ElevatorManager manager,
                             PauseControl pauseControl) {
        super("RequestFilePlayer", pauseControl);
        this.fileName = fileName;
        this.script = script;
        this.manager = manager;
        this.log = manager.logger();
    }

    @Override
    protected void onStart() {
        log.info("Archivo '" + fileName + "' cargado: " + script.size() + " requerimiento(s)");
    }

    @Override
    protected void step() throws InterruptedException {
        if (index >= script.size()) {
            log.info("Archivo '" + fileName + "' completado");
            finishLoop();
            return;
        }
        ScheduledCommand sc = script.get(index);
        long wait = sc.atMs() - elapsedMs;
        if (wait > 0) {
            simSleep(wait, null);
            elapsedMs += wait;
        }
        String response = manager.executeConsoleCommand(sc.command());
        log.info("[archivo linea " + sc.lineNumber() + "] " + sc.command() + " -> " + response);
        index++;
    }
}
