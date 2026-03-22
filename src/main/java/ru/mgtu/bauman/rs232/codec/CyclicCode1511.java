package ru.mgtu.bauman.rs232.codec;

/**
 * Систематический циклический код (15, 11) над GF(2).
 * Порождающий многочлен степени 4: g(x) = x^4 + x + 1 (0b10011 = 0x13).
 * Информационное слово — 11 младших бит, в кодовое слово входит как старшие 11 бит
 * информационных позиций; 4 контрольных бита — остаток от деления m(x)·x^4 на g(x).
 * <p>
 * Обнаружение ошибок: синдром (остаток от деления принятого многочлена на g(x)) должен быть 0.
 */
public final class CyclicCode1511 {

    /** g(x) = x^4 + x + 1 */
    private static final int G = 0x13;

    private CyclicCode1511() {
    }

    /**
     * @param info11 11 бит информации (младшие биты)
     * @return 15-битное кодовое слово
     */
    public static int encode(int info11) {
        int m = info11 & 0x7FF;
        long reg = (long) m << 4;
        for (int i = 10; i >= 0; i--) {
            if ((reg & (1L << (i + 4))) != 0) {
                reg ^= (long) G << i;
            }
        }
        return (m << 4) | ((int) reg & 0xF);
    }

    /**
     * Синдром принятого 15-битного слова (остаток от деления на g(x)).
     *
     * @return 0, если ошибок не обнаружено (синдром нулевой)
     */
    public static int syndrome(int received15) {
        int r = received15 & 0x7FFF;
        for (int i = 14; i >= 4; i--) {
            if ((r & (1 << i)) != 0) {
                r ^= G << (i - 4);
            }
        }
        return r & 0xF;
    }

    /**
     * Извлечь 11 информационных бит из безошибочного кодового слова.
     */
    public static int dataBits(int codeword15) {
        return (codeword15 >> 4) & 0x7FF;
    }
}
