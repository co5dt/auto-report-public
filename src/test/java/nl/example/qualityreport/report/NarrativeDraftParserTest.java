package nl.example.qualityreport.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NarrativeDraftParserTest {

    @Test
    void parsesValidJson() {
        String json =
                """
                {
                  "type": "change",
                  "claims": [
                    {"text": "De klasse Foo is toegevoegd.", "citedFactIds": ["class-0"]},
                    {"text": "Ticket PROJ-1 betreft een bugfix.", "citedFactIds": ["ticket-0"]}
                  ]
                }
                """;

        Optional<NarrativeDraft> result = NarrativeDraftParser.parse(json);

        assertThat(result).isPresent();
        assertThat(result.get().claims()).hasSize(2);
        assertThat(result.get().type()).isEqualTo("change");
        assertThat(result.get().claims().get(0).citedFactIds()).containsExactly("class-0");
    }

    @Test
    void extractsJsonFromCodeFence() {
        String wrapped =
                """
                ```json
                {"type": "risk", "claims": [{"text": "Er is een risico.", "citedFactIds": ["vote-0"]}]}
                ```
                """;

        Optional<NarrativeDraft> result = NarrativeDraftParser.parse(wrapped);

        assertThat(result).isPresent();
        assertThat(result.get().type()).isEqualTo("risk");
    }

    @Test
    void returnsEmptyOnInvalidJson() {
        assertThat(NarrativeDraftParser.parse("This is just prose.")).isEmpty();
    }

    @Test
    void returnsEmptyOnNullOrBlank() {
        assertThat(NarrativeDraftParser.parse(null)).isEmpty();
        assertThat(NarrativeDraftParser.parse("  ")).isEmpty();
    }

    @Test
    void returnsEmptyOnEmptyClaims() {
        String json = """
                {"type": "change", "claims": []}
                """;
        assertThat(NarrativeDraftParser.parse(json)).isEmpty();
    }

    @Test
    void renderProseJoinsClaimTexts() {
        var draft = new NarrativeDraft(
                "change",
                List.of(
                        new NarrativeDraft.NarrativeClaim("Eerste zin.", List.of("f-0")),
                        new NarrativeDraft.NarrativeClaim("Tweede zin.", List.of("f-1"))));

        assertThat(draft.renderProse()).isEqualTo("Eerste zin. Tweede zin.");
    }

    @Test
    void handlesNullCitedFactIds() {
        String json = """
                {"type": "change", "claims": [{"text": "Een claim."}]}
                """;

        Optional<NarrativeDraft> result = NarrativeDraftParser.parse(json);

        assertThat(result).isPresent();
        assertThat(result.get().claims().get(0).citedFactIds()).isEmpty();
    }
}
