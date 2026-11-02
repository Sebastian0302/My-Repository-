package logging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Crea los Logger (java.util.logging) de la simulacion.
 *
 * Cada Logger escribe en TRES lugares a la vez (un Logger puede tener varios
 * Handler):
 *   1. Su propio archivo:   logs/elevador1.log, logs/elevador2.log, logs/manager.log
 *   2. La linea de comandos (StdoutHandler)
 *   3. El panel de logs de la GUI (GuiLogHandler)
 */
public final class LogFactory {

    public static final String LOG_DIR = "logs";

    private static final StdoutHandler CONSOLE = new StdoutHandler();
    private static final GuiLogHandler GUI = new GuiLogHandler();
    private static final List<Logger> CREATED = new ArrayList<>();

    private LogFactory() {
    }

    public static GuiLogHandler guiHandler() {
        return GUI;
    }

    public static Logger elevatorLogger(int elevatorId) {
        return createLogger("Elevador " + elevatorId, "elevador" + elevatorId + ".log");
    }

    public static Logger managerLogger() {
        return createLogger("Manager", "manager.log");
    }

    public static synchronized Logger createLogger(String name, String fileName) {
        Logger logger = Logger.getLogger(name);
        removeHandlers(logger); // por si es una simulacion reiniciada
        logger.setUseParentHandlers(false); // evita que el log se imprima doble
        logger.setLevel(Level.ALL);
        try {
            Files.createDirectories(Path.of(LOG_DIR));
            // append = true: cada ejecucion agrega al final del archivo
            FileHandler file = new FileHandler(LOG_DIR + "/" + fileName, true);
            file.setEncoding("UTF-8");
            file.setFormatter(new SimulationLogFormatter());
            logger.addHandler(file);
        } catch (IOException e) {
            System.err.println("No se pudo crear el archivo de log " + fileName + ": " + e.getMessage());
        }
        logger.addHandler(CONSOLE);
        logger.addHandler(GUI);
        CREATED.add(logger);
        return logger;
    }

    /** Cierra los archivos de log (se llama al detener la simulacion). */
    public static synchronized void closeAll() {
        for (Logger l : CREATED) {
            removeHandlers(l);
        }
        CREATED.clear();
    }

    private static void removeHandlers(Logger logger) {
        for (Handler h : logger.getHandlers()) {
            logger.removeHandler(h);
            if (h instanceof FileHandler) {
                h.close();
            }
        }
    }
}
