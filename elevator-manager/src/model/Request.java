package model;

/**
 * Un requerimiento que llega al Elevator Manager.
 *
 * Hay dos tipos (ver el enunciado):
 *  - HALL:  alguien AFUERA presiona el boton de subir/bajar en un piso.
 *           El manager decide que elevador lo atiende.
 *  - CABIN: alguien ADENTRO del elevador X presiona el boton de un piso.
 *           Ya se sabe que elevador es; el manager solo se lo reenvia.
 */
public record Request(Type type, int floor, Direction direction, int elevatorId) {

    public enum Type { HALL, CABIN }

    public static Request hall(int floor, Direction direction) {
        return new Request(Type.HALL, floor, direction, 0);
    }

    public static Request cabin(int elevatorId, int floor) {
        return new Request(Type.CABIN, floor, null, elevatorId);
    }

    @Override
    public String toString() {
        return type == Type.HALL
                ? "llamada en piso " + floor + " para " + direction.spanishCode()
                : "boton interno del Elevador " + elevatorId + " hacia piso " + floor;
    }
}
