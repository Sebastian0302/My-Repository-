package logging;

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * Da formato a cada linea de log. Ejemplo:
 *
 *   [2026-11-02 10:15:03.120] [Elevador 2] INFO    Sube del piso 3 al piso 4
 *
 * El nombre del Logger ("Elevador 2", "Manager") identifica quien escribio,
 * tal como pide el enunciado ("el log debe tener el ID del elevador").
 * Hereda de java.util.logging.Formatter y sobreescribe format().
 */
public class SimulationLogFormatter extends Formatter {

    @Override
    public String format(LogRecord r) {
        return String.format("[%1$tF %1$tT.%1$tL] [%2$s] %3$-7s %4$s%n",
                r.getMillis(), r.getLoggerName(), r.getLevel().getName(), formatMessage(r));
    }
}
