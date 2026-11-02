package protocol;

import model.Direction;

/**
 * Lo que un comando puede pedirle a un elevador. Elevator implementa esta
 * interfaz. Asi el paquete protocol no depende de la clase Elevator concreta.
 */
public interface CommandTarget {

    void addCabinStop(int floor);

    void addHallCall(int floor, Direction direction);

    void reset() throws InterruptedException;
}
