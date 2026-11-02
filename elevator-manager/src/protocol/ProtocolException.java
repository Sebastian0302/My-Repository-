package protocol;

/** Error al interpretar un comando de texto del protocolo. */
public class ProtocolException extends Exception {

    public ProtocolException(String message) {
        super(message);
    }
}
