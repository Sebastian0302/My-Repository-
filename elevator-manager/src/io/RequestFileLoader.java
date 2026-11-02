package io;

import core.SimulationConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import protocol.ConsoleCommandParser;
import protocol.ProtocolException;

/**
 * Lee un archivo de texto con una lista de requerimientos.
 *
 * Formato: una instruccion por linea
 *
 *     <milisegundos_desde_inicio> <comando de consola>
 *
 *     # comentario
 *     0     CALL 5 DOWN
 *     1500  GOTO 1 9
 *     3000  CALL 1 UP
 *
 * Se valida TODO el archivo antes de ejecutar nada: si hay un error se
 * reporta con el numero de linea y no se ejecuta ninguna instruccion.
 */
public final class RequestFileLoader {

    private RequestFileLoader() {
    }

    public static List<ScheduledCommand> load(Path file, SimulationConfig cfg) throws IOException, ProtocolException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<ScheduledCommand> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            int n = i + 1;
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+", 2);
            if (parts.length < 2) {
                throw new ProtocolException("Linea " + n + ": formato esperado '<ms> <comando>'");
            }
            long at;
            try {
                at = Long.parseLong(parts[0]);
            } catch (NumberFormatException e) {
                throw new ProtocolException("Linea " + n + ": tiempo invalido '" + parts[0] + "'");
            }
            if (at < 0) {
                throw new ProtocolException("Linea " + n + ": el tiempo no puede ser negativo");
            }
            try {
                ConsoleCommandParser.parse(parts[1], cfg); // solo validar
            } catch (ProtocolException e) {
                throw new ProtocolException("Linea " + n + ": " + e.getMessage());
            }
            result.add(new ScheduledCommand(at, parts[1], n));
        }
        // Ordenar por tiempo (sort es estable: lineas con el mismo tiempo mantienen su orden)
        result.sort(Comparator.comparingLong(ScheduledCommand::atMs));
        return result;
    }
}
