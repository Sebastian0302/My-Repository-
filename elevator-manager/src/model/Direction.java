package model;

/**
 * Direccion de movimiento de un elevador o de una llamada desde un piso.
 * Un enum en Java es una clase con un numero fijo de instancias; por eso
 * puede tener campos y metodos.
 */
public enum Direction {
    UP(+1, "hacia arriba", "SUBIR", "▲"),
    DOWN(-1, "hacia abajo", "BAJAR", "▼");

    /** +1 o -1: cuanto cambia el numero de piso al moverse en esta direccion. */
    private final int delta;
    private final String label;
    private final String spanishCode;
    private final String arrow;

    Direction(int delta, String label, String spanishCode, String arrow) {
        this.delta = delta;
        this.label = label;
        this.spanishCode = spanishCode;
        this.arrow = arrow;
    }

    public int delta() {
        return delta;
    }

    public String label() {
        return label;
    }

    public String spanishCode() {
        return spanishCode;
    }

    public String arrow() {
        return arrow;
    }

    public Direction opposite() {
        return this == UP ? DOWN : UP;
    }

    /** Acepta UP/DOWN, SUBIR/BAJAR, ARRIBA/ABAJO, U/D (sin importar mayusculas). */
    public static Direction parse(String text) {
        switch (text.trim().toUpperCase()) {
            case "UP", "U", "SUBIR", "ARRIBA", "S":
                return UP;
            case "DOWN", "D", "BAJAR", "ABAJO", "B":
                return DOWN;
            default:
                throw new IllegalArgumentException("Direccion invalida: '" + text + "' (use UP o DOWN)");
        }
    }
}
