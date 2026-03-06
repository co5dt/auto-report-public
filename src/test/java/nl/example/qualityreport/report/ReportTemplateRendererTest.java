package nl.example.qualityreport.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReportTemplateRendererTest {

    @Nested
    class PlaceholderExtraction {

        @Test
        void extractsSinglePlaceholder() {
            var renderer = new ReportTemplateRenderer("Hello {{name}}", true);
            Set<String> keys = renderer.extractPlaceholders("Hello {{name}}");
            assertThat(keys).containsExactly("name");
        }

        @Test
        void extractsMultiplePlaceholders() {
            var renderer = new ReportTemplateRenderer("{{a}} and {{b}} and {{a}}", true);
            Set<String> keys = renderer.extractPlaceholders("{{a}} and {{b}} and {{a}}");
            assertThat(keys).containsExactlyInAnyOrder("a", "b");
        }

        @Test
        void returnsEmptySetForNoPlaceholders() {
            var renderer = new ReportTemplateRenderer("no placeholders here", true);
            Set<String> keys = renderer.extractPlaceholders("no placeholders here");
            assertThat(keys).isEmpty();
        }
    }

    @Nested
    class Rendering {

        @Test
        void replacesAllPlaceholders() {
            var renderer = new ReportTemplateRenderer("# {{title}}\n{{body}}", true);
            String result = renderer.render(Map.of("title", "Report", "body", "Content here"));
            assertThat(result).isEqualTo("# Report\nContent here");
        }

        @Test
        void replacesDuplicatePlaceholderOccurrences() {
            var renderer = new ReportTemplateRenderer("{{x}} then {{x}}", true);
            String result = renderer.render(Map.of("x", "val"));
            assertThat(result).isEqualTo("val then val");
        }

        @Test
        void preservesNonPlaceholderContent() {
            var renderer = new ReportTemplateRenderer("## Header\n{{slot}}\n---", true);
            String result = renderer.render(Map.of("slot", "data"));
            assertThat(result).isEqualTo("## Header\ndata\n---");
        }
    }

    @Nested
    class StrictValidation {

        @Test
        void throwsOnUnresolvedPlaceholder() {
            var renderer = new ReportTemplateRenderer("{{a}} {{b}}", true);
            assertThatThrownBy(() -> renderer.render(Map.of("a", "val")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unresolved")
                    .hasMessageContaining("b");
        }

        @Test
        void throwsOnExtraneousSuppliedKey() {
            var renderer = new ReportTemplateRenderer("{{a}}", true);
            Map<String, String> slots = new LinkedHashMap<>();
            slots.put("a", "val");
            slots.put("z", "extra");
            assertThatThrownBy(() -> renderer.render(slots))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("do not match")
                    .hasMessageContaining("z");
        }

        @Test
        void succeedsWhenAllKeysMatch() {
            var renderer = new ReportTemplateRenderer("{{x}} {{y}}", true);
            String result = renderer.render(Map.of("x", "1", "y", "2"));
            assertThat(result).isEqualTo("1 2");
        }
    }

    @Nested
    class DefaultTemplateLoading {

        @Test
        void loadsDefaultTemplateFromClasspath() {
            var renderer = new ReportTemplateRenderer();
            Set<String> keys = renderer.extractPlaceholders(new ReportTemplateRenderer().render(fullSlotMap()));
            assertThat(keys).isEmpty();
        }

        @Test
        void throwsOnMissingResource() {
            assertThatThrownBy(() -> new ReportTemplateRenderer("/nonexistent-template.md"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not found");
        }
    }

    private static Map<String, String> fullSlotMap() {
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("fix_version", "1.0.0");
        slots.put("primary_ticket", "PROJ-100");
        slots.put("team", "Alpha");
        slots.put("date", "2026-03-04");
        slots.put("risk_score", "LOW");
        slots.put("tickets_body", "PROJ-100, PROJ-101");
        slots.put("change_narrative_body", "Changes were made.");
        slots.put("changes_per_type", "- **BE-wijzigingen (1 commits, 2 bestanden, +10/-5)**\n");
        slots.put("impact_checklist", "☑ Database(s) wijzigingen\n");
        slots.put("acceptance_criteria_body", "All criteria met.");
        slots.put("risk_narrative_body", "Low risk overall.");
        slots.put("deliberation_table", "**Agent deliberation:**\n| Agent | Vote |\n");
        slots.put("manual_test_body", "Tested manually.");
        slots.put("automated_test_body", "- Resultaat: 10 passed\n- Coverage: 80%");
        slots.put("deployment_body", "Standaard uitlevering: Ja");
        slots.put("dod_body", "☑ DoD is gereed voor alle issues.");
        return slots;
    }
}
