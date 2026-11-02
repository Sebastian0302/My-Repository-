package elevator;

/** Estado en el que se encuentra un elevador (se muestra en la GUI). */
public enum ElevatorStatus {
    IDLE("En espera"),
    MOVING("En movimiento"),
    DOORS_OPEN("Puertas abiertas"),
    RESETTING("Reset en curso");

    private final String label;

    ElevatorStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
