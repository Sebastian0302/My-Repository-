package gui;

import core.SimulationConfig;
import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * Ventana inicial: pide al usuario la cantidad de elevadores, de pisos, el
 * tiempo de movimiento entre pisos y el tiempo de espera en cada parada.
 * JSpinner solo deja escribir numeros dentro del rango, asi se evita
 * validar texto a mano.
 */
public final class ConfigDialog {

    private ConfigDialog() {
    }

    /** @return la configuracion elegida, o null si el usuario cancelo. */
    public static SimulationConfig ask(Component parent, SimulationConfig defaults) {
        JSpinner elevators = new JSpinner(new SpinnerNumberModel(defaults.elevatorCount(), 1, 12, 1));
        JSpinner floors = new JSpinner(new SpinnerNumberModel(defaults.floorCount(), 2, 60, 1));
        JSpinner travel = new JSpinner(new SpinnerNumberModel((int) defaults.travelTimeMs(), 50, 60000, 100));
        JSpinner stop = new JSpinner(new SpinnerNumberModel((int) defaults.stopTimeMs(), 50, 60000, 100));

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Cantidad de elevadores:"));
        form.add(elevators);
        form.add(new JLabel("Cantidad de pisos:"));
        form.add(floors);
        form.add(new JLabel("Tiempo entre pisos (ms):"));
        form.add(travel);
        form.add(new JLabel("Tiempo de espera en parada (ms):"));
        form.add(stop);

        int option = JOptionPane.showConfirmDialog(parent, form, "Elevator Manager - Configuracion",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (option != JOptionPane.OK_OPTION) {
            return null;
        }
        return new SimulationConfig((Integer) elevators.getValue(), (Integer) floors.getValue(),
                (Integer) travel.getValue(), (Integer) stop.getValue());
    }
}
