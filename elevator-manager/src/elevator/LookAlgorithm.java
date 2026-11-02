package elevator;

import model.Direction;

/**
 * El "cerebro" de movimiento de un elevador: algoritmo LOOK.
 *
 * Idea (como los elevadores reales):
 *  1. Si voy subiendo, sigo subiendo mientras haya pisos pendientes arriba,
 *     parando en los que tengan boton interno o llamada para SUBIR.
 *  2. Cuando ya no queda nada arriba, cambio de direccion y hago lo mismo
 *     bajando.
 *  3. Si no hay nada pendiente, me quedo en espera.
 *
 * Esto cumple las reglas del enunciado:
 *  - "Un elevador que se dirige hacia arriba no debe cambiar de direccion
 *     para traer una persona. Primero tiene que subir a donde se dirigia."
 *  - "Un elevador que va hacia una direccion puede pasar recogiendo en el
 *     camino personas que vayan hacia la misma direccion."
 *
 * Es una funcion PURA (no tiene estado, no usa hilos): recibe la situacion y
 * devuelve una decision. Por eso la reutilizan tanto el Elevator (para
 * moverse de verdad) como el CostEstimator del manager (para "simular en su
 * cabeza" cuanto tardaria cada elevador en atender una llamada).
 */
public final class LookAlgorithm {

    /** Las 4 cosas que un elevador puede hacer en un paso. */
    public enum Action {
        IDLE,  // no hay nada que hacer
        MOVE,  // moverse un piso en 'direction'
        STOP,  // parar en el piso actual (abrir puertas) quedando en 'direction'
        TURN   // solo cambiar de direccion a 'direction'
    }

    public record Decision(Action action, Direction direction) {
    }

    private LookAlgorithm() {
    }

    public static Decision decide(int floor, Direction dir, StopSet stops) {
        if (stops.isEmpty()) {
            return new Decision(Action.IDLE, dir);
        }
        // a) Alguien quiere bajarse aqui, o alguien aqui quiere ir hacia donde voy.
        if (stops.hasCabin(floor) || stops.hasHall(floor, dir)) {
            return new Decision(Action.STOP, dir);
        }
        // b) Todavia hay trabajo adelante: sigo en la misma direccion.
        if (stops.hasAnyAhead(floor, dir)) {
            return new Decision(Action.MOVE, dir);
        }
        // c) No hay nada adelante -> toca dar la vuelta.
        Direction opposite = dir.opposite();
        if (stops.hasHall(floor, opposite)) {
            // Alguien en ESTE piso quiere ir al otro lado: cambio y paro aqui.
            return new Decision(Action.STOP, opposite);
        }
        // Lo pendiente esta "atras": solo cambio de direccion.
        return new Decision(Action.TURN, opposite);
    }
}
