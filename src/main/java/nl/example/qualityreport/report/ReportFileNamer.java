package nl.example.qualityreport.report;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ReportFileNamer {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private ReportFileNamer() {}

    public static String filename(List<String> tickets, LocalDate date) {
        String ticket = primaryTicket(tickets);
        return "quality-report-" + sanitize(ticket) + "-" + date.format(DATE_FMT) + ".md";
    }

    static String primaryTicket(List<String> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return "UNKNOWN";
        }
        return tickets.getFirst().strip();
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }
}
