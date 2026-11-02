package protocol;

import model.Direction;

/** "PICKUP 5 DOWN": el manager asigna una llamada del piso 5 para bajar. */
public class PickupCommand extends Command {

    public static final String KEYWORD = "PICKUP";
    private final int floor;
    private final Direction direction;

    public PickupCommand(int floor, Direction direction) {
        super(KEYWORD);
        this.floor = floor;
        this.direction = direction;
    }

    public int floor() {
        return floor;
    }

    public Direction direction() {
        return direction;
    }

    @Override
    public void execute(CommandTarget target) {
        target.addHallCall(floor, direction);
    }

    @Override
    protected String arguments() {
        return floor + " " + direction.name();
    }
}
