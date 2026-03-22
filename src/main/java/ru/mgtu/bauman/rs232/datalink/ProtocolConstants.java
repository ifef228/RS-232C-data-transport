package ru.mgtu.bauman.rs232.datalink;

/**
 * Типы кадров (согласовано с методичкой: I, Link, Uplink, ACK, Ret) + служебные для передачи файла.
 */
public final class ProtocolConstants {

    public static final int ADDR_BROADCAST = 0x7F;

    public static final int FT_LINK = 0x01;
    public static final int FT_UPLINK = 0x02;
    public static final int FT_I = 0x03;
    public static final int FT_ACK = 0x04;
    public static final int FT_RET = 0x05;
    /** Метаданные файла: имя + размер */
    public static final int FT_META = 0x06;
    /** Конец передачи файла */
    public static final int FT_END = 0x07;
    /** Текстовое уведомление удалённому пользователю */
    public static final int FT_NOTIFY = 0x08;

    private ProtocolConstants() {
    }
}
