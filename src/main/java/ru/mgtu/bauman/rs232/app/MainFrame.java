package ru.mgtu.bauman.rs232.app;

import com.fazecast.jSerialComm.SerialPort;
import ru.mgtu.bauman.rs232.datalink.DataLinkLayer;
import ru.mgtu.bauman.rs232.physical.SerialPhysicalLayer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Пользовательский уровень: меню, выбор режима, COM-порта, передача/приём файла.
 */
public class MainFrame extends JFrame {

    private final JComboBox<String> portCombo = new JComboBox<>();
    private final JComboBox<Integer> baudCombo = new JComboBox<>(new Integer[]{9600, 19200, 38400, 57600, 115200});
    private final JComboBox<Integer> dataBitsCombo = new JComboBox<>(new Integer[]{8, 7});
    private final JComboBox<String> stopBitsCombo = new JComboBox<>(new String[]{"1", "2"});
    private final JComboBox<String> parityCombo = new JComboBox<>(new String[]{"NONE", "ODD", "EVEN", "MARK", "SPACE"});
    private final JSpinner localAddrSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 126, 1));
    private final JSpinner remoteAddrSpinner = new JSpinner(new SpinnerNumberModel(2, 1, 126, 1));

    private final JRadioButton modeSender = new JRadioButton("Передатчик (источник файла)", true);
    private final JRadioButton modeReceiver = new JRadioButton("Приёмник (каталог для файла)");

    private final JTextField fileField = new JTextField(40);
    private final JTextField dirField = new JTextField(40);
    private final JButton chooseFileBtn = new JButton("Файл…");
    private final JButton chooseDirBtn = new JButton("Каталог…");

    private final JButton connectBtn = new JButton("Подключить порт");
    private final JButton disconnectBtn = new JButton("Отключить");
    private final JButton actionBtn = new JButton("Передать файл / Начать приём");

    private final JTextArea logArea = new JTextArea(16, 60);

    private SerialPhysicalLayer physical;
    private DataLinkLayer dataLink;

    public MainFrame() {
        super("Пересылка текстовых файлов по RS-232 (вариант 38)");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setContentPane(buildContent());
        pack();
        setLocationRelativeTo(null);

        refreshPorts();

        chooseFileBtn.addActionListener(e -> {
            JFileChooser ch = new JFileChooser();
            if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                fileField.setText(ch.getSelectedFile().getAbsolutePath());
            }
        });
        chooseDirBtn.addActionListener(e -> {
            JFileChooser ch = new JFileChooser();
            ch.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                dirField.setText(ch.getSelectedFile().getAbsolutePath());
            }
        });

        ButtonGroup bg = new ButtonGroup();
        bg.add(modeSender);
        bg.add(modeReceiver);
        modeSender.addActionListener(e -> updateModeUi());
        modeReceiver.addActionListener(e -> updateModeUi());

        connectBtn.addActionListener(e -> connect());
        disconnectBtn.addActionListener(e -> disconnect());
        actionBtn.addActionListener(e -> runAction());

        disconnectBtn.setEnabled(false);
        actionBtn.setEnabled(false);
        updateModeUi();

        JMenuBar bar = new JMenuBar();
        JMenu m = new JMenu("Порт");
        JMenuItem refresh = new JMenuItem("Обновить список COM");
        refresh.addActionListener(e -> refreshPorts());
        m.add(refresh);
        bar.add(m);
        setJMenuBar(bar);
    }

    private void updateModeUi() {
        boolean s = modeSender.isSelected();
        fileField.setEnabled(s);
        chooseFileBtn.setEnabled(s);
        dirField.setEnabled(!s);
        chooseDirBtn.setEnabled(!s);
        actionBtn.setText(s ? "Передать файл" : "Начать приём (ожидание)");
    }

    private JPanel buildContent() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        top.add(new JLabel("COM-порт:"), c);
        c.gridx = 1;
        top.add(portCombo, c);
        c.gridy++;
        c.gridx = 0;
        top.add(new JLabel("Скорость:"), c);
        c.gridx = 1;
        baudCombo.setSelectedItem(115200);
        top.add(baudCombo, c);
        c.gridy++;
        c.gridx = 0;
        top.add(new JLabel("Биты данных:"), c);
        c.gridx = 1;
        top.add(dataBitsCombo, c);
        c.gridy++;
        c.gridx = 0;
        top.add(new JLabel("Стоп-биты:"), c);
        c.gridx = 1;
        top.add(stopBitsCombo, c);
        c.gridy++;
        c.gridx = 0;
        top.add(new JLabel("Чётность:"), c);
        c.gridx = 1;
        top.add(parityCombo, c);
        c.gridy++;
        c.gridx = 0;
        top.add(new JLabel("Адрес локальный / удалённый:"), c);
        c.gridx = 1;
        JPanel adr = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        adr.add(localAddrSpinner);
        adr.add(remoteAddrSpinner);
        top.add(adr, c);

        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 2;
        JPanel modes = new JPanel(new GridLayout(2, 1));
        modes.add(modeSender);
        modes.add(modeReceiver);
        top.add(modes, c);

        c.gridy++;
        c.gridwidth = 1;
        c.gridx = 0;
        top.add(new JLabel("Файл (источник):"), c);
        c.gridx = 1;
        JPanel fp = new JPanel(new BorderLayout(4, 0));
        fp.add(fileField, BorderLayout.CENTER);
        fp.add(chooseFileBtn, BorderLayout.EAST);
        top.add(fp, c);

        c.gridy++;
        c.gridx = 0;
        top.add(new JLabel("Каталог (приёмник):"), c);
        c.gridx = 1;
        JPanel dp = new JPanel(new BorderLayout(4, 0));
        dp.add(dirField, BorderLayout.CENTER);
        dp.add(chooseDirBtn, BorderLayout.EAST);
        top.add(dp, c);

        c.gridy++;
        c.gridx = 0;
        c.gridwidth = 2;
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btns.add(connectBtn);
        btns.add(disconnectBtn);
        btns.add(actionBtn);
        top.add(btns, c);

        root.add(top, BorderLayout.NORTH);

        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        root.add(new JScrollPane(logArea), BorderLayout.CENTER);

        return root;
    }

    private void refreshPorts() {
        portCombo.removeAllItems();
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort p : ports) {
            portCombo.addItem(p.getSystemPortName());
        }
        portCombo.setEditable(true);
        log("Найдено портов: " + portCombo.getItemCount());
    }

    private void log(String s) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(s + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void connect() {
        try {
            String name = (String) portCombo.getSelectedItem();
            if (name == null || name.isBlank()) {
                JOptionPane.showMessageDialog(this, "Укажите имя COM-порта", "Ошибка", JOptionPane.ERROR_MESSAGE);
                return;
            }
            int baud = (Integer) baudCombo.getSelectedItem();
            int dataBits = (Integer) dataBitsCombo.getSelectedItem();
            int stop = stopBitsCombo.getSelectedIndex();
            int parity = parityCombo.getSelectedIndex();

            physical = new SerialPhysicalLayer();
            physical.open(name.trim(), baud, dataBits, SerialPhysicalLayer.stopBitsFromUi(stop), SerialPhysicalLayer.parityFromUi(parity));

            dataLink = new DataLinkLayer(physical);
            dataLink.setAddresses((Integer) localAddrSpinner.getValue(), (Integer) remoteAddrSpinner.getValue());
            dataLink.start();

            connectBtn.setEnabled(false);
            disconnectBtn.setEnabled(true);
            actionBtn.setEnabled(true);
            log("Порт открыт: " + name + " " + baud + " " + dataBits + "N" + (stop == 0 ? "1" : "2");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка открытия порта", JOptionPane.ERROR_MESSAGE);
            disconnect();
        }
    }

    private void disconnect() {
        actionBtn.setEnabled(false);
        if (dataLink != null) {
            dataLink.close();
            dataLink = null;
        }
        if (physical != null) {
            physical.close();
            physical = null;
        }
        connectBtn.setEnabled(true);
        disconnectBtn.setEnabled(false);
        log("Порт закрыт.");
    }

    private void runAction() {
        if (dataLink == null || !physical.isOpen()) {
            JOptionPane.showMessageDialog(this, "Сначала подключите порт.", "Нет соединения", JOptionPane.WARNING_MESSAGE);
            return;
        }
        actionBtn.setEnabled(false);
        Thread t = new Thread(() -> {
            try {
                if (modeSender.isSelected()) {
                    String p = fileField.getText().trim();
                    if (p.isEmpty()) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Укажите файл.", "Нет файла", JOptionPane.WARNING_MESSAGE));
                        return;
                    }
                    Path path = Paths.get(p);
                    FileTransferService.sendTextFile(path, dataLink, this::log);
                } else {
                    String d = dirField.getText().trim();
                    if (d.isEmpty()) {
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Укажите каталог для сохранения.", "Нет каталога", JOptionPane.WARNING_MESSAGE));
                        return;
                    }
                    Path out = Paths.get(d);
                    FileTransferService.receiveLoop(dataLink, out, this::log, msg ->
                            SwingUtilities.invokeLater(() ->
                                    JOptionPane.showMessageDialog(this, "Удалённый абонент: " + msg, "Сообщение", JOptionPane.INFORMATION_MESSAGE)));
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    log("Ошибка: " + ex.getMessage());
                    JOptionPane.showMessageDialog(this, ex.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                SwingUtilities.invokeLater(() -> actionBtn.setEnabled(true));
            }
        }, "transfer");
        t.setDaemon(true);
        t.start();
    }
}
