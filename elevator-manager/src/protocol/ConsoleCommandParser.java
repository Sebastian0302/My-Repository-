package protocol;

import core.SimulationConfig;
import model.Direction;

/** Interpreta y VALIDA (rangos de pisos/elevadores) un comando de consola. */
public final class ConsoleCommandParser {

    private ConsoleCommandParser() {
    }

    public static ConsoleCommand parse(String line, SimulationConfig cfg) throws ProtocolException {
        if (line == null || line.isBlank()) {
            throw new ProtocolException("Comando vacio");
        }
        String[] p = line.trim().split("\\s+");
        String keyword = p[0].toUpperCase();
        try {
            switch (keyword) {
                case "CALL": {
                    CommandParser.expectArgs(p, 2, "CALL <piso> <UP|DOWN>");
                    int floor = floor(p[1], cfg);
                    Direction dir = Direction.parse(p[2]);
                    if (floor == cfg.floorCount() && dir == Direction.UP) {
                        throw new ProtocolException("En el ultimo piso no existe el boton de SUBIR");
                    }
                    if (floor == 1 && dir == Direction.DOWN) {
                        throw new ProtocolException("En el piso 1 no existe el boton de BAJAR");
                    }
                    return new ConsoleCommand(ConsoleCommand.Kind.CALL, 0, floor, dir);
                }
                case "GOTO":
                    CommandParser.expectArgs(p, 2, "GOTO <elevador> <piso>");
                    return new ConsoleCommand(ConsoleCommand.Kind.GOTO, elevator(p[1], cfg), floor(p[2], cfg), null);
                case "RESET":
                    if (p.length == 1) {
                        return new ConsoleCommand(ConsoleCommand.Kind.RESET, 0, 0, null);
                    }
                    CommandParser.expectArgs(p, 1, "RESET [elevador]");
                    return new ConsoleCommand(ConsoleCommand.Kind.RESET, elevator(p[1], cfg), 0, null);
                case "PAUSE":
                case "RESUME":
                case "STATUS":
                case "HELP":
                    CommandParser.expectArgs(p, 0, keyword);
                    return new ConsoleCommand(ConsoleCommand.Kind.valueOf(keyword), 0, 0, null);
                default:
                    throw new ProtocolException("Comando desconocido: '" + p[0] + "'. Escriba HELP");
            }
        } catch (IllegalArgumentException e) {
            throw new ProtocolException(e.getMessage());
        }
    }

    private static int floor(String s, SimulationConfig cfg) throws ProtocolException {
        int f = Integer.parseInt(s);
        if (f < 1 || f > cfg.floorCount()) {
            throw new ProtocolException("Piso fuera de rango: " + f + " (1.." + cfg.floorCount() + ")");
        }
        return f;
    }

    private static int elevator(String s, SimulationConfig cfg) throws ProtocolException {
        int e = Integer.parseInt(s);
        if (e < 1 || e > cfg.elevatorCount()) {
            throw new ProtocolException("Elevador fuera de rango: " + e + " (1.." + cfg.elevatorCount() + ")");
        }
        return e;
    }
}
