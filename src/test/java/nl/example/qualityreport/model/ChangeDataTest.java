package nl.example.qualityreport.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangeDataTest {

    @Test
    void groupByRole_aggregatesCommitsByRole() {
        var commits = List.of(commit("TE", 1, 10, 2), commit("TE", 2, 20, 5), commit("BE", 1, 5, 1));
        var data = ChangeData.from(commits, "", List.of("a.java"));

        var byRole = data.groupByRole();
        assertThat(byRole).containsKeys("TE", "BE");
        assertThat(byRole.get("TE").commitCount()).isEqualTo(2);
        assertThat(byRole.get("TE").insertions()).isEqualTo(30);
        assertThat(byRole.get("TE").deletions()).isEqualTo(7);
        assertThat(byRole.get("BE").commitCount()).isEqualTo(1);
    }

    @Test
    void countByFileType_detectsIDocAndScripts() {
        var data = ChangeData.from(
                List.of(),
                "",
                List.of("templates/letter.ftl", "migrations/V1__init.sql", "App.java", "report.docx", "config.xml"));

        assertThat(data.iDocCount()).isEqualTo(2);
        assertThat(data.scriptCount()).isEqualTo(1);
        assertThat(data.countByFileType().get("Other")).isEqualTo(2L);
    }

    @Test
    void isScript_detectsMigrationPaths() {
        assertThat(ChangeData.isScript("db/migration/V1__init.sql")).isTrue();
        assertThat(ChangeData.isScript("src/main/resources/db/migrate/001.sql")).isTrue();
        assertThat(ChangeData.isScript("scripts/update.sql")).isTrue();
        assertThat(ChangeData.isScript("App.java")).isFalse();
    }

    @Test
    void isIDoc_detectsTemplateExtensions() {
        assertThat(ChangeData.isIDoc("templates/letter.ftl")).isTrue();
        assertThat(ChangeData.isIDoc("docs/contract.DOCX")).isTrue();
        assertThat(ChangeData.isIDoc("README.md")).isFalse();
    }

    @Test
    void diffLineCount_returnsZeroForEmptyDiff() {
        var data = ChangeData.from(List.of(), "", List.of());
        assertThat(data.diffLineCount()).isEqualTo(0);
    }

    @Test
    void diffLineCount_countsLinesCorrectly() {
        var data = ChangeData.from(List.of(), "line1\nline2\nline3", List.of());
        assertThat(data.diffLineCount()).isEqualTo(3);
    }

    @Test
    void totalInsertionsAndDeletions() {
        var commits = List.of(commit("TE", 1, 10, 3), commit("BE", 2, 5, 2));
        var data = ChangeData.from(commits, "", List.of());

        assertThat(data.totalInsertions()).isEqualTo(15);
        assertThat(data.totalDeletions()).isEqualTo(5);
    }

    private static CommitInfo commit(String role, int files, int ins, int del) {
        return new CommitInfo(
                "abc123", "Author", "author@test.nl", role, "Team", "message", Instant.now(), files, ins, del);
    }
}
