package yt.ren.slate.text;

import java.io.ByteArrayOutputStream;

public class ModifiedUtf8 {
    public static byte[] encode(String text) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(text.length());
        for (int i = 0; i < text.length(); ++i) {
            char c = text.charAt(i);
            if (c != 0 && c < 0x80) {
                out.write(c);
            } else if (c < 0x800) {
                out.write(0xc0 | (c >> 6));
                out.write(0x80 | (c & 0x3f));
            } else {
                out.write(0xe0 | (c >> 12));
                out.write(0x80 | ((c >> 6) & 0x3f));
                out.write(0x80 | (c & 0x3f));
            }
        }
        return out.toByteArray();
    }

    public static String decode(byte[] data, int length) {
        int end = 0;
        while (end < length && data[end] != 0) {
            ++end;
        }
        StringBuilder out = new StringBuilder(end);
        int i = 0;
        while (i < end) {
            int ch = data[i] & 0xff;
            int ch2 = i + 1 < end ? data[i + 1] & 0xff : 0;
            int ch3 = i + 2 < end ? data[i + 2] & 0xff : 0;
            switch (ch >> 4) {
                case 0xc:
                case 0xd:
                    if ((ch2 & 0xc0) == 0x80) {
                        out.append((char) (((ch & 0x1f) << 6) | (ch2 & 0x3f)));
                        i += 2;
                        continue;
                    }
                    break;
                case 0xe:
                    if ((ch2 & 0xc0) == 0x80 && (ch3 & 0xc0) == 0x80) {
                        out.append((char) (((ch & 0x0f) << 12) | ((ch2 & 0x3f) << 6) | (ch3 & 0x3f)));
                        i += 3;
                        continue;
                    }
                    break;
                case 0x8:
                case 0x9:
                case 0xa:
                case 0xb:
                case 0xf:
                    break;
                default:
                    out.append((char) ch);
                    ++i;
                    continue;
            }
            out.append((char) ch);
            ++i;
        }
        return out.toString();
    }
}
