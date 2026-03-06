package nl.example.qualityreport.git;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import nl.example.qualityreport.model.RepoFile;

/**
 * Resolves fuzzy code references (class names, basenames, partial paths)
 * to canonical file paths from a known set of tracked files.
 *
 * <p>Resolution order (first match wins):
 * <ol>
 *   <li>Exact path match</li>
 *   <li>Basename match (e.g. "Foo.java" → "src/main/java/Foo.java")</li>
 *   <li>Class/symbol match (e.g. "Foo" → "src/main/java/Foo.java")</li>
 *   <li>Partial path suffix match (e.g. "cache/PlCache.java" → "src/.../cache/PlCache.java")</li>
 * </ol>
 *
 * <p>When files are repo-qualified, ambiguity across repos is detected and
 * candidates include repo-qualified entries so the caller can read from the
 * correct repository.
 */
public class CodeReferenceResolver {

    private final List<RepoFile> trackedFiles;
    private final List<RepoFile> changedFiles;

    public CodeReferenceResolver(Collection<String> trackedPaths, Collection<String> changedPaths) {
        this.trackedFiles = trackedPaths.stream().map(RepoFile::new).toList();
        this.changedFiles = changedPaths.stream().map(RepoFile::new).toList();
    }

    public CodeReferenceResolver(List<RepoFile> trackedFiles, List<RepoFile> changedFiles, boolean repoQualified) {
        this.trackedFiles = List.copyOf(trackedFiles);
        this.changedFiles = List.copyOf(changedFiles);
    }

    /**
     * Result of resolving a reference.
     *
     * @param status      one of UNIQUE_MATCH, AMBIGUOUS, NO_MATCH
     * @param repoMatches the candidate repo-qualified files
     * @param strategy    which resolution strategy produced the result
     */
    public record Resolution(Status status, List<RepoFile> repoMatches, String strategy) {

        public enum Status {
            UNIQUE_MATCH,
            AMBIGUOUS,
            NO_MATCH
        }

        /** Backward-compatible accessor returning plain paths. */
        public List<String> matches() {
            return repoMatches.stream().map(RepoFile::path).toList();
        }

        public static Resolution unique(RepoFile file, String strategy) {
            return new Resolution(Status.UNIQUE_MATCH, List.of(file), strategy);
        }

        public static Resolution unique(String path, String strategy) {
            return unique(new RepoFile(path), strategy);
        }

        public static Resolution ambiguous(List<RepoFile> files, String strategy) {
            return new Resolution(Status.AMBIGUOUS, List.copyOf(files), strategy);
        }

        public static Resolution noMatch() {
            return new Resolution(Status.NO_MATCH, List.of(), "none");
        }
    }

    /**
     * Resolve a reference, preferring changed files over all tracked files.
     * @param ref the reference string from the agent
     * @param restrictToChanged if true, only match against changed files
     */
    public Resolution resolve(String ref, boolean restrictToChanged) {
        if (ref == null || ref.isBlank()) {
            return Resolution.noMatch();
        }

        String cleaned = ref.strip();
        List<RepoFile> scope = restrictToChanged ? changedFiles : trackedFiles;

        Resolution r;

        r = tryExactMatch(cleaned, scope);
        if (r != null) return r;

        r = tryBasenameMatch(cleaned, scope);
        if (r != null) return r;

        r = tryClassNameMatch(cleaned, scope);
        if (r != null) return r;

        r = trySuffixMatch(cleaned, scope);
        if (r != null) return r;

        if (restrictToChanged) {
            return resolve(ref, false);
        }

        return Resolution.noMatch();
    }

    private Resolution tryExactMatch(String ref, List<RepoFile> scope) {
        var matches = new ArrayList<RepoFile>();
        for (RepoFile rf : scope) {
            if (rf.path().equals(ref)) {
                matches.add(rf);
            }
        }
        return toResolution(matches, "exact");
    }

    private Resolution tryBasenameMatch(String ref, List<RepoFile> scope) {
        var matches = new ArrayList<RepoFile>();
        for (RepoFile rf : scope) {
            String path = rf.path();
            String basename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            if (basename.equals(ref)) {
                matches.add(rf);
            }
        }
        return toResolution(matches, "basename");
    }

    private Resolution tryClassNameMatch(String ref, List<RepoFile> scope) {
        if (ref.contains("/") || ref.contains(".")) {
            return null;
        }

        var matches = new ArrayList<RepoFile>();
        for (RepoFile rf : scope) {
            String path = rf.path();
            String basename = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            String nameWithoutExt =
                    basename.contains(".") ? basename.substring(0, basename.lastIndexOf('.')) : basename;
            if (nameWithoutExt.equals(ref)) {
                matches.add(rf);
            }
        }
        return toResolution(matches, "class_name");
    }

    private Resolution trySuffixMatch(String ref, List<RepoFile> scope) {
        var matches = new ArrayList<RepoFile>();
        String suffixToMatch = ref.startsWith("/") ? ref : "/" + ref;
        for (RepoFile rf : scope) {
            String pathWithSlash = "/" + rf.path();
            if (pathWithSlash.endsWith(suffixToMatch)) {
                matches.add(rf);
            }
        }
        return toResolution(matches, "suffix");
    }

    private Resolution toResolution(List<RepoFile> matches, String strategy) {
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() == 1) {
            return Resolution.unique(matches.getFirst(), strategy);
        }
        return Resolution.ambiguous(matches, strategy);
    }
}
