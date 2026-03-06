package nl.example.qualityreport.model;

/**
 * A file path qualified by its originating repository.
 */
public record RepoFile(String repoName, String path) {

    public RepoFile(String path) {
        this("", path);
    }
}
