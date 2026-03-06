package nl.example.qualityreport.report.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DomainKeywordDictionaryTest {

    @Nested
    class Loading {

        @Test
        void loadsFromJsonStream() {
            String json = """
                    {"terms": ["BRP mutatie", "GBA-V", "BSN"]}
                    """;
            var dict =
                    DomainKeywordDictionary.parseJson(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

            assertThat(dict.terms()).containsExactly("BRP mutatie", "GBA-V", "BSN");
            assertThat(dict.size()).isEqualTo(3);
            assertThat(dict.isEmpty()).isFalse();
        }

        @Test
        void loadsFromFilePath(@TempDir Path tmp) throws IOException {
            Path file = tmp.resolve("kw.json");
            Files.writeString(file, """
                    {"terms": ["DigiD", "PASPOORT"]}
                    """);

            var dict = DomainKeywordDictionary.loadFromPath(file);

            assertThat(dict.terms()).containsExactly("DigiD", "PASPOORT");
        }

        @Test
        void returnsEmptyForMissingFile() {
            var dict = DomainKeywordDictionary.loadFromPath(Path.of("/nonexistent/kw.json"));
            assertThat(dict.isEmpty()).isTrue();
        }

        @Test
        void returnsEmptyForNullPath() {
            var dict = DomainKeywordDictionary.loadFromPath(null);
            assertThat(dict.isEmpty()).isTrue();
        }

        @Test
        void returnsEmptyForMalformedJson() {
            String bad = "not json at all";
            var dict =
                    DomainKeywordDictionary.parseJson(new ByteArrayInputStream(bad.getBytes(StandardCharsets.UTF_8)));
            assertThat(dict.isEmpty()).isTrue();
        }

        @Test
        void returnsEmptyForMissingTermsArray() {
            String json = """
                    {"other": "value"}
                    """;
            var dict =
                    DomainKeywordDictionary.parseJson(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            assertThat(dict.isEmpty()).isTrue();
        }

        @Test
        void skipsBlankTerms() {
            String json = """
                    {"terms": ["BSN", "", "  ", "GBA-V"]}
                    """;
            var dict =
                    DomainKeywordDictionary.parseJson(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
            assertThat(dict.terms()).containsExactly("BSN", "GBA-V");
        }
    }

    @Nested
    class Matching {

        @Test
        void caseInsensitiveMatch() {
            var dict = dictOf("SQL injection", "PASPOORT");

            assertThat(dict.findMatchesIn("Fix SQL Injection vulnerability")).containsExactly("SQL injection");
            assertThat(dict.findMatchesIn("paspoort verlenging")).containsExactly("PASPOORT");
        }

        @Test
        void hyphenSpaceNormalization() {
            var dict = dictOf("BRP mutatie");

            assertThat(dict.findMatchesIn("De BRP-mutatie is verwerkt")).containsExactly("BRP mutatie");
            assertThat(dict.findMatchesIn("BRP  mutatie controleren")).containsExactly("BRP mutatie");
            assertThat(dict.findMatchesIn("brp_mutatie log")).containsExactly("BRP mutatie");
        }

        @Test
        void multipleMatchesInSameText() {
            var dict = dictOf("BSN", "DigiD", "GBA-V");

            List<String> found = dict.findMatchesIn("Burger BSN ophalen via GBA-V met DigiD authenticatie");
            assertThat(found).containsExactlyInAnyOrder("BSN", "DigiD", "GBA-V");
        }

        @Test
        void noMatchReturnsEmpty() {
            var dict = dictOf("PASPOORT", "RIJBEWIJS");
            assertThat(dict.findMatchesIn("Add cache layer for lookups")).isEmpty();
        }

        @Test
        void emptyTextReturnsEmpty() {
            var dict = dictOf("BSN");
            assertThat(dict.findMatchesIn("")).isEmpty();
            assertThat(dict.findMatchesIn(null)).isEmpty();
        }

        @Test
        void emptyDictionaryNeverMatches() {
            var dict = DomainKeywordDictionary.empty();
            assertThat(dict.findMatchesIn("BSN DigiD PASPOORT")).isEmpty();
        }
    }

    @Nested
    class Normalization {

        @Test
        void normalizesVariousSeparators() {
            assertThat(DomainKeywordDictionary.normalize("BRP mutatie")).isEqualTo("brp mutatie");
            assertThat(DomainKeywordDictionary.normalize("BRP-mutatie")).isEqualTo("brp mutatie");
            assertThat(DomainKeywordDictionary.normalize("BRP_mutatie")).isEqualTo("brp mutatie");
            assertThat(DomainKeywordDictionary.normalize("BRP  mutatie")).isEqualTo("brp mutatie");
            assertThat(DomainKeywordDictionary.normalize("GBA-V")).isEqualTo("gba v");
        }
    }

    private static DomainKeywordDictionary dictOf(String... terms) {
        var sb = new StringBuilder("{\"terms\":[");
        for (int i = 0; i < terms.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(terms[i]).append("\"");
        }
        sb.append("]}");
        return DomainKeywordDictionary.parseJson(
                new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8)));
    }
}
