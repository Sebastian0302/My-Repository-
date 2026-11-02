package protocol;

/** "GOTO 7": alguien dentro del elevador presiono el boton del piso 7. */
public class GotoCommand extends Command {

    public static final String KEYWORD = "GOTO";
    private final int floor;

    public GotoCommand(int floor) {
        super(KEYWORD);
        this.floor = floor;
    }

    public int floor() {
        return floor;
    }

    @Override
    public void execute(CommandTarget target) {
        target.addCabinStop(floor);
    }

    @Override
    protected String arguments() {
        return String.valueOf(floor);
    }
}
