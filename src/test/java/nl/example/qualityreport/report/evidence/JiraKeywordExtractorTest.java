package nl.example.qualityreport.report.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import nl.example.qualityreport.llm.LlmProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class JiraKeywordExtractorTest {

    @Nested
    class ResponseParsing {

        @Test
        void parsesCleanJsonArray() {
            List<String> result = JiraKeywordExtractor.parseResponse("[\"SQL injection\", \"BSN\", \"BRP mutatie\"]");
            assertThat(result).containsExactly("SQL injection", "BSN", "BRP mutatie");
        }

        @Test
        void parsesJsonEmbeddedInProse() {
            String response =
                    """
                    Here are the extracted keywords:
                    ["PASPOORT", "DigiD", "GBA-V"]
                    Hope that helps!
                    """;
            List<String> result = JiraKeywordExtractor.parseResponse(response);
            assertThat(result).containsExactly("PASPOORT", "DigiD", "GBA-V");
        }

        @Test
        void parsesJsonInCodeFence() {
            String response =
                    """
                    ```json
                    ["BSN", "MuniPortal"]
                    ```
                    """;
            List<String> result = JiraKeywordExtractor.parseResponse(response);
            assertThat(result).containsExactly("BSN", "MuniPortal");
        }

        @Test
        void filtersBlankEntries() {
            List<String> result = JiraKeywordExtractor.parseResponse("[\"BSN\", \"\", \"  \", \"DigiD\"]");
            assertThat(result).containsExactly("BSN", "DigiD");
        }

        @Test
        void returnsEmptyForNullResponse() {
            assertThat(JiraKeywordExtractor.parseResponse(null)).isEmpty();
        }

        @Test
        void returnsEmptyForBlankResponse() {
            assertThat(JiraKeywordExtractor.parseResponse("   ")).isEmpty();
        }

        @Test
        void returnsEmptyForMalformedJson() {
            assertThat(JiraKeywordExtractor.parseResponse("not json at all")).isEmpty();
        }

        @Test
        void returnsEmptyForPlainTextWithBrackets() {
            assertThat(JiraKeywordExtractor.parseResponse("Use [arrays] for storage"))
                    .isEmpty();
        }

        @Test
        void handlesDutchTerms() {
            List<String> result =
                    JiraKeywordExtractor.parseResponse("[\"BRP mutatie\", \"reisdocument\", \"stembureau\"]");
            assertThat(result).containsExactly("BRP mutatie", "reisdocument", "stembureau");
        }

        @Test
        void handlesMixedDutchEnglish() {
            List<String> result = JiraKeywordExtractor.parseResponse(
                    "[\"SQL injection\", \"PASPOORT\", \"rate limiting\", \"verkiezingen\"]");
            assertThat(result).containsExactly("SQL injection", "PASPOORT", "rate limiting", "verkiezingen");
        }
    }

    @Nested
    class Integration {

        @Test
        void extractsKeywordsViaStubLlm() {
            LlmProvider stub = (systemPrompt, userMessage) -> "[\"BSN\", \"BRP mutatie\"]";
            var extractor = new JiraKeywordExtractor(stub);

            List<String> result =
                    extractor.extract("Fix BSN lookup", "De BRP mutatie verwerking moet worden aangepast", null);

            assertThat(result).containsExactly("BSN", "BRP mutatie");
        }

        @Test
        void returnsEmptyWhenLlmThrows() {
            LlmProvider failing = (systemPrompt, userMessage) -> {
                throw new RuntimeException("LLM is down");
            };
            var extractor = new JiraKeywordExtractor(failing);

            List<String> result = extractor.extract("title", "description", null);
            assertThat(result).isEmpty();
        }

        @Test
        void returnsEmptyWhenAllInputsBlank() {
            LlmProvider neverCalled = (systemPrompt, userMessage) -> {
                throw new AssertionError("Should not call LLM on blank input");
            };
            var extractor = new JiraKeywordExtractor(neverCalled);

            assertThat(extractor.extract("", null, "  ")).isEmpty();
        }
    }
}
