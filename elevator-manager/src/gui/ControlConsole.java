package gui;

import core.PauseControl;
import core.SimulationConfig;
import elevator.ElevatorSnapshot;
import io.RequestFileLoader;
import io.RequestFilePlayer;
import io.ScheduledCommand;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.text.DefaultCaret;
import logging.LogFactory;
import manager.ElevatorManager;
import model.Direction;
import model.Request;

/**
 * CONSOLA DE COMANDOS (ventana principal).
 *
 *  +--------------------------------------------------------------------+
 *  | [Iniciar] [Detener] [Pausar] [Reset sistema] [Cargar archivo] ...   |
 *  +-------------+------------------------------+-----------------------+
 *  | Botones de  |  Simulacion grafica del      |  Tarjeta por elevador |
 *  | piso (^ v)  |  edificio                    |  (piso, botones, req) |
 *  +-------------+------------------------------+-----------------------+
 *  | Logs en vivo                                                       |
 *  | > comando de texto ______________________ [Enviar]                 |
 *  +--------------------------------------------------------------------+
 *
 * IMPORTANTE (hilos y Swing): Swing NO es thread-safe; solo el hilo de
 * eventos (EDT) puede tocar componentes. Por eso:
 *  - La vista se refresca con un javax.swing.Timer (corre en el EDT) que
 *    cada 50 ms pide snapshots a los elevadores.
 *  - Los logs que llegan desde otros hilos se pasan al EDT con invokeLater.
 */
public class ControlConsole extends JFrame {

    private static final int MAX_LOG_CHARS = 200_000;

    private SimulationConfig config;
    private ElevatorManager manager;          // null = simulacion detenida
    private PauseControl pauseControl;
    private RequestFilePlayer filePlayer;

    private final JButton startButton = new JButton("▶ Iniciar");
    private final JButton stopButton = new JButton("■ Detener");
    private final JButton pauseButton = new JButton("⏸ Pausar");
    private final JButton resetButton = new JButton("↺ Reset sistema");
    private final JButton uploadButton = new JButton("Cargar archivo...");
    private final JButton configButton = new JButton("Configuracion...");
    private final JLabel statusLabel = new JLabel();

    private final JTextArea logArea = new JTextArea();
    private final JTextField commandField = new JTextField();
    private final JPanel centerHolder = new JPanel(new BorderLayout());

    private FloorCallPanel floorPanel;
    private BuildingPanel buildingPanel;
    private final List<ElevatorCard> cards = new ArrayList<>();

    private final Timer refreshTimer = new Timer(50, e -> refreshView());

    public ControlConsole(SimulationConfig config) {
        super("Elevator Manager - Consola de comandos");
        this.config = config;
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        add(buildToolbar(), BorderLayout.NORTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, centerHolder, buildLogPanel());
        split.setResizeWeight(0.72);
        add(split, BorderLayout.CENTER);

        // Los logs de todos los hilos llegan aqui y se pasan al EDT.
        LogFactory.guiHandler().setListener(line -> SwingUtilities.invokeLater(() -> appendLog(line)));

        rebuildSimulationViews();
        updateControls();
        setSize(new Dimension(1280, 860));
        setLocationRelativeTo(null);
        appendLog("Bienvenido. Configuracion: " + config + "\nPresione 'Iniciar' para comenzar. Escriba HELP para ver los comandos.\n");
    }

    // =====================================================================
    //  Construccion de la interfaz
    // =====================================================================

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        startButton.addActionListener(e -> startSimulation());
        stopButton.addActionListener(e -> stopSimulation());
        pauseButton.addActionListener(e -> togglePause());
        resetButton.addActionListener(e -> manager.resetSystem());
        uploadButton.addActionListener(e -> uploadFile());
        configButton.addActionListener(e -> changeConfig());
        for (JButton b : new JButton[]{startButton, stopButton, pauseButton, resetButton, uploadButton, configButton}) {
            b.setFocusPainted(false);
            bar.add(b);
        }
        statusLabel.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        bar.add(statusLabel);
        return bar;
    }

    private JPanel buildLogPanel() {
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ((DefaultCaret) logArea.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE); // auto-scroll

        commandField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        commandField.setToolTipText("Ej: CALL 5 DOWN | GOTO 2 8 | RESET | RESET 1 | PAUSE | RESUME | STATUS | HELP");
        commandField.addActionListener(e -> sendTypedCommand());
        JButton send = new JButton("Enviar");
        send.addActionListener(e -> sendTypedCommand());

        JPanel input = new JPanel(new BorderLayout(6, 0));
        input.add(new JLabel(" Comando >"), BorderLayout.WEST);
        input.add(commandField, BorderLayout.CENTER);
        input.add(send, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder("Logs (tambien en logs/*.log y en la linea de comandos)"));
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        panel.add(input, BorderLayout.SOUTH);
        return panel;
    }

    /** (Re)crea los paneles que dependen de la cantidad de pisos/elevadores. */
    private void rebuildSimulationViews() {
        centerHolder.removeAll();
        cards.clear();

        floorPanel = new FloorCallPanel(config.floorCount(), this::onHallCall);
        floorPanel.setPreferredSize(new Dimension(170, 100));
        buildingPanel = new BuildingPanel(config.floorCount());

        JPanel cardsPanel = new JPanel(new GridLayout(0, config.elevatorCount() > 4 ? 2 : 1, 6, 6));
        for (int i = 1; i <= config.elevatorCount(); i++) {
            int id = i;
            ElevatorCard card = new ElevatorCard(id, config.floorCount(),
                    floor -> manager.submitCabinRequest(id, floor),
                    () -> manager.resetElevator(id));
            cards.add(card);
            cardsPanel.add(card);
        }
        JPanel cardsWrapper = new JPanel(new BorderLayout());
        cardsWrapper.add(cardsPanel, BorderLayout.NORTH);
        JScrollPane cardsScroll = new JScrollPane(cardsWrapper);
        cardsScroll.setPreferredSize(new Dimension(config.elevatorCount() > 4 ? 560 : 300, 100));
        cardsScroll.getVerticalScrollBar().setUnitIncrement(16);

        centerHolder.add(floorPanel, BorderLayout.WEST);
        centerHolder.add(buildingPanel, BorderLayout.CENTER);
        centerHolder.add(cardsScroll, BorderLayout.EAST);
        centerHolder.revalidate();
        centerHolder.repaint();
        refreshView();
    }

    // =====================================================================
    //  Acciones de los botones
    // =====================================================================

    private void startSimulation() {
        pauseControl = new PauseControl();
        manager = new ElevatorManager(config, pauseControl);
        manager.startSimulation();
        refreshTimer.start();
        updateControls();
    }

    private void stopSimulation() {
        if (filePlayer != null) {
            filePlayer.shutdown();
            filePlayer = null;
        }
        refreshTimer.stop();
        manager.stopSimulation();
        manager = null;
        refreshView();
        updateControls();
    }

    private void togglePause() {
        if (manager.isPaused()) {
            manager.resumeSimulation();
        } else {
            manager.pauseSimulation();
        }
        updateControls();
    }

    private void changeConfig() {
        SimulationConfig c = ConfigDialog.ask(this, config);
        if (c != null) {
            config = c;
            appendLog("Nueva configuracion: " + config + "\n");
            rebuildSimulationViews();
        }
    }

    private void onHallCall(int floor, Direction dir) {
        manager.submitHallCall(floor, dir);
    }

    /** "Upload" de una lista de requerimientos (solo en estado de reset). */
    private void uploadFile() {
        if (!manager.isAtResetState()) {
            JOptionPane.showMessageDialog(this,
                    "Solo se puede cargar un archivo cuando el sistema esta en estado de reset:\n"
                            + "al inicio de la simulacion o despues de un 'Reset sistema',\n"
                            + "con todos los elevadores en el piso 1 y sin requerimientos pendientes.",
                    "Cargar archivo", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (filePlayer != null && filePlayer.isRunning()) {
            JOptionPane.showMessageDialog(this, "Ya se esta ejecutando un archivo.");
            return;
        }
        JFileChooser chooser = new JFileChooser(new File("."));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        try {
            List<ScheduledCommand> script = RequestFileLoader.load(file.toPath(), config);
            filePlayer = new RequestFilePlayer(file.getName(), script, manager, pauseControl);
            filePlayer.start();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "No se pudo cargar el archivo:\n" + ex.getMessage(),
                    "Error en archivo", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendTypedCommand() {
        String text = commandField.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        commandField.setText("");
        appendLog("> " + text + "\n");
        if (manager == null) {
            appendLog("La simulacion no esta iniciada. Presione 'Iniciar'.\n");
            return;
        }
        appendLog(manager.executeConsoleCommand(text) + "\n");
        updateControls();
    }

    // =====================================================================
    //  Refresco de la vista (EDT)
    // =====================================================================

    private void refreshView() {
        List<ElevatorSnapshot> snaps = new ArrayList<>();
        boolean[] up = new boolean[config.floorCount() + 1];
        boolean[] down = new boolean[config.floorCount() + 1];
        if (manager != null) {
            snaps = manager.snapshots();
            for (ElevatorSnapshot s : snaps) {
                for (int f = 1; f <= config.floorCount(); f++) {
                    up[f] |= s.stops().hasHall(f, Direction.UP);
                    down[f] |= s.stops().hasHall(f, Direction.DOWN);
                }
            }
            for (Request r : manager.queuedRequests()) {
                if (r.type() == Request.Type.HALL) {
                    (r.direction() == Direction.UP ? up : down)[r.floor()] = true;
                }
            }
        } else {
            for (int i = 1; i <= config.elevatorCount(); i++) {
                snaps.add(ElevatorSnapshot.initial(i, config.floorCount()));
            }
        }
        buildingPanel.update(snaps, up, down);
        floorPanel.update(up, down);
        for (int i = 0; i < cards.size() && i < snaps.size(); i++) {
            cards.get(i).update(snaps.get(i));
        }
    }

    private void updateControls() {
        boolean running = manager != null;
        startButton.setEnabled(!running);
        configButton.setEnabled(!running);
        stopButton.setEnabled(running);
        pauseButton.setEnabled(running);
        resetButton.setEnabled(running);
        uploadButton.setEnabled(running);
        floorPanel.setControlsEnabled(running);
        for (ElevatorCard c : cards) {
            c.setControlsEnabled(running);
        }
        boolean paused = running && manager.isPaused();
        pauseButton.setText(paused ? "▶ Reanudar" : "⏸ Pausar");
        statusLabel.setText(!running ? "Estado: DETENIDA" : paused ? "Estado: EN PAUSA" : "Estado: EN EJECUCION");
    }

    private void appendLog(String text) {
        logArea.append(text);
        int len = logArea.getDocument().getLength();
        if (len > MAX_LOG_CHARS) { // evita que el JTextArea crezca sin limite
            logArea.replaceRange("", 0, len - MAX_LOG_CHARS / 2);
        }
    }
}
