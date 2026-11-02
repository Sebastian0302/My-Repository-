package logging;

import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Handler que reenvia cada linea de log a la interfaz grafica (el panel de
 * "Logs" de la consola de comandos). La GUI se registra con setListener.
 */
public class GuiLogHandler extends Handler {

    private volatile Consumer<String> listener;

    public GuiLogHandler() {
        setFormatter(new SimulationLogFormatter());
        setLevel(java.util.logging.Level.INFO); // en la GUI no se muestran los FINE
    }

    public void setListener(Consumer<String> listener) {
        this.listener = listener;
    }

    @Override
    public void publish(LogRecord record) {
        Consumer<String> l = listener;
        if (l != null && isLoggable(record)) {
            l.accept(getFormatter().format(record));
        }
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }
}
