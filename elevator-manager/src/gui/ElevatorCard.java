package gui;

import elevator.ElevatorSnapshot;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Tarjeta con la informacion de UN elevador (lo que pide el enunciado):
 *  - en que piso se encuentra (y su direccion / estado)
 *  - que pisos tiene encendidos dentro (botones internos, en amarillo)
 *  - la lista de requerimientos que vienen del Elevator Manager
 * Los botones numerados simulan presionar un boton DENTRO del elevador.
 */
class ElevatorCard extends JPanel {

    private final JLabel floorLabel = new JLabel();
    private final JLabel directionLabel = new JLabel();
    private final JLabel statusLabel = new JLabel();
    private final JLabel hallLabel = new JLabel();
    private final JButton[] cabinButtons;
    private final JButton resetButton = new JButton("Reset");
    private final Color defaultColor;

    ElevatorCard(int id, int floors, IntConsumer onCabinButton, Runnable onReset) {
        super(new BorderLayout(4, 4));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Elevador " + id),
                BorderFactory.createEmptyBorder(2, 4, 4, 4)));

        floorLabel.setFont(floorLabel.getFont().deriveFont(Font.BOLD, 16f));
        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.add(floorLabel);
        info.add(directionLabel);
        info.add(statusLabel);
        info.add(hallLabel);

        JPanel buttons = new JPanel(new GridLayout(0, 5, 2, 2));
        cabinButtons = new JButton[floors + 1];
        for (int f = 1; f <= floors; f++) {
            int floor = f;
            JButton b = new JButton(String.valueOf(f));
            b.setMargin(new Insets(1, 1, 1, 1));
            b.setFocusPainted(false);
            b.setToolTipText("Presionar boton del piso " + f + " dentro del Elevador " + id);
            b.addActionListener(e -> onCabinButton.accept(floor));
            cabinButtons[f] = b;
            buttons.add(b);
        }
        defaultColor = cabinButtons[1].getBackground();
        resetButton.addActionListener(e -> onReset.run());

        JPanel south = new JPanel(new BorderLayout());
        south.add(resetButton, BorderLayout.EAST);

        add(info, BorderLayout.NORTH);
        add(buttons, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    void update(ElevatorSnapshot s) {
        floorLabel.setText("Piso " + s.floor() + (s.inMotion() ? " → " + (s.floor() + s.direction().delta()) : ""));
        directionLabel.setText("Direccion: " + s.direction().arrow() + " " + s.direction().label());
        statusLabel.setText("Estado: " + s.status().label());
        statusLabel.setForeground(Theme.forStatus(s.status()));
        hallLabel.setText("Req. del manager: " + s.stops().describeHallCalls());
        for (int f = 1; f < cabinButtons.length; f++) {
            cabinButtons[f].setBackground(s.stops().hasCabin(f) ? Theme.LIT : defaultColor);
        }
    }

    void setControlsEnabled(boolean enabled) {
        for (int f = 1; f < cabinButtons.length; f++) {
            cabinButtons[f].setEnabled(enabled);
        }
        resetButton.setEnabled(enabled);
    }
}
