package ru.mgtu.bauman.rs232;

import ru.mgtu.bauman.rs232.app.MainFrame;
import ru.mgtu.bauman.rs232.util.AppLog;

import javax.swing.*;

public final class Main {

    public static void main(String[] args) {
        AppLog.line("=== RS-232 File Transport: сообщения [GUI], [PHY], [канал] дублируются в этот терминал ===");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            MainFrame f = new MainFrame();
            f.setVisible(true);
        });
    }
}
