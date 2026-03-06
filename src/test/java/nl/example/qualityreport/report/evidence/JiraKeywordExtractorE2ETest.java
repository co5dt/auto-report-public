package nl.example.qualityreport.report.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import nl.example.qualityreport.llm.OllamaProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * E2E tests for {@link JiraKeywordExtractor} against a live Ollama instance.
 *
 * <p>Gated by the {@code e2e} JUnit 5 tag — excluded from default {@code mvn test},
 * run via {@code mvn test -Pe2e}.
 *
 * <p>Each scenario sends realistic Jira text (Dutch municipality domain) through
 * the full extraction pipeline and validates that the LLM returns structurally
 * valid keywords relevant to the input.
 */
@Tag("e2e")
class JiraKeywordExtractorE2ETest {

    private static final String BASE_URL = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
    private static final String MODEL = System.getenv().getOrDefault("OLLAMA_MODEL", "qwen2.5-coder:3b");
    private static final boolean VERBOSE = "true".equalsIgnoreCase(System.getenv("OLLAMA_VERBOSE"));

    private static JiraKeywordExtractor extractor;

    @BeforeAll
    static void preflight() {
        assumeOllamaReachable();
        assumeModelAvailable();

        var provider = new OllamaProvider(MODEL, VERBOSE);
        extractor = new JiraKeywordExtractor(provider);
    }

    @Test
    void extractsSecurityTermsFromVulnerabilityTicket() {
        List<String> keywords = extractor.extract(
                "Fix SQL injection in BSN lookup endpoint",
                "De BSN lookup query in het BRP systeem is kwetsbaar voor SQL injection. "
                        + "Alle user input moet worden geparametreerd.",
                "BSN lookup is beveiligd tegen SQL injection en XSS");

        assertThat(keywords)
                .isNotEmpty()
                .anyMatch(k -> k.toLowerCase().contains("sql injection")
                        || k.toUpperCase().contains("BSN")
                        || k.toUpperCase().contains("XSS")
                        || k.toUpperCase().contains("BRP"));
    }

    @Test
    void extractsDutchMunicipalTerms() {
        List<String> keywords = extractor.extract(
                "BRP mutatie verwerking optimalisation",
                "De BRP mutatie verwerking via GBA-V koppeling moet sneller. "
                        + "Huidige verwerking van reisdocumenten duurt te lang bij piekbelasting.",
                null);

        assertThat(keywords)
                .isNotEmpty()
                .anyMatch(k -> k.toUpperCase().contains("BRP")
                        || k.toUpperCase().contains("GBA")
                        || k.toLowerCase().contains("reisdocument"));
    }

    @Test
    void extractsProductNamesFromAuthTicket() {
        List<String> keywords = extractor.extract(
                "DigiD SSO integratie voor MuniPortal",
                "Single sign-on via DigiD moet worden geïmplementeerd voor het "
                        + "MuniPortal portaal. SAML assertions moeten worden gevalideerd.",
                "Gebruiker kan inloggen via DigiD");

        assertThat(keywords)
                .isNotEmpty()
                .anyMatch(k -> k.toLowerCase().contains("digid")
                        || k.toLowerCase().contains("iburgerzaken")
                        || k.toLowerCase().contains("saml")
                        || k.toLowerCase().contains("sso"));
    }

    @Test
    void extractsFromMixedDutchEnglishTicket() {
        List<String> keywords = extractor.extract(
                "Rate limiting on verkiezingen API",
                "The /api/v2/verkiezingen/stembureaus endpoint needs rate limiting. "
                        + "Current CSRF token rotation is insufficient under load.",
                "Rate limit is 100 req/min per API key");

        assertThat(keywords)
                .isNotEmpty()
                .anyMatch(k -> k.toLowerCase().contains("rate limit")
                        || k.toLowerCase().contains("verkiezingen")
                        || k.toLowerCase().contains("stembureaus")
                        || k.toUpperCase().contains("CSRF")
                        || k.contains("/api/v2"));
    }

    @Test
    void extractsDocumentTypeTerms() {
        List<String> keywords = extractor.extract(
                "PASPOORT aanvraag validatie fout",
                "Bij het aanvragen van een PASPOORT via de burgerzaken module "
                        + "wordt het RIJBEWIJS nummer niet correct gevalideerd. "
                        + "De check tegen de RDW database faalt bij nummers met prefix 'NL'.",
                null);

        assertThat(keywords)
                .isNotEmpty()
                .anyMatch(k -> k.toUpperCase().contains("PASPOORT")
                        || k.toUpperCase().contains("RIJBEWIJS")
                        || k.toUpperCase().contains("RDW")
                        || k.toLowerCase().contains("burgerzaken"));
    }

    @Test
    void returnsValidJsonStructureForMinimalInput() {
        List<String> keywords = extractor.extract("BSN check", null, null);

        assertThat(keywords).isNotNull().allSatisfy(k -> assertThat(k).isNotBlank());
    }

    // --- preflight helpers ---

    private static void assumeOllamaReachable() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/api/tags"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            assumeTrue(
                    resp.statusCode() == 200,
                    "Ollama not reachable at " + BASE_URL + " (HTTP " + resp.statusCode() + ")");
        } catch (IOException | InterruptedException e) {
            assumeTrue(false, "Ollama not reachable at " + BASE_URL + ": " + e.getMessage());
        }
    }

    private static void assumeModelAvailable() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> resp = client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(BASE_URL + "/api/tags"))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            boolean modelPresent = resp.body().contains(MODEL);
            assumeTrue(modelPresent, "Model " + MODEL + " not available. Run: ollama pull " + MODEL);
        } catch (IOException | InterruptedException e) {
            assumeTrue(false, "Failed to check model availability: " + e.getMessage());
        }
    }
}
