package nl.example.qualityreport.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReportTemplateValidatorTest {

    @Nested
    class ValidReport {

        @Test
        void acceptsWellFormedReport() {
            String report = minimalValidReport();
            List<String> violations = ReportTemplateValidator.validate(report);
            assertThat(violations).isEmpty();
        }

        @Test
        void requireValidDoesNotThrowForValidReport() {
            ReportTemplateValidator.requireValid(minimalValidReport());
        }
    }

    @Nested
    class MissingHeadings {

        @Test
        void detectsMissingHeading() {
            String report = minimalValidReport().replace("## DoD\n", "");
            List<String> violations = ReportTemplateValidator.validate(report);
            assertThat(violations).anyMatch(v -> v.contains("Missing") && v.contains("## DoD"));
        }

        @Test
        void detectsMultipleMissingHeadings() {
            String report =
                    minimalValidReport().replace("## Betrokken tickets\n", "").replace("## DoD\n", "");
            List<String> violations = ReportTemplateValidator.validate(report);
            assertThat(violations).hasSize(2);
        }
    }

    @Nested
    class DuplicateHeadings {

        @Test
        void detectsDuplicateHeading() {
            String report = minimalValidReport() + "\n## DoD\nDuplicate section.\n";
            List<String> violations = ReportTemplateValidator.validate(report);
            assertThat(violations).anyMatch(v -> v.contains("Duplicate") && v.contains("## DoD"));
        }
    }

    @Nested
    class OrderViolations {

        @Test
        void detectsOutOfOrderHeadings() {
            String report = minimalValidReport().replace("## Betrokken tickets\nTickets here\n", "")
                    + "## Betrokken tickets\nTickets at end\n";
            List<String> violations = ReportTemplateValidator.validate(report);
            assertThat(violations).anyMatch(v -> v.contains("out of order"));
        }
    }

    @Nested
    class RequireValid {

        @Test
        void throwsWithViolationDetails() {
            String report = minimalValidReport().replace("## DoD\n", "");
            assertThatThrownBy(() -> ReportTemplateValidator.requireValid(report))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Missing required heading")
                    .hasMessageContaining("## DoD");
        }
    }

    @Nested
    class HeadingLineStartDetection {

        @Test
        void headingInlineInTextDoesNotCountAsDuplicate() {
            String report = minimalValidReport().replace("Tickets here", "  Prefix text ## Betrokken tickets again");
            List<String> violations = ReportTemplateValidator.validate(report);
            assertThat(violations).isEmpty();
        }

        @Test
        void headingAtLineStartIsCounted() {
            String report = minimalValidReport() + "\n## DoD\nDuplicate.\n";
            List<String> violations = ReportTemplateValidator.validate(report);
            assertThat(violations).anyMatch(v -> v.contains("Duplicate") && v.contains("## DoD"));
        }
    }

    @Nested
    class RequiredHeadingsAreComplete {

        @Test
        void allExpectedHeadingsAreListed() {
            assertThat(ReportTemplateValidator.REQUIRED_HEADINGS)
                    .containsExactly(
                            "# Testrapport",
                            "## Betrokken tickets",
                            "## Wat is er gewijzigd en waarom?",
                            "### Wijzigingen per type",
                            "## Wat wordt geraakt?",
                            "## Acceptatiecriteria",
                            "## Risico-analyse",
                            "## Wat is getest?",
                            "### Handmatig",
                            "### Geautomatiseerd",
                            "## Uitlevering op productie",
                            "## DoD");
        }
    }

    private static String minimalValidReport() {
        return """
                # Testrapport 1.0.0 — PROJ-100

                **Fix version:** 1.0.0
                **Team:** Alpha
                **Datum:** 2026-03-04
                **Risicoscore:** LOW

                ---
                ## Betrokken tickets
                Tickets here
                ## Wat is er gewijzigd en waarom?
                Narrative here

                ### Wijzigingen per type
                - **BE-wijzigingen (1 commits, 2 bestanden, +10/-5)**
                ## Wat wordt geraakt?
                ☑ Database(s) wijzigingen
                ## Acceptatiecriteria
                Criteria here
                ## Risico-analyse
                **Risicoscore:** LOW

                **Grootste risico's:**
                Risk narrative

                **Agent deliberation:**
                | Agent | Vote |
                ## Wat is getest?
                ### Handmatig
                Manual test

                ### Geautomatiseerd
                - Resultaat: passed
                ## Uitlevering op productie
                Standaard uitlevering: Ja
                ## DoD
                ☑ DoD is gereed voor alle issues.
                """;
    }
}
