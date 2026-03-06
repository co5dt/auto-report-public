package nl.example.qualityreport.context;

public final class XmlUtils {

    private XmlUtils() {}

    public static String escapeXml(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        var sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String noData(String reason) {
        return "<no_data reason=\"" + escapeXml(reason) + "\"/>";
    }

    static String tag(String name, String content) {
        return "<%s>%s</%s>".formatted(name, content, name);
    }

    static String tagEscaped(String name, String content) {
        return "<%s>%s</%s>".formatted(name, escapeXml(content), name);
    }

    public static boolean isBlankOrNone(String value) {
        return value == null || value.isBlank() || "none".equalsIgnoreCase(value.trim());
    }
}
