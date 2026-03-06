package nl.example.qualityreport.roster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RosterTest {

    private static final Path FIXTURES = Path.of("src/test/resources/fixtures");

    @Test
    void loadValidRoster_parsesTeamsAndMembers() throws IOException {
        Roster roster = Roster.load(FIXTURES.resolve("roster-valid.json"));

        assertThat(roster.teamCount()).isEqualTo(2);
        assertThat(roster.memberCount()).isEqualTo(5);
    }

    @Test
    void findByEmail_returnsMatchingMember() throws IOException {
        Roster roster = Roster.load(FIXTURES.resolve("roster-valid.json"));

        var member = roster.findByEmail("c.devries@example.nl");
        assertThat(member).isPresent();
        assertThat(member.get().role()).isEqualTo("BE");
        assertThat(member.get().team()).isEqualTo("Team Alpha");
        assertThat(member.get().name()).isEqualTo("Chris de Vries");
    }

    @Test
    void findByEmail_returnsMemberFromSecondTeam() throws IOException {
        Roster roster = Roster.load(FIXTURES.resolve("roster-valid.json"));

        var member = roster.findByEmail("a.tester@example.nl");
        assertThat(member).isPresent();
        assertThat(member.get().role()).isEqualTo("Tester");
        assertThat(member.get().team()).isEqualTo("Team Beta");
    }

    @Test
    void findByEmail_returnsEmptyForUnknownEmail() throws IOException {
        Roster roster = Roster.load(FIXTURES.resolve("roster-valid.json"));

        assertThat(roster.findByEmail("nobody@example.nl")).isEmpty();
    }

    @Test
    void findByEmail_returnsEmptyForNull() throws IOException {
        Roster roster = Roster.load(FIXTURES.resolve("roster-valid.json"));

        assertThat(roster.findByEmail(null)).isEmpty();
    }

    @Test
    void findByEmail_isCaseInsensitive() throws IOException {
        Roster roster = Roster.load(FIXTURES.resolve("roster-valid.json"));

        var member = roster.findByEmail("C.DEVRIES@Example.NL");
        assertThat(member).isPresent();
        assertThat(member.get().name()).isEqualTo("Chris de Vries");
    }

    @Test
    void findByEmail_trimsWhitespace() throws IOException {
        Roster roster = Roster.load(FIXTURES.resolve("roster-valid.json"));

        var member = roster.findByEmail("  c.devries@example.nl  ");
        assertThat(member).isPresent();
    }

    @Test
    void loadInvalidJson_throwsIOException() {
        assertThatThrownBy(() -> Roster.load(FIXTURES.resolve("roster-invalid.json")))
                .isInstanceOf(IOException.class);
    }

    @Test
    void loadDuplicateEmail_throwsOnCollision() {
        assertThatThrownBy(() -> Roster.load(FIXTURES.resolve("roster-duplicate-email.json")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Duplicate alias")
                .hasMessageContaining("duplicate@example.nl");
    }

    @Test
    void loadMissingOptionalFields_defaultsApplied() throws IOException {
        Roster roster = Roster.load(FIXTURES.resolve("roster-missing-fields.json"));

        assertThat(roster.memberCount()).isEqualTo(1);
        var member = roster.findByEmail("bare@example.nl");
        assertThat(member).isPresent();
        assertThat(member.get().name()).isEqualTo("unknown");
        assertThat(member.get().role()).isEqualTo("unknown");
        assertThat(member.get().team()).isEqualTo("Minimal");
    }

    @Test
    void loadNonexistentFile_throwsIOException() {
        assertThatThrownBy(() -> Roster.load(FIXTURES.resolve("nonexistent.json")))
                .isInstanceOf(IOException.class);
    }

    // --- Multi-email alias tests ---

    @Test
    void multiEmail_aliasResolvesToSameMember() throws IOException {
        Roster roster = Roster.load(FIXTURES.resolve("roster-multi-email-valid.json"));

        var byPrimary = roster.findByEmail("c.devries@example.nl");
        var byAlias = roster.findByEmail("chris.devries@external.org");

        assertThat(byPrimary).isPresent();
        assertThat(byAlias).isPresent();
        assertThat(byPrimary.get()).isSameAs(byAlias.get());
        assertThat(byPrimary.get().name()).isEqualTo("Chris de Vries");
        assertThat(byPrimary.get().role()).isEqualTo("BE");
        assertThat(byPrimary.get().team()).isEqualTo("Team Alpha");
    }

    @Test
    void multiEmail_memberCountReflectsDistinctPersons() throws IOException {
        Roster roster = Roster.load(FIXTURES.resolve("roster-multi-email-valid.json"));

        assertThat(roster.memberCount()).isEqualTo(3);
        assertThat(roster.teamCount()).isEqualTo(2);
    }

    @Test
    void multiEmail_aliasNormalizationIsCaseAndWhitespaceInsensitive() throws IOException {
        Roster roster = Roster.load(FIXTURES.resolve("roster-multi-email-valid.json"));

        var member = roster.findByEmail("  CHRIS.DEVRIES@EXTERNAL.ORG  ");
        assertThat(member).isPresent();
        assertThat(member.get().name()).isEqualTo("Chris de Vries");
    }

    @Test
    void multiEmail_mixedLegacy_singleEmailMemberStillResolves() throws IOException {
        Roster roster = Roster.load(FIXTURES.resolve("roster-multi-email-mixed-legacy.json"));

        var multiEmailMember = roster.findByEmail("chris.devries@external.org");
        assertThat(multiEmailMember).isPresent();
        assertThat(multiEmailMember.get().name()).isEqualTo("Chris de Vries");

        var legacyMember = roster.findByEmail("m.janssen@example.nl");
        assertThat(legacyMember).isPresent();
        assertThat(legacyMember.get().name()).isEqualTo("Martijn Janssen");

        assertThat(roster.memberCount()).isEqualTo(2);
    }

    @Test
    void multiEmail_duplicateAliasCrossMember_throwsIOException() {
        assertThatThrownBy(() -> Roster.load(FIXTURES.resolve("roster-multi-email-duplicate-alias.json")))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Duplicate alias")
                .hasMessageContaining("shared@example.nl");
    }

    @Test
    void multiEmail_emptyEmailsArray_memberSkipped() throws IOException {
        Roster roster = Roster.load(FIXTURES.resolve("roster-multi-email-invalid.json"));

        assertThat(roster.memberCount()).isEqualTo(0);
    }

    @Test
    void multiEmail_aliasesFieldOnMember() throws IOException {
        Roster roster = Roster.load(FIXTURES.resolve("roster-multi-email-valid.json"));

        var member = roster.findByEmail("c.devries@example.nl");
        assertThat(member).isPresent();
        assertThat(member.get().aliases()).containsExactly("c.devries@example.nl", "chris.devries@external.org");
    }

    @Test
    void multiEmail_primaryEmailIsFirst() throws IOException {
        Roster roster = Roster.load(FIXTURES.resolve("roster-multi-email-valid.json"));

        var member = roster.findByEmail("chris.devries@external.org");
        assertThat(member).isPresent();
        assertThat(member.get().email()).isEqualTo("c.devries@example.nl");
    }

    @Test
    void singleEmailRoster_backwardCompatible() throws IOException {
        Roster roster = Roster.load(FIXTURES.resolve("roster-valid.json"));

        assertThat(roster.memberCount()).isEqualTo(5);
        var member = roster.findByEmail("c.devries@example.nl");
        assertThat(member).isPresent();
        assertThat(member.get().aliases()).containsExactly("c.devries@example.nl");
    }
}
