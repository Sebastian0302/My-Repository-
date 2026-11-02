package logging;

import java.util.logging.LogRecord;
import java.util.logging.StreamHandler;

/**
 * Escribe los logs en la ventana de linea de comandos (System.out).
 *
 * Se hereda de StreamHandler en lugar de usar ConsoleHandler porque
 * ConsoleHandler escribe en System.err. Se sobreescriben dos metodos:
 *  - publish: hace flush inmediato para ver cada linea al momento
 *  - close:   NO cierra System.out (solo hace flush)
 */
public class StdoutHandler extends StreamHandler {

    public StdoutHandler() {
        super(System.out, new SimulationLogFormatter());
    }

    @Override
    public synchronized void publish(LogRecord record) {
        super.publish(record);
        flush();
    }

    @Override
    public synchronized void close() {
        flush();
    }
}
