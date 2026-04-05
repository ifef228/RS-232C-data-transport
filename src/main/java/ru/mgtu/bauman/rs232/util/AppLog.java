package ru.mgtu.bauman.rs232.util;

/**
 * Дублирует сообщения в stdout (терминал), чтобы при запуске из консоли были видны логи.
 * Swing-лог в окне остаётся основным для пользователя.
 */
public final class AppLog {

    private static volatile boolean consoleEnabled = true;

    private AppLog() {
    }

    public static void setConsoleEnabled(boolean enabled) {
        consoleEnabled = enabled;
    }

    public static void line(String msg) {
        if (consoleEnabled) {
            System.out.println(msg);
            System.out.flush();
        }
    }

    public static void line(String prefix, String msg) {
        line(prefix + msg);
    }

    /** Первые байты в hex для отладки канала. */
    public static String hexPreview(byte[] data, int max) {
        if (data == null || data.length == 0) {
            return "";
        }
        int n = Math.min(max, data.length);
        StringBuilder sb = new StringBuilder(n * 3);
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", data[i] & 0xFF));
        }
        if (data.length > max) {
            sb.append(" …(+").append(data.length - max).append(" байт)");
        }
        return sb.toString();
    }
}
