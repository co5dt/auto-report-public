package nl.example.qualityreport.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportFileNamerTest {

    @Test
    void standardFilename() {
        String name = ReportFileNamer.filename(List.of("PROJ-128530"), LocalDate.of(2026, 3, 3));
        assertThat(name).isEqualTo("quality-report-PROJ-128530-2026-03-03.md");
    }

    @Test
    void multipleTicketsUsesFirst() {
        String name = ReportFileNamer.filename(List.of("PROJ-111", "PROJ-222"), LocalDate.of(2026, 1, 15));
        assertThat(name).isEqualTo("quality-report-PROJ-111-2026-01-15.md");
    }

    @Test
    void emptyTicketsUsesUnknown() {
        String name = ReportFileNamer.filename(List.of(), LocalDate.of(2026, 6, 1));
        assertThat(name).isEqualTo("quality-report-UNKNOWN-2026-06-01.md");
    }

    @Test
    void nullTicketsUsesUnknown() {
        String name = ReportFileNamer.filename(null, LocalDate.of(2026, 6, 1));
        assertThat(name).isEqualTo("quality-report-UNKNOWN-2026-06-01.md");
    }

    @Test
    void specialCharsInTicketAreSanitized() {
        String name = ReportFileNamer.filename(List.of("PROJ/123 456"), LocalDate.of(2026, 1, 1));
        assertThat(name).isEqualTo("quality-report-PROJ_123_456-2026-01-01.md");
    }
}
