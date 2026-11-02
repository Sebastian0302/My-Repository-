package protocol;

/** "RESET": borrar todos los comandos y regresar a piso 1, direccion arriba. */
public class ResetCommand extends Command {

    public static final String KEYWORD = "RESET";

    public ResetCommand() {
        super(KEYWORD);
    }

    @Override
    public void execute(CommandTarget target) throws InterruptedException {
        target.reset();
    }

    @Override
    protected String arguments() {
        return "";
    }
}
