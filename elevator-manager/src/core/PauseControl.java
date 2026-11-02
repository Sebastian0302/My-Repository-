package core;

/**
 * Control de pausa compartido por TODOS los hilos de la simulacion.
 *
 * Es un "monitor" clasico de Java: el estado (paused) solo se lee/escribe
 * dentro de metodos synchronized, y los hilos que deben esperar llaman a
 * wait(). Cuando se reanuda, notifyAll() despierta a todos los hilos que
 * estaban dormidos esperando.
 */
public class PauseControl {

    private boolean paused = false;

    public synchronized void pause() {
        paused = true;
    }

    public synchronized void resume() {
        paused = false;
        notifyAll(); // despierta a los hilos bloqueados en awaitIfPaused()
    }

    public synchronized boolean isPaused() {
        return paused;
    }

    /**
     * Bloquea al hilo que la llama mientras la simulacion este en pausa.
     * Se usa while (y no if) para protegerse de "despertares espurios".
     */
    public synchronized void awaitIfPaused() throws InterruptedException {
        while (paused) {
            wait();
        }
    }
}
