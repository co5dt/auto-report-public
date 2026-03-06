package nl.example.qualityreport.git;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Reads file contents at specific git revisions. Operates on already-resolved
 * repos — does not manage roster or diff extraction.
 *
 * <p>Also provides a tracked-file index at a given revision for reference resolution.
 */
public class GitFileContentProvider {

    public static final int MAX_FILE_SIZE = 100_000;
    static final String TRUNCATION_MARKER = "\n... [truncated at %d bytes, total %d bytes]";

    /**
     * Reads the content of a file at the given revision (branch or ref name).
     *
     * @return the text content, truncated if larger than {@link #MAX_FILE_SIZE}
     */
    public Optional<String> readFileAtRevision(Path repoPath, String filePath, String revision) throws IOException {
        try (Repository repo = openRepo(repoPath)) {
            ObjectId revId = repo.resolve(revision);
            if (revId == null) {
                return Optional.empty();
            }
            return readBlob(repo, revId, filePath);
        }
    }

    /**
     * Lists all tracked file paths at the given revision.
     */
    public List<String> listTrackedFiles(Path repoPath, String revision) throws IOException {
        try (Repository repo = openRepo(repoPath)) {
            ObjectId revId = repo.resolve(revision);
            if (revId == null) {
                return List.of();
            }
            return listTreePaths(repo, revId);
        }
    }

    private Optional<String> readBlob(Repository repo, ObjectId revId, String filePath) throws IOException {
        try (RevWalk revWalk = new RevWalk(repo);
                TreeWalk treeWalk = new TreeWalk(repo)) {

            RevCommit commit = revWalk.parseCommit(revId);
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);

            while (treeWalk.next()) {
                if (treeWalk.getPathString().equals(filePath)) {
                    ObjectLoader loader = repo.open(treeWalk.getObjectId(0));
                    byte[] bytes = loader.getBytes();

                    if (isBinary(bytes)) {
                        return Optional.of("[binary file, " + bytes.length + " bytes]");
                    }

                    String content = new String(bytes, StandardCharsets.UTF_8);
                    if (bytes.length > MAX_FILE_SIZE) {
                        content = content.substring(0, Math.min(content.length(), MAX_FILE_SIZE))
                                + TRUNCATION_MARKER.formatted(MAX_FILE_SIZE, bytes.length);
                    }
                    return Optional.of(content);
                }
            }
        }
        return Optional.empty();
    }

    private List<String> listTreePaths(Repository repo, ObjectId revId) throws IOException {
        try (RevWalk revWalk = new RevWalk(repo);
                TreeWalk treeWalk = new TreeWalk(repo)) {

            RevCommit commit = revWalk.parseCommit(revId);
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);

            var paths = new ArrayList<String>();
            while (treeWalk.next()) {
                paths.add(treeWalk.getPathString());
            }
            return List.copyOf(paths);
        }
    }

    private Repository openRepo(Path repoPath) throws IOException {
        Path gitDir = repoPath.resolve(".git");
        if (!gitDir.toFile().isDirectory()) {
            gitDir = repoPath;
        }
        return new FileRepositoryBuilder()
                .setGitDir(gitDir.toFile())
                .readEnvironment()
                .build();
    }

    private static boolean isBinary(byte[] bytes) {
        int checkLen = Math.min(bytes.length, 8000);
        for (int i = 0; i < checkLen; i++) {
            if (bytes[i] == 0) {
                return true;
            }
        }
        return false;
    }
}
