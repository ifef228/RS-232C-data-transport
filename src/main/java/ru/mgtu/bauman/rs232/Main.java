package ru.mgtu.bauman.rs232;

import ru.mgtu.bauman.rs232.app.MainFrame;

import javax.swing.*;

public final class Main {

    public static void main(String[] args) {
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
