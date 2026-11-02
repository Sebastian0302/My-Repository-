package protocol;

import java.util.Objects;

/**
 * Comando del protocolo de comunicacion Manager -> Elevador (punto extra).
 *
 * El manager NO llama metodos del elevador para darle ordenes: le manda un
 * TEXTO como "PICKUP 5 DOWN". El elevador lo interpreta (CommandParser), lo
 * encola, y lo ejecuta en SU propio hilo. Formato:
 *
 *     PALABRA_CLAVE [argumentos separados por espacio]
 *
 *     GOTO <piso>              -> boton interno: ir al piso
 *     PICKUP <piso> <UP|DOWN>  -> recoger a alguien en un piso
 *     RESET                    -> borrar todo y regresar al piso 1
 *
 * HERENCIA CON LOGICA: cada subclase sabe (1) que argumentos lleva y
 * (2) que hacerle al elevador. El codigo que procesa la cola del elevador no
 * necesita un if/switch por tipo: simplemente llama command.execute(...)
 * (polimorfismo).
 */
public abstract class Command {

    private final String keyword;

    protected Command(String keyword) {
        this.keyword = keyword;
    }

    public String keyword() {
        return keyword;
    }

    /** Aplica el comando sobre el elevador (se llama en el hilo del elevador). */
    public abstract void execute(CommandTarget target) throws InterruptedException;

    /** Argumentos en texto (vacio si no tiene). */
    protected abstract String arguments();

    /** Representacion textual del comando, la que viaja por el "protocolo". */
    public final String toProtocol() {
        String args = arguments();
        return args.isEmpty() ? keyword : keyword + " " + args;
    }

    // Dos comandos son iguales si su texto es igual (sirve para detectar duplicados).
    @Override
    public boolean equals(Object o) {
        return o instanceof Command other && toProtocol().equals(other.toProtocol());
    }

    @Override
    public int hashCode() {
        return Objects.hash(toProtocol());
    }

    @Override
    public String toString() {
        return toProtocol();
    }
}
