package gui;

import java.awt.Color;

/** Colores usados por la interfaz. */
final class Theme {

    static final Color LIT = new Color(255, 196, 0);          // boton "encendido"
    static final Color IDLE = new Color(120, 130, 145);
    static final Color MOVING = new Color(40, 110, 210);
    static final Color DOORS = new Color(40, 160, 90);
    static final Color RESETTING = new Color(220, 120, 30);
    static final Color SHAFT = new Color(232, 236, 242);
    static final Color FLOOR_LINE = new Color(200, 205, 215);

    private Theme() {
    }

    static Color forStatus(elevator.ElevatorStatus s) {
        return switch (s) {
            case IDLE -> IDLE;
            case MOVING -> MOVING;
            case DOORS_OPEN -> DOORS;
            case RESETTING -> RESETTING;
        };
    }
}
