package yt.ren.slate.text;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public class ValueFormatter {
    public static String format(String format, Object... args) {
        StringBuilder out = new StringBuilder();
        int argument = 0;
        int i = 0;
        int n = format.length();
        while (i < n) {
            char c = format.charAt(i++);
            if (c != '%') {
                out.append(c);
                continue;
            }
            boolean left = false;
            boolean zero = false;
            boolean plus = false;
            boolean space = false;
            boolean alternate = false;
            for (; i < n; i++) {
                char f = format.charAt(i);
                if (f == '-') {
                    left = true;
                } else if (f == '0') {
                    zero = true;
                } else if (f == '+') {
                    plus = true;
                } else if (f == ' ') {
                    space = true;
                } else if (f == '#') {
                    alternate = true;
                } else {
                    break;
                }
            }
            int width = -1;
            if (i < n && format.charAt(i) == '*') {
                width = ((Number) args[argument++]).intValue();
                if (width < 0) {
                    left = true;
                    width = -width;
                }
                i++;
            } else {
                while (i < n && Character.isDigit(format.charAt(i))) {
                    width = (Math.max(width, 0)) * 10 + (format.charAt(i++) - '0');
                }
            }
            int precision = -1;
            if (i < n && format.charAt(i) == '.') {
                i++;
                precision = 0;
                if (i < n && format.charAt(i) == '*') {
                    precision = ((Number) args[argument++]).intValue();
                    i++;
                } else {
                    while (i < n && Character.isDigit(format.charAt(i))) {
                        precision = precision * 10 + (format.charAt(i++) - '0');
                    }
                }
            }
            String length = "";
            if (format.startsWith("ll", i)) {
                length = "ll";
                i += 2;
            } else if (format.startsWith("I64", i)) {
                length = "ll";
                i += 3;
            } else if (i < n && "hlzj".indexOf(format.charAt(i)) >= 0) {
                length = String.valueOf(format.charAt(i++));
            }
            char conversion = format.charAt(i++);
            String body;
            String sign = "";
            switch (conversion) {
                case '%':
                    out.append('%');
                    continue;
                case 'c':
                    body = String.valueOf((char) (((Number) args[argument++]).intValue() & 0xFF));
                    zero = false;
                    break;
                case 's': {
                    Object value = args[argument++];
                    String text = value == null ? "(null)" : value.toString();
                    body = precision >= 0 && precision < text.length() ? text.substring(0, precision) : text;
                    zero = false;
                    break;
                }
                case 'd':
                case 'i': {
                    long value = integer(args[argument++], length, true);
                    if (value < 0) {
                        sign = "-";
                    } else if (plus) {
                        sign = "+";
                    } else if (space) {
                        sign = " ";
                    }
                    body = Long.toUnsignedString(value < 0 ? -value : value);
                    body = integerPrecision(body, precision);
                    if (precision >= 0) {
                        zero = false;
                    }
                    break;
                }
                case 'u': {
                    long value = integer(args[argument++], length, false);
                    body = integerPrecision(Long.toUnsignedString(value), precision);
                    if (precision >= 0) {
                        zero = false;
                    }
                    break;
                }
                case 'x':
                case 'X': {
                    long value = integer(args[argument++], length, false);
                    body = Long.toHexString(value);
                    if (conversion == 'X') {
                        body = body.toUpperCase();
                    }
                    body = integerPrecision(body, precision);
                    if (alternate && value != 0) {
                        sign = conversion == 'X' ? "0X" : "0x";
                    }
                    if (precision >= 0) {
                        zero = false;
                    }
                    break;
                }
                case 'f':
                case 'F':
                case 'g':
                case 'G': {
                    double value = ((Number) args[argument++]).doubleValue();
                    boolean negative = (Double.doubleToRawLongBits(value) & 0x8000000000000000L) != 0;
                    if (negative) {
                        sign = "-";
                    } else if (plus) {
                        sign = "+";
                    } else if (space) {
                        sign = " ";
                    }
                    double magnitude = Math.abs(value);
                    if (Double.isNaN(value)) {
                        long bits = Double.doubleToRawLongBits(value);
                        boolean quiet = (bits & 0x0008000000000000L) != 0;
                        if (!quiet) {
                            body = "nan(snan)";
                        } else if (bits == 0xfff8000000000000L) {
                            body = "nan(ind)";
                        } else {
                            body = "nan";
                        }
                        zero = false;
                    } else if (Double.isInfinite(value)) {
                        body = "inf";
                        zero = false;
                    } else if (conversion == 'f' || conversion == 'F') {
                        body = fixed(magnitude, precision < 0 ? 6 : precision, alternate);
                    } else {
                        body = general(magnitude, precision < 0 ? 6 : precision == 0 ? 1 : precision, alternate);
                    }
                    if (conversion == 'F' || conversion == 'G') {
                        body = body.toUpperCase();
                    }
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unsupported conversion %" + conversion);
            }
            int total = sign.length() + body.length();
            if (width > total) {
                int pad = width - total;
                if (left) {
                    out.append(sign).append(body);
                    repeat(out, ' ', pad);
                } else if (zero) {
                    out.append(sign);
                    repeat(out, '0', pad);
                    out.append(body);
                } else {
                    repeat(out, ' ', pad);
                    out.append(sign).append(body);
                }
            } else {
                out.append(sign).append(body);
            }
        }
        return out.toString();
    }

    private static long integer(Object value, String length, boolean signed) {
        long raw = ((Number) value).longValue();
        if (length.equals("ll") || length.equals("z") || length.equals("j")) {
            return raw;
        }
        if (length.equals("h")) {
            return signed ? (short) raw : raw & 0xFFFFL;
        }
        return signed ? (int) raw : raw & 0xFFFFFFFFL;
    }

    private static String integerPrecision(String digits, int precision) {
        if (precision == 0 && digits.equals("0")) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = digits.length(); i < precision; i++) {
            out.append('0');
        }
        return out.append(digits).toString();
    }

    private static void repeat(StringBuilder out, char c, int count) {
        for (int i = 0; i < count; i++) {
            out.append(c);
        }
    }

    public static String fixed(double magnitude, int precision, boolean alternate) {
        String text = new BigDecimal(magnitude).setScale(precision, RoundingMode.HALF_EVEN).toPlainString();
        if (precision == 0 && alternate) {
            text += ".";
        }
        return text;
    }

    public static String general(double magnitude, int precision, boolean alternate) {
        if (magnitude == 0.0) {
            if (alternate) {
                StringBuilder zeros = new StringBuilder("0.");
                repeat(zeros, '0', precision - 1);
                return zeros.toString();
            }
            return "0";
        }
        BigDecimal rounded = new BigDecimal(magnitude).round(new MathContext(precision, RoundingMode.HALF_EVEN));
        int exponent = rounded.precision() - rounded.scale() - 1;
        String text;
        if (exponent < -4 || exponent >= precision) {
            BigDecimal mantissa = rounded.movePointLeft(exponent);
            String digits = mantissa.setScale(precision - 1, RoundingMode.HALF_EVEN).toPlainString();
            if (!alternate) {
                digits = stripZeros(digits);
            }
            String exp = Integer.toString(Math.abs(exponent));
            if (exp.length() < 2) {
                exp = "0" + exp;
            }
            text = digits + "e" + (exponent < 0 ? "-" : "+") + exp;
        } else {
            text = rounded.setScale(Math.max(precision - 1 - exponent, 0), RoundingMode.HALF_EVEN).toPlainString();
            if (!alternate) {
                text = stripZeros(text);
            }
        }
        return text;
    }

    private static String stripZeros(String text) {
        if (text.indexOf('.') < 0) {
            return text;
        }
        int end = text.length();
        while (text.charAt(end - 1) == '0') {
            end--;
        }
        if (text.charAt(end - 1) == '.') {
            end--;
        }
        return text.substring(0, end);
    }
}
