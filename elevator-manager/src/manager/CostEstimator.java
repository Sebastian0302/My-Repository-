package manager;

import core.SimulationConfig;
import elevator.ElevatorSnapshot;
import elevator.ElevatorStatus;
import elevator.LookAlgorithm;
import elevator.StopSet;
import model.Direction;

/**
 * Estima cuantos milisegundos tardaria un elevador en llegar a atender una
 * llamada (piso, direccion), SI se la asignaramos.
 *
 * Como lo hace: toma una copia de las paradas del elevador, le agrega la
 * nueva llamada y "simula en papel" el algoritmo LOOK paso a paso (sin
 * dormir, es instantaneo), sumando:
 *      + travelTimeMs por cada piso que recorre
 *      + stopTimeMs   por cada parada que hace ANTES de llegar
 *
 * Por que asi y no solo "distancia"? Porque un elevador cercano pero con
 * muchas paradas en el camino, o que va en la direccion contraria, puede
 * tardar mas que uno lejano y libre. Asi se cumple "el mas cercano o el
 * menos ocupado" con un solo numero comparable.
 */
public final class CostEstimator {

    private CostEstimator() {
    }

    public static long estimate(ElevatorSnapshot s, int targetFloor, Direction wanted, SimulationConfig cfg) {
        long travel = cfg.travelTimeMs();
        long stop = cfg.stopTimeMs();
        StopSet stops = s.stops().copy();
        int floor = s.floor();
        Direction dir = s.direction();
        long cost = 0;

        if (s.status() == ElevatorStatus.RESETTING) {
            // Primero debe terminar de bajar al piso 1; despues empieza "limpio".
            cost += (long) (floor - 1) * travel;
            floor = 1;
            dir = Direction.UP;
            stops.clear();
        } else if (s.inMotion()) {
            // Va entre pisos: le falta terminar el viaje actual.
            cost += remaining(travel, s.progress());
            floor += dir.delta();
        } else if (s.status() == ElevatorStatus.DOORS_OPEN) {
            cost += remaining(stop, s.progress());
        }

        stops.addHall(targetFloor, wanted);
        int maxSteps = 6 * stops.floors() + 10; // limite de seguridad
        for (int i = 0; i < maxSteps; i++) {
            LookAlgorithm.Decision d = LookAlgorithm.decide(floor, dir, stops);
            switch (d.action()) {
                case IDLE:
                    return cost; // no deberia ocurrir: la llamada esta en stops
                case TURN:
                    dir = d.direction();
                    break;
                case MOVE:
                    floor += dir.delta();
                    cost += travel;
                    break;
                case STOP:
                    dir = d.direction();
                    if (floor == targetFloor && dir == wanted) {
                        return cost; // llego!
                    }
                    stops.clearAt(floor, dir);
                    cost += stop;
                    break;
                default:
                    break;
            }
        }
        return Long.MAX_VALUE / 2;
    }

    private static long remaining(long total, double progress) {
        return Math.round(total * (1.0 - Math.min(1.0, Math.max(0.0, progress))));
    }
}
