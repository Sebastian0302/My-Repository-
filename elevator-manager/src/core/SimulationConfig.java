package core;

/**
 * Parametros de la simulacion que el usuario ingresa al iniciar el programa.
 *
 * Es un "record" (Java 16+): una clase inmutable cuyos campos, constructor,
 * getters, equals, hashCode y toString los genera el compilador.
 *
 * @param elevatorCount cantidad de elevadores (N >= 1)
 * @param floorCount    cantidad de pisos del edificio (>= 2)
 * @param travelTimeMs  milisegundos que tarda un elevador en moverse UN piso
 * @param stopTimeMs    milisegundos que un elevador espera en una parada
 */
public record SimulationConfig(int elevatorCount, int floorCount, long travelTimeMs, long stopTimeMs) {

    /** Constructor compacto: valida los datos antes de asignarlos. */
    public SimulationConfig {
        if (elevatorCount < 1) {
            throw new IllegalArgumentException("Debe haber al menos 1 elevador");
        }
        if (floorCount < 2) {
            throw new IllegalArgumentException("El edificio debe tener al menos 2 pisos");
        }
        if (travelTimeMs <= 0 || stopTimeMs <= 0) {
            throw new IllegalArgumentException("Los tiempos deben ser mayores que 0 ms");
        }
    }

    public static SimulationConfig defaults() {
        return new SimulationConfig(3, 10, 1000, 2000);
    }

    /**
     * Permite pasar valores por linea de comandos para pre-llenar el dialogo:
     * java manager.ElevatorManager [elevadores] [pisos] [msEntrePisos] [msParada]
     */
    public static SimulationConfig fromArgsOrDefaults(String[] args) {
        SimulationConfig d = defaults();
        try {
            int e = args.length > 0 ? Integer.parseInt(args[0]) : d.elevatorCount();
            int f = args.length > 1 ? Integer.parseInt(args[1]) : d.floorCount();
            long t = args.length > 2 ? Long.parseLong(args[2]) : d.travelTimeMs();
            long s = args.length > 3 ? Long.parseLong(args[3]) : d.stopTimeMs();
            return new SimulationConfig(e, f, t, s);
        } catch (IllegalArgumentException ex) { // NumberFormatException tambien es IllegalArgumentException
            System.err.println("Argumentos invalidos (" + ex.getMessage() + "), se usan valores por defecto");
            return d;
        }
    }

    @Override
    public String toString() {
        return elevatorCount + " elevador(es), " + floorCount + " pisos, "
                + travelTimeMs + " ms entre pisos, " + stopTimeMs + " ms por parada";
    }
}
