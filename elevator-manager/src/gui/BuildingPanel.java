package gui;

import elevator.ElevatorSnapshot;
import elevator.ElevatorStatus;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;
import model.Direction;

/**
 * Simulacion GRAFICA del edificio (punto extra). Se dibuja a mano con
 * Graphics2D sobreescribiendo paintComponent (otra vez herencia: JPanel).
 *
 * Cada elevador es una columna (cubo); la cabina se dibuja en una posicion
 * "continua" (ej. 3.4) usando el progreso del viaje, por eso se ve moverse
 * suavemente entre pisos. Cuando abre puertas se dibujan las puertas
 * abriendose y cerrandose.
 */
class BuildingPanel extends JPanel {

    private static final int LEFT = 70;
    private static final int TOP = 28;
    private static final int BOTTOM = 10;

    private final int floors;
    private List<ElevatorSnapshot> snapshots = new ArrayList<>();
    private boolean[] upCalls;
    private boolean[] downCalls;

    BuildingPanel(int floors) {
        this.floors = floors;
        this.upCalls = new boolean[floors + 1];
        this.downCalls = new boolean[floors + 1];
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(420, 520));
    }

    void update(List<ElevatorSnapshot> snapshots, boolean[] upCalls, boolean[] downCalls) {
        this.snapshots = snapshots;
        this.upCalls = upCalls;
        this.downCalls = downCalls;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double floorH = (getHeight() - TOP - BOTTOM) / (double) floors;
        int n = Math.max(1, snapshots.size());
        int gap = 12;
        int shaftW = (int) Math.min(90, (getWidth() - LEFT - 10 - gap * n) / (double) n);

        // ---- pisos, etiquetas y llamadas de piso ----
        g.setFont(getFont().deriveFont(Font.PLAIN, (float) Math.min(13, Math.max(9, floorH * 0.45))));
        for (int f = 1; f <= floors; f++) {
            int y = (int) yOf(f, floorH);
            g.setColor(Theme.FLOOR_LINE);
            g.drawLine(LEFT - 6, y + (int) floorH, getWidth() - 4, y + (int) floorH);
            g.setColor(Color.DARK_GRAY);
            g.drawString("P" + f, 6, (int) (y + floorH / 2 + 4));
            drawArrow(g, 40, y + floorH * 0.30, Direction.UP, upCalls[f], floorH);
            drawArrow(g, 40, y + floorH * 0.70, Direction.DOWN, downCalls[f], floorH);
        }

        // ---- cubos y cabinas ----
        for (int i = 0; i < snapshots.size(); i++) {
            ElevatorSnapshot s = snapshots.get(i);
            int x = LEFT + i * (shaftW + gap);
            g.setColor(Theme.SHAFT);
            g.fillRect(x, TOP, shaftW, (int) (floorH * floors));
            g.setColor(Color.DARK_GRAY);
            g.drawString("E" + s.id(), x + shaftW / 2 - 8, TOP - 8);

            // botones internos encendidos: puntitos amarillos en su piso
            for (int f = 1; f <= floors; f++) {
                if (s.stops().hasCabin(f)) {
                    g.setColor(Theme.LIT);
                    g.fillOval(x + shaftW - 10, (int) (yOf(f, floorH) + floorH / 2 - 3), 7, 7);
                }
            }

            double pos = s.visualPosition();
            int cy = (int) (TOP + (floors - pos) * floorH) + 2;
            int ch = Math.max(6, (int) floorH - 4);
            int cx = x + 4;
            int cw = shaftW - 16;
            Color c = Theme.forStatus(s.status());
            g.setColor(c);
            g.fillRoundRect(cx, cy, cw, ch, 6, 6);

            if (s.status() == ElevatorStatus.DOORS_OPEN) {
                // puertas: se abren y cierran siguiendo una curva seno
                double open = Math.sin(Math.PI * s.progress());
                int gapW = (int) (cw * 0.8 * open);
                g.setColor(new Color(255, 250, 220));
                g.fillRect(cx + (cw - gapW) / 2, cy + 2, gapW, ch - 4);
            }
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(1f));
            String label = s.direction().arrow();
            g.drawString(label, cx + 3, cy + Math.min(ch - 2, 14));
        }
    }

    private double yOf(int floor, double floorH) {
        return TOP + (floors - floor) * floorH;
    }

    private void drawArrow(Graphics2D g, int x, double cy, Direction d, boolean lit, double floorH) {
        int s = (int) Math.max(3, Math.min(6, floorH * 0.18));
        int[] xs = {x - s, x + s, x};
        int[] ys = d == Direction.UP
                ? new int[]{(int) cy + s, (int) cy + s, (int) cy - s}
                : new int[]{(int) cy - s, (int) cy - s, (int) cy + s};
        g.setColor(lit ? Theme.LIT : new Color(215, 218, 224));
        g.fillPolygon(xs, ys, 3);
    }
}
