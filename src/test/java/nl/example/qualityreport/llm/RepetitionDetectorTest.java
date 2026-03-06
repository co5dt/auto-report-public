package nl.example.qualityreport.llm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RepetitionDetectorTest {

    @Test
    void detectsRepeatingBlock() {
        String block = "This sentence repeats over and over again! ";
        String text = "x".repeat(500) + block.repeat(25);
        var detector = new RepetitionDetector(800, 40, 3);

        String fragment = detector.detectLoop(text);

        assertThat(fragment).isNotNull();
    }

    @Test
    void noLoopInNormalText() {
        var sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("Sentence number %d is unique content. ".formatted(i));
        }
        var detector = new RepetitionDetector();

        assertThat(detector.detectLoop(sb)).isNull();
    }

    @Test
    void noLoopWhenTextTooShort() {
        var detector = new RepetitionDetector();
        assertThat(detector.detectLoop("short text")).isNull();
    }

    @Test
    void detectsExactTailRepetition() {
        String repeated = "x".repeat(50);
        String text = "a".repeat(200) + repeated.repeat(5);
        var detector = new RepetitionDetector(300, 40, 3);

        assertThat(detector.detectLoop(text)).isNotNull();
    }

    @Test
    void returnsNullForEmptyInput() {
        var detector = new RepetitionDetector();
        assertThat(detector.detectLoop("")).isNull();
    }

    @Test
    void fragmentIsTruncated() {
        String block = "A".repeat(200);
        String text = "prefix".repeat(50) + block.repeat(5);
        var detector = new RepetitionDetector(1000, 40, 3);

        String fragment = detector.detectLoop(text);
        if (fragment != null) {
            assertThat(fragment.length()).isLessThanOrEqualTo(123);
        }
    }

    @Test
    void detectsLargeBlockRepetition() {
        // Simulates the repair-loop scenario: a full Dutch paragraph (~350 chars) repeated 3 times.
        // This block exceeds windowSize/minRepeats (800/3=266), so standard detection would miss it.
        String paragraph = "De klasse PaymentLogger wordt aangepast om structured logging toe te voegen "
                + "voor het volledige levenscyclus van betalingen. Het aanpassen van de klasse "
                + "PaymentLogger introduceert een technische complexiteit, omdat nieuwe methodes "
                + "logPaymentInitiated en logPaymentCompleted worden toegevoegd aan het systeem.";
        String text = paragraph.repeat(3);
        var detector = new RepetitionDetector();

        assertThat(detector.detectLoop(text)).isNotNull();
    }

    @Test
    void doesNotFalsePositiveOnLargeUniqueBlock() {
        // Large block appearing only once — should not trigger large-block detection.
        String paragraph = "De klasse PaymentLogger wordt aangepast om structured logging toe te voegen "
                + "voor het volledige levenscyclus van betalingen. Methodes logPaymentInitiated "
                + "en logPaymentCompleted worden toegevoegd. ";
        String text = "Unique preamble text that is long enough. ".repeat(20) + paragraph;
        var detector = new RepetitionDetector();

        assertThat(detector.detectLoop(text)).isNull();
    }

    @Test
    void similarityOfIdenticalStringsIsOne() {
        String s = "De klasse GbaLookupServiceTest.java is toegevoegd voor het testen van de GBA-lookup-service.";
        assertThat(RepetitionDetector.similarity(s, s)).isEqualTo(1.0);
    }

    @Test
    void similarityOfEmptyStringsIsOne() {
        assertThat(RepetitionDetector.similarity("", "")).isEqualTo(1.0);
    }

    @Test
    void similarityOfCompletelyDifferentStringsIsLow() {
        assertThat(RepetitionDetector.similarity("aaaaaaaaaa", "bbbbbbbbbb")).isEqualTo(0.0);
    }

    @Test
    void similarityOfNearDuplicatesIsHigh() {
        String a =
                "Het bestand GBAPersonEntity.java heeft veranderd met nieuwe attributen zoals gbav, die verwijst naar GBA-V.\n\n";
        String b =
                "Het bestand GBAPersonEntity.java heeft veranderd met nieuwe attributen zoals bsn, die verwijst naar Brondossiernummer.\n\n";
        assertThat(RepetitionDetector.similarity(a, b)).isGreaterThan(0.85);
    }

    @Test
    void similarityBelowThresholdDoesNotTrigger() {
        String a = "De klasse PaymentService verwerkt betalingen via iDEAL en biedt retry-logica.\n\n";
        String b = "De klasse PaymentService verwerkt betalingen via Bancontact en biedt cancel-logica.\n\n";
        assertThat(RepetitionDetector.similarity(a, b)).isLessThan(RepetitionDetector.SIMILARITY_THRESHOLD);
    }

    // ── Near-duplicate loop detection — detectLoop() integration tests ────────

    @Test
    void detectsAlternatingNearDuplicateLoop() {
        String sentenceA =
                "De klasse `GbaLookupServiceTest.java` is toegevoegd voor het testen van de GBA-lookup-service. "
                        + "De methode `testFindPersonByBsn` bevat een assertie waarbij BSN (Brondossiernummer) wordt gebruikt om personen te zoeken.\n\n";
        String sentenceB_gbav =
                "Het bestand `GBAPersonEntity.java` heeft veranderd met nieuwe attributen zoals `gbav`, "
                        + "die verwijst naar GBA-V.\n\n";
        String sentenceB_bsn = "Het bestand `GBAPersonEntity.java` heeft veranderd met nieuwe attributen zoals `bsn`, "
                + "die verwijst naar Brondossiernummer.\n\n";

        String prefix = "Introductory text. ".repeat(10);
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < 10; i++) {
            sb.append(sentenceA);
            sb.append(i % 2 == 0 ? sentenceB_gbav : sentenceB_bsn);
        }

        assertThat(new RepetitionDetector().detectLoop(sb)).isNotNull();
    }

    @Test
    void detectsThreeVariantCycleViaLargeBlockExactCheck() {
        String sentenceA =
                "De klasse GbaLookupServiceTest.java is toegevoegd voor het testen van de GBA-lookup-service. "
                        + "De methode testFindPersonByBsn bevat een assertie.\n\n";
        String sentenceB = "Het bestand GBAPersonEntity.java heeft veranderd met nieuwe attributen zoals gbav, "
                + "die verwijst naar GBA-V.\n\n";
        String sentenceC = "Het bestand GBAPersonEntity.java heeft veranderd met nieuwe attributen zoals bsn, "
                + "die verwijst naar BSN nummer.\n\n";

        String prefix = "Unique prefix. ".repeat(50);
        String loop = (sentenceA + sentenceB + sentenceC).repeat(4);
        assertThat(new RepetitionDetector().detectLoop(prefix + loop)).isNotNull();
    }

    @Test
    void detectsLoopThatStartsAfterLongPreamble() {
        StringBuilder preamble = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            preamble.append("Unique sentence number ").append(i).append(" about a different topic entirely. ");
        }
        String loopSentence = "De methode findByBsn zoekt personen op basis van BSN in de database.\n\n";
        String text = preamble + loopSentence.repeat(10);
        assertThat(new RepetitionDetector().detectLoop(text)).isNotNull();
    }

    @Test
    void detectsMinimalBlockAtBoundary() {
        String block = "A".repeat(40);
        String text = "prefix text to push past window size. ".repeat(25) + block.repeat(3);
        assertThat(new RepetitionDetector().detectLoop(text)).isNotNull();
    }

    @Test
    void doesNotFalsePositiveOnLoopAfterGoodContent() {
        String goodReport =
                """
                ## Wat is er gewijzigd en waarom?
                De CacheConfig klasse is toegevoegd om Redis-caching te configureren.
                De GbaLookupService is aangepast om gecachte lookups te ondersteunen.
                De CacheEvictionService biedt methodes om cache per BSN te invalideren.

                ## Risico-analyse
                **Risicoscore:** HIGH

                De implementatie introduceert een externe afhankelijkheid op Redis.
                Cache-invalidatie bij datawijzigingen vereist expliciete evictie-logica.
                TTL van 15 minuten is een bewuste keuze voor GBA-V consistentie.
                """;
        assertThat(new RepetitionDetector().detectLoop(goodReport)).isNull();
    }

    @Test
    void doesNotFalsePositiveOnDiverseText() {
        String text =
                """
                De eerste paragraaf beschrijft de configuratiewijziging in detail.
                Hierbij wordt de cache TTL aangepast van 60 naar 120 seconden.

                De tweede paragraaf behandelt de impact op de performance.
                Verwacht wordt dat response tijden met 30% verbeteren na de cache introductie.

                De derde paragraaf beschrijft de testresultaten na de wijziging.
                Alle 45 unit tests slagen en de coverage is gestegen naar 95 procent.

                De vierde paragraaf geeft een samenvatting van de deployment aanpak.
                Standaard uitlevering zonder feature toggle of handmatig script vereist.

                De vijfde paragraaf beschrijft de monitoring strategie na uitlevering.
                Logs worden gecheckt en metrics gemonitord gedurende de hypercare periode.

                De zesde paragraaf beschrijft de rollback procedure als problemen optreden.
                Een database restore staat klaar en het team is beschikbaar voor support.

                De zevende paragraaf beschrijft de acceptatiecriteria die zijn gevalideerd.
                BSN validatie, cache evictie per persoon en null-waarden worden niet gecached.

                De achtste paragraaf beschrijft de betrokken teams en hun verantwoordelijkheden.
                Team Alpha heeft de backend geimplementeerd, Team Beta heeft getest en gereviewd.
                """;
        assertThat(new RepetitionDetector().detectLoop(text)).isNull();
    }

    @Test
    void detectsDriftingLoopWithIncrementingCounter() {
        var sb = new StringBuilder("Unieke introductie. ".repeat(20));
        for (int i = 1; i <= 20; i++) {
            sb.append("De klasse CacheEvictionService verwijdert persoonlijke gegevens " + "uit de cache voor BSN ")
                    .append(i)
                    .append(". De methode evictPersonData wordt aangeroepen om data te invalideren.\n\n");
        }
        assertThat(new RepetitionDetector().detectLoop(sb)).isNotNull();
    }

    @Test
    void detectsRepeatWithMinorNoiseTokens() {
        String base = "De implementatie van het caching-systeem introduceert een externe afhankelijkheid op Redis. "
                + "Dit verhoogt de complexiteit van de deployment procedure aanzienlijk. "
                + "Daarnaast is cache-invalidatie bij datawijzigingen essentieel voor de data-integriteit.";
        var sb = new StringBuilder("Unique preamble text. ".repeat(30));
        for (int i = 0; i < 10; i++) {
            sb.append(base)
                    .append(" [PROJ-")
                    .append(90000 + i)
                    .append("] [2026-03-")
                    .append(String.format("%02d", 5 + i))
                    .append("] \n\n");
        }
        assertThat(new RepetitionDetector().detectLoop(sb)).isNotNull();
    }

    @Test
    void detectsShortSentenceRepeatUnderMinBlockSize() {
        String shortSentence = "Cache is nu actief.\n";
        String text = "Prefix. ".repeat(100) + shortSentence.repeat(50);
        assertThat(new RepetitionDetector().detectLoop(text)).isNotNull();
    }

    @Test
    void detectsMisalignedAlternatingLoopViaSlidingWindow() {
        String sentA = "De klasse GbaLookupServiceTest.java is toegevoegd voor het testen. "
                + "De methode testFind bevat een assertie.\n\n";
        String sentB_v1 = "Het bestand Entity.java heeft veranderd met nieuwe attributen zoals gbav.\n\n";
        String sentB_v2 = "Het bestand Entity.java heeft veranderd met nieuwe attributen zoals bsn.\n\n";

        var sb = new StringBuilder("X".repeat(73));
        for (int i = 0; i < 12; i++) {
            sb.append(sentA);
            sb.append(i % 2 == 0 ? sentB_v1 : sentB_v2);
        }
        assertThat(new RepetitionDetector().detectLoop(sb)).isNotNull();
    }

    @Test
    void detectsThreeNearDupVariantsCycleViaExactBlockCheck() {
        String vA = "De klasse PersonService is aangepast om de nieuwe zoekfunctionaliteit "
                + "te implementeren voor het opvragen van persoonsgegevens.\n\n";
        String vB = "De klasse PersonService is aangepast om de nieuwe validatiefunctionaliteit "
                + "te implementeren voor het controleren van persoonsgegevens.\n\n";
        String vC = "De klasse PersonService is aangepast om de nieuwe exportfunctionaliteit "
                + "te implementeren voor het downloaden van persoonsgegevens.\n\n";

        String prefix = "Uniek. ".repeat(50);
        String loop = (vA + vB + vC).repeat(5);
        assertThat(new RepetitionDetector().detectLoop(prefix + loop)).isNotNull();
    }

    @Test
    void detectionLatencyIsBounded() {
        String sentA = "De klasse `GbaLookupServiceTest.java` is toegevoegd voor het testen van de GBA-lookup-service. "
                + "De methode `testFindPersonByBsn` bevat een assertie waarbij BSN wordt gebruikt.\n\n";
        String sentB_v1 = "Het bestand `GBAPersonEntity.java` heeft veranderd met attributen zoals `gbav`.\n\n";
        String sentB_v2 = "Het bestand `GBAPersonEntity.java` heeft veranderd met attributen zoals `bsn`.\n\n";

        var detector = new RepetitionDetector();
        var sb = new StringBuilder();
        int detectedAt = -1;

        for (int i = 0; i < 50; i++) {
            sb.append(sentA);
            sb.append(i % 2 == 0 ? sentB_v1 : sentB_v2);
            if (detector.detectLoop(sb) != null) {
                detectedAt = sb.length();
                break;
            }
        }

        assertThat(detectedAt).as("Loop must be detected").isGreaterThan(0);
        assertThat(detectedAt).as("Detection must occur before 3000 chars").isLessThan(3000);
    }

    @Test
    void similarityPerformanceIsBounded() {
        String a = "De klasse PersonService is aangepast. ".repeat(13);
        String b = "De klasse PersonService is gewijzigd. ".repeat(13);
        a = a.substring(0, 480);
        b = b.substring(0, 480);

        long start = System.nanoTime();
        RepetitionDetector.similarity(a, b);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs)
                .as("Similarity on 480-char halves must complete under 200ms")
                .isLessThan(200);
    }

    // ── Tier 4: paragraph-level near-duplicate detection ─────────────────────

    @Test
    void detectsMultiVariantParagraphCycle() {
        String template = "Dezeveranderingen om multi-tenant isolatie toe te voegen impliceren "
                + "grote risico's, omdat ze veranderingen en nieuwe functionaliteit introduceren "
                + "die kunnen leiden tot bugs of regressies als niet volledig getest worden. "
                + "Dit betekent dat de klasse `%s` wordt aangepast aan om gemeente-specifieke "
                + "data toegang te bieden.";
        String[] classNames = {"TenantFilter", "TenantFilterTest", "TenantContextTest", "TenantConfig"};

        var sb = new StringBuilder("Uniek introductietekst. ".repeat(5));
        for (int cycle = 0; cycle < 3; cycle++) {
            for (String cls : classNames) {
                sb.append(template.formatted(cls)).append("\n\n");
            }
        }
        assertThat(new RepetitionDetector().detectLoop(sb)).isNotNull();
    }

    @Test
    void detectsFiveVariantCycleViaParagraphCheck() {
        String[] services = {"PersonService", "AddressService", "DocumentService", "AuthService", "CacheService"};
        var sb = new StringBuilder("Unique preamble content. ".repeat(10));
        for (int cycle = 0; cycle < 3; cycle++) {
            for (String svc : services) {
                sb.append("De klasse `%s` is aangepast om de nieuwe functionaliteit te ondersteunen. ".formatted(svc));
                sb.append("De implementatie introduceert wijzigingen in het domeinmodel en de servicelaag ");
                sb.append("die invloed kunnen hebben op bestaande integraties en de deploymentprocedure.\n\n");
            }
        }
        assertThat(new RepetitionDetector().detectLoop(sb)).isNotNull();
    }

    @Test
    void doesNotFalsePositiveOnStructuredReportParagraphs() {
        String report =
                """
                De wijziging betreft het toevoegen van een nieuwe kolom aan de PersonEntity tabel.
                Dit vereist een Flyway migratie V42__add_nationality die het schema uitbreidt.

                De GbaLookupService is uitgebreid met een nieuwe methode findByNationality.
                Deze methode biedt gemeente-medewerkers de mogelijkheid om op nationaliteit te zoeken.

                Het endpoint GET /api/persons/nationality/{code} is toegevoegd aan de REST controller.
                De response bevat een gepagineerde lijst van personen met de opgegeven nationaliteit.

                De unit tests voor GbaLookupServiceTest valideren de nieuwe zoekfunctionaliteit.
                Coverage is gestegen van 78% naar 85% door de toevoeging van 12 nieuwe test cases.

                De acceptatiecriteria zijn volledig afgedekt door zowel handmatige als geautomatiseerde tests.
                Het team heeft de wijziging gereviewed en goedgekeurd voor uitlevering naar productie.
                """;
        assertThat(new RepetitionDetector().detectLoop(report)).isNull();
    }

    @Test
    void handlesUnicodeNormalizationVariants() {
        String composed = "De café-configuratie is gewijzigd voor de résumé-functionaliteit.\n\n";
        String decomposed = "De cafe\u0301-configuratie is gewijzigd voor de re\u0301sume\u0301-functionaliteit.\n\n";

        var sb = new StringBuilder("Uniek introductietekst. ".repeat(20));
        for (int i = 0; i < 10; i++) {
            sb.append(i % 2 == 0 ? composed : decomposed);
        }
        assertThat(new RepetitionDetector().detectLoop(sb)).isNotNull();
    }
}
