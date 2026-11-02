package core;

import java.util.function.DoubleConsumer;

/**
 * Clase base (abstracta) de todo "proceso independiente" de la simulacion.
 *
 * HERENCIA CON LOGICA: Elevator, ElevatorManager y RequestFilePlayer heredan
 * de esta clase. Todos comparten el mismo ciclo de vida de un hilo:
 *
 *     onStart()  ->  repetir { esperar si hay pausa; step(); }  ->  onStop()
 *
 * y cada subclase solo define QUE hace en un paso (step). Este patron se
 * llama "Template Method": el esqueleto del algoritmo (run) es final y vive
 * en la clase padre; los detalles los ponen las hijas.
 *
 * Ademas centraliza dos cosas que todas las hijas necesitan:
 *  - detener el hilo de forma segura (shutdown)
 *  - "dormir" respetando la pausa (simSleep)
 */
public abstract class SimulationProcess extends Thread {

    /** Cada cuanto se revisa la pausa mientras se "duerme". */
    private static final long TICK_MS = 20;

    protected final PauseControl pauseControl;

    /** volatile: lo escribe un hilo (la GUI) y lo lee otro (este proceso). */
    private volatile boolean running = true;

    protected SimulationProcess(String name, PauseControl pauseControl) {
        super(name);
        this.pauseControl = pauseControl;
        // Hilo "daemon": si se cierra la ventana, la JVM no espera a este hilo.
        setDaemon(true);
    }

    @Override
    public final void run() {
        onStart();
        try {
            while (running && !isInterrupted()) {
                pauseControl.awaitIfPaused();
                step();
            }
        } catch (InterruptedException e) {
            // Es la forma normal de terminar: shutdown() interrumpe el hilo.
        } finally {
            onStop();
        }
    }

    /** Un paso de trabajo del proceso. Lo implementa cada subclase. */
    protected abstract void step() throws InterruptedException;

    /** Gancho opcional que se ejecuta al iniciar el hilo. */
    protected void onStart() {
    }

    /** Gancho opcional que se ejecuta al terminar el hilo. */
    protected void onStop() {
    }

    /** Solicita detener el proceso (desde cualquier hilo). */
    public void shutdown() {
        running = false;
        interrupt(); // despierta al hilo si esta en sleep()/wait()/poll()
    }

    /** Termina el ciclo desde el propio hilo, sin interrumpirlo. */
    protected void finishLoop() {
        running = false;
    }

    public boolean isRunning() {
        return running && isAlive();
    }

    /**
     * Duerme 'ms' milisegundos de TIEMPO DE SIMULACION: si la simulacion se
     * pausa a la mitad, el tiempo deja de correr hasta que se reanude.
     * Se duerme en pedacitos de TICK_MS para poder reaccionar a la pausa.
     *
     * @param progress si no es null, recibe el avance (0.0 a 1.0); lo usa la
     *                 animacion grafica para dibujar el elevador entre pisos.
     */
    protected void simSleep(long ms, DoubleConsumer progress) throws InterruptedException {
        long elapsed = 0;
        while (elapsed < ms) {
            pauseControl.awaitIfPaused();
            long chunk = Math.min(TICK_MS, ms - elapsed);
            Thread.sleep(chunk);
            elapsed += chunk;
            if (progress != null) {
                progress.accept((double) elapsed / ms);
            }
        }
    }
}
