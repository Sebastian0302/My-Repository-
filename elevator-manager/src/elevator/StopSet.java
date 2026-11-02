package elevator;

import java.util.ArrayList;
import java.util.List;
import model.Direction;

/**
 * Conjunto de pisos que un elevador tiene pendientes de visitar.
 *
 * Se guardan 3 tipos de "marcas" por piso:
 *  - cabin[f]: alguien adentro presiono el boton del piso f (dejar personas)
 *  - up[f]:    el manager le asigno una llamada en piso f para SUBIR
 *  - down[f]:  el manager le asigno una llamada en piso f para BAJAR
 *
 * Se usan arreglos boolean indexados por piso (posicion 0 sin usar) porque
 * los pisos van de 1..floors. Un boolean evita duplicados automaticamente:
 * presionar dos veces el mismo boton no agrega dos paradas.
 *
 * Esta clase NO es thread-safe por si sola: el Elevator la protege con
 * synchronized. A otros hilos solo se les entrega una copia (copy()).
 */
public class StopSet {

    private final int floors;
    private final boolean[] cabin;
    private final boolean[] up;
    private final boolean[] down;

    public StopSet(int floors) {
        this.floors = floors;
        this.cabin = new boolean[floors + 1];
        this.up = new boolean[floors + 1];
        this.down = new boolean[floors + 1];
    }

    public int floors() {
        return floors;
    }

    public boolean isValidFloor(int floor) {
        return floor >= 1 && floor <= floors;
    }

    private boolean[] hall(Direction dir) {
        return dir == Direction.UP ? up : down;
    }

    public void addCabin(int floor) {
        cabin[floor] = true;
    }

    public void addHall(int floor, Direction dir) {
        hall(dir)[floor] = true;
    }

    public boolean hasCabin(int floor) {
        return cabin[floor];
    }

    public boolean hasHall(int floor, Direction dir) {
        return hall(dir)[floor];
    }

    public boolean hasAnyAt(int floor) {
        return cabin[floor] || up[floor] || down[floor];
    }

    public boolean hasAnyAbove(int floor) {
        for (int f = floor + 1; f <= floors; f++) {
            if (hasAnyAt(f)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAnyBelow(int floor) {
        for (int f = floor - 1; f >= 1; f--) {
            if (hasAnyAt(f)) {
                return true;
            }
        }
        return false;
    }

    /** Hay algo pendiente "adelante" segun la direccion de movimiento. */
    public boolean hasAnyAhead(int floor, Direction dir) {
        return dir == Direction.UP ? hasAnyAbove(floor) : hasAnyBelow(floor);
    }

    public boolean isEmpty() {
        return count() == 0;
    }

    /** Cantidad total de marcas pendientes (sirve para medir "que tan ocupado" esta). */
    public int count() {
        int c = 0;
        for (int f = 1; f <= floors; f++) {
            if (cabin[f]) c++;
            if (up[f]) c++;
            if (down[f]) c++;
        }
        return c;
    }

    /** Se atendio el piso: se apaga el boton interno y la llamada en la direccion dada. */
    public void clearAt(int floor, Direction dir) {
        cabin[floor] = false;
        hall(dir)[floor] = false;
    }

    public void clear() {
        for (int f = 0; f <= floors; f++) {
            cabin[f] = false;
            up[f] = false;
            down[f] = false;
        }
    }

    public StopSet copy() {
        StopSet c = new StopSet(floors);
        System.arraycopy(cabin, 0, c.cabin, 0, cabin.length);
        System.arraycopy(up, 0, c.up, 0, up.length);
        System.arraycopy(down, 0, c.down, 0, down.length);
        return c;
    }

    public List<Integer> cabinStops() {
        List<Integer> list = new ArrayList<>();
        for (int f = 1; f <= floors; f++) {
            if (cabin[f]) list.add(f);
        }
        return list;
    }

    /** Ej.: "3▲ 7▼" (llamadas asignadas por el manager). */
    public String describeHallCalls() {
        StringBuilder sb = new StringBuilder();
        for (int f = 1; f <= floors; f++) {
            if (up[f]) sb.append(f).append(Direction.UP.arrow()).append(' ');
            if (down[f]) sb.append(f).append(Direction.DOWN.arrow()).append(' ');
        }
        return sb.length() == 0 ? "(ninguno)" : sb.toString().trim();
    }
}
