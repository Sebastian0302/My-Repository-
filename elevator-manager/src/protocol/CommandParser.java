package protocol;

import model.Direction;

/** Convierte el texto del protocolo Manager -> Elevador en un objeto Command. */
public final class CommandParser {

    private CommandParser() {
    }

    public static Command parse(String text) throws ProtocolException {
        if (text == null || text.isBlank()) {
            throw new ProtocolException("Comando vacio");
        }
        String[] p = text.trim().split("\\s+");
        String keyword = p[0].toUpperCase();
        try {
            switch (keyword) {
                case GotoCommand.KEYWORD:
                    expectArgs(p, 1, "GOTO <piso>");
                    return new GotoCommand(Integer.parseInt(p[1]));
                case PickupCommand.KEYWORD:
                    expectArgs(p, 2, "PICKUP <piso> <UP|DOWN>");
                    return new PickupCommand(Integer.parseInt(p[1]), Direction.parse(p[2]));
                case ResetCommand.KEYWORD:
                    expectArgs(p, 0, "RESET");
                    return new ResetCommand();
                default:
                    throw new ProtocolException("Comando desconocido: " + keyword);
            }
        } catch (IllegalArgumentException e) { // incluye NumberFormatException
            throw new ProtocolException("Argumento invalido en '" + text + "': " + e.getMessage());
        }
    }

    static void expectArgs(String[] parts, int count, String usage) throws ProtocolException {
        if (parts.length - 1 != count) {
            throw new ProtocolException("Uso: " + usage);
        }
    }
}
