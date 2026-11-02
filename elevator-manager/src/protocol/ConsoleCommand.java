package protocol;

import model.Direction;

/**
 * Comando que el USUARIO escribe en la consola (o en un archivo de
 * requerimientos) y que recibe el Elevator Manager.
 *
 *     CALL <piso> <UP|DOWN>      boton de afuera en un piso
 *     GOTO <elevador> <piso>     boton de adentro de un elevador
 *     RESET                      reset de todo el sistema
 *     RESET <elevador>           reset de un solo elevador
 *     PAUSE / RESUME             pausar / reanudar la simulacion
 *     STATUS                     mostrar el estado de los elevadores
 *     HELP                       ayuda
 */
public record ConsoleCommand(Kind kind, int elevatorId, int floor, Direction direction) {

    public enum Kind { CALL, GOTO, RESET, PAUSE, RESUME, STATUS, HELP }

    public static final String HELP_TEXT = String.join("\n",
            "Comandos disponibles:",
            "  CALL <piso> <UP|DOWN>    llamar un elevador desde un piso (ej: CALL 5 DOWN)",
            "  GOTO <elevador> <piso>   presionar boton dentro de un elevador (ej: GOTO 2 8)",
            "  RESET [elevador]         reset de todo el sistema o de un elevador",
            "  PAUSE | RESUME           pausar / reanudar",
            "  STATUS                   estado de los elevadores",
            "  HELP                     esta ayuda");
}
