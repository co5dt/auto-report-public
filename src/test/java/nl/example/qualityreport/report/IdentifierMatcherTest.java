package nl.example.qualityreport.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IdentifierMatcherTest {

    @Nested
    class DirectMatch {

        @Test
        void exactSubstring() {
            assertThat(IdentifierMatcher.containsIdentifier(
                            "De klasse DocumentController is gewijzigd.", "DocumentController"))
                    .isTrue();
        }

        @Test
        void caseInsensitive() {
            assertThat(IdentifierMatcher.containsIdentifier(
                            "de klasse documentcontroller is gewijzigd.", "DocumentController"))
                    .isTrue();
        }

        @Test
        void ticketIds() {
            assertThat(IdentifierMatcher.containsIdentifier("Ticket PROJ-91010 betreft een wijziging.", "PROJ-91010"))
                    .isTrue();
        }
    }

    @Nested
    class KebabCaseNormalization {

        @Test
        void camelCaseMatchesKebabInText() {
            assertThat(IdentifierMatcher.containsIdentifier(
                            "citizen-search-service implements the endpoint", "CitizenSearchService"))
                    .isTrue();
        }

        @Test
        void kebabIdentifierMatchesCamelInText() {
            assertThat(IdentifierMatcher.containsIdentifier("De CitizenSearchService klasse", "citizen-search-service"))
                    .isTrue();
        }
    }

    @Nested
    class TokenNormalization {

        @Test
        void camelCaseMatchesSpaceSeparatedTokens() {
            assertThat(IdentifierMatcher.containsIdentifier(
                            "the citizen search service handles requests", "CitizenSearchService"))
                    .isTrue();
        }
    }

    @Nested
    class UnderscoreNormalization {

        @Test
        void upperSnakeMatchesWithSpaces() {
            assertThat(IdentifierMatcher.containsIdentifier(
                            "De http timeout ms waarde is aangepast.", "HTTP_TIMEOUT_MS"))
                    .isTrue();
        }

        @Test
        void upperSnakeMatchesWithHyphens() {
            assertThat(IdentifierMatcher.containsIdentifier(
                            "De invalid-bsn foutcode wordt gecontroleerd.", "INVALID_BSN"))
                    .isTrue();
        }

        @Test
        void upperSnakeMatchesJoined() {
            assertThat(IdentifierMatcher.containsIdentifier("httptimeoutms is een configuratie.", "HTTP_TIMEOUT_MS"))
                    .isTrue();
        }

        @Test
        void exactUpperSnakeMatch() {
            assertThat(IdentifierMatcher.containsIdentifier("De constante INVALID_BSN wordt gebruikt.", "INVALID_BSN"))
                    .isTrue();
        }
    }

    @Nested
    class NoFalsePositives {

        @Test
        void partialMatchDoesNotCount() {
            assertThat(IdentifierMatcher.containsIdentifier("De controller is aangepast.", "DocumentController"))
                    .isFalse();
        }

        @Test
        void nullSafety() {
            assertThat(IdentifierMatcher.containsIdentifier(null, "test")).isFalse();
            assertThat(IdentifierMatcher.containsIdentifier("text", null)).isFalse();
        }

        @Test
        void unrelatedText() {
            assertThat(IdentifierMatcher.containsIdentifier("De database-migratie is voltooid.", "INVALID_BSN"))
                    .isFalse();
        }
    }

    @Nested
    class HelperMethods {

        @Test
        void normalizeToTokens() {
            assertThat(IdentifierMatcher.normalizeToTokens("CitizenSearchService"))
                    .isEqualTo("citizen search service");
        }

        @Test
        void camelToKebab() {
            assertThat(IdentifierMatcher.camelToKebab("CitizenSearchService")).isEqualTo("citizen-search-service");
        }

        @Test
        void nonCamelCaseUnchanged() {
            assertThat(IdentifierMatcher.normalizeToTokens("PROJ-91010")).isEqualTo("proj-91010");
        }
    }
}
