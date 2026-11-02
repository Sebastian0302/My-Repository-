package gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import model.Direction;

/**
 * Los botones que estan AFUERA de los elevadores: en cada piso hay un boton
 * para SUBIR y otro para BAJAR, compartidos por todos los elevadores.
 * (El piso 1 no tiene BAJAR y el ultimo piso no tiene SUBIR.)
 * Un boton se queda encendido (amarillo) hasta que algun elevador lo atiende.
 */
class FloorCallPanel extends JPanel {

    private final JButton[] upButtons;
    private final JButton[] downButtons;
    private final Color defaultColor;

    FloorCallPanel(int floors, BiConsumer<Integer, Direction> onCall) {
        super(new BorderLayout());
        setBorder(BorderFactory.createTitledBorder("Botones de piso"));
        upButtons = new JButton[floors + 1];
        downButtons = new JButton[floors + 1];

        JPanel grid = new JPanel(new GridLayout(floors, 3, 4, 2));
        for (int f = floors; f >= 1; f--) { // de arriba hacia abajo, como un edificio
            grid.add(new JLabel("Piso " + f, JLabel.RIGHT));
            upButtons[f] = makeButton(f, Direction.UP, f < floors, onCall);
            downButtons[f] = makeButton(f, Direction.DOWN, f > 1, onCall);
            grid.add(upButtons[f]);
            grid.add(downButtons[f]);
        }
        defaultColor = upButtons[1].getBackground();
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(grid, BorderLayout.NORTH);
        add(new JScrollPane(wrapper), BorderLayout.CENTER);
    }

    private JButton makeButton(int floor, Direction dir, boolean exists, BiConsumer<Integer, Direction> onCall) {
        JButton b = new JButton(dir.arrow());
        b.setToolTipText("Piso " + floor + ": llamar elevador para " + dir.spanishCode());
        b.setFocusPainted(false);
        b.setMargin(new java.awt.Insets(1, 6, 1, 6));
        b.setVisible(exists);
        b.addActionListener(e -> onCall.accept(floor, dir));
        return b;
    }

    void update(boolean[] upLit, boolean[] downLit) {
        for (int f = 1; f < upButtons.length; f++) {
            upButtons[f].setBackground(upLit[f] ? Theme.LIT : defaultColor);
            downButtons[f].setBackground(downLit[f] ? Theme.LIT : defaultColor);
        }
    }

    void setControlsEnabled(boolean enabled) {
        for (int f = 1; f < upButtons.length; f++) {
            upButtons[f].setEnabled(enabled);
            downButtons[f].setEnabled(enabled);
        }
    }
}
