package elevator;

import model.Direction;

/**
 * "Fotografia" inmutable del estado de un elevador en un instante.
 *
 * Los demas hilos (manager y GUI) NUNCA leen los campos internos del
 * elevador directamente; piden un snapshot. Asi se evitan condiciones de
 * carrera: el snapshot se arma dentro de un bloque synchronized y despues
 * cada quien lo puede leer con calma.
 *
 * @param stops    paradas pendientes (incluye comandos aun en la cola)
 * @param inMotion true si esta viajando entre 'floor' y el siguiente piso
 * @param progress avance (0..1) del viaje o de la parada actual
 */
public record ElevatorSnapshot(int id, int floor, Direction direction, ElevatorStatus status,
                               boolean inMotion, double progress, StopSet stops, int queuedCommands) {

    /** Estado inicial (piso 1, hacia arriba) para dibujar antes de iniciar. */
    public static ElevatorSnapshot initial(int id, int floors) {
        return new ElevatorSnapshot(id, 1, Direction.UP, ElevatorStatus.IDLE, false, 0, new StopSet(floors), 0);
    }

    /** Posicion "continua" para la animacion, ej. 3.4 = entre piso 3 y 4. */
    public double visualPosition() {
        return inMotion ? floor + direction.delta() * progress : floor;
    }
}
