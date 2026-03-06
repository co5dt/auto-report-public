package nl.example.qualityreport.git;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import nl.example.qualityreport.model.ChangeData;
import nl.example.qualityreport.model.CommitInfo;
import nl.example.qualityreport.model.RepoFile;
import nl.example.qualityreport.model.TeamMember;
import nl.example.qualityreport.roster.Roster;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.EmptyTreeIterator;

public class GitExtractor {

    private final Roster roster;

    public GitExtractor(Roster roster) {
        this.roster = roster;
    }

    public record ExtractionResult(ChangeData changeData, List<String> warnings) {}

    public ChangeData extract(List<Path> repoPaths, String branch, String target) throws IOException {
        return extractWithWarnings(repoPaths, branch, target).changeData();
    }

    public ExtractionResult extractWithWarnings(List<Path> repoPaths, String branch, String target) throws IOException {
        if (repoPaths.size() == 1) {
            validateRefs(repoPaths, branch, target);
        }

        var allCommits = new ArrayList<CommitInfo>();
        var allDiffs = new StringBuilder();
        var allChangedFiles = new ArrayList<RepoFile>();
        var warnings = new ArrayList<String>();
        int successCount = 0;

        for (Path repoPath : repoPaths) {
            try {
                validateRefs(List.of(repoPath), branch, target);
                extractSingleRepo(repoPath, branch, target, allCommits, allDiffs, allChangedFiles);
                successCount++;
            } catch (IOException e) {
                if (repoPaths.size() == 1) {
                    throw e;
                }
                warnings.add("Skipped %s: %s".formatted(repoLabel(repoPath), e.getMessage()));
            }
        }

        if (successCount == 0) {
            throw new IOException(
                    "All %d repositories failed ref validation. Nothing to extract.".formatted(repoPaths.size()));
        }

        allCommits.sort(Comparator.comparing(CommitInfo::date));

        return new ExtractionResult(
                ChangeData.fromRepoFiles(allCommits, allDiffs.toString(), allChangedFiles), List.copyOf(warnings));
    }

    private void validateRefs(List<Path> repoPaths, String branch, String target) throws IOException {
        Map<Path, List<String>> failures = new LinkedHashMap<>();
        Map<Path, List<String>> availableBranches = new LinkedHashMap<>();

        for (Path repoPath : repoPaths) {
            Path gitDir = repoPath.resolve(".git");
            if (!gitDir.toFile().isDirectory()) {
                gitDir = repoPath;
            }
            try (Repository repo = new FileRepositoryBuilder()
                    .setGitDir(gitDir.toFile())
                    .readEnvironment()
                    .build()) {
                var missing = new ArrayList<String>();
                if (repo.resolve(branch) == null) {
                    missing.add(branch);
                }
                if (repo.resolve(target) == null) {
                    missing.add(target);
                }
                if (!missing.isEmpty()) {
                    failures.put(repoPath, missing);
                    var branches = new ArrayList<String>();
                    repo.getRefDatabase().getRefsByPrefix("refs/heads/").stream()
                            .map(ref -> ref.getName().substring("refs/heads/".length()))
                            .sorted()
                            .forEach(branches::add);
                    availableBranches.put(repoPath, branches);
                }
            }
        }

        if (!failures.isEmpty()) {
            var sb = new StringBuilder("Cannot resolve refs in ");
            sb.append(failures.size() == 1 ? "1 repository:" : failures.size() + " repositories:");
            for (var entry : failures.entrySet()) {
                sb.append("\n  ")
                        .append(entry.getKey())
                        .append(": missing ")
                        .append(String.join(", ", entry.getValue()));
                List<String> branches = availableBranches.get(entry.getKey());
                if (branches != null && !branches.isEmpty()) {
                    sb.append("\n    available branches: ").append(String.join(", ", branches));
                }
            }
            sb.append("\n\nHint: use --target <branch> to specify the correct target branch.");
            throw new IOException(sb.toString());
        }
    }

    static String repoLabel(Path repoPath) {
        return repoPath.getFileName().toString();
    }

    /**
     * Detects the default branch for the given repo by trying common names
     * ({@code main}, {@code master}) and falling back to HEAD.
     */
    public static String detectDefaultBranch(Path repoPath) throws IOException {
        Path gitDir = repoPath.resolve(".git");
        if (!gitDir.toFile().isDirectory()) {
            gitDir = repoPath;
        }
        try (Repository repo = new FileRepositoryBuilder()
                .setGitDir(gitDir.toFile())
                .readEnvironment()
                .build()) {
            for (String candidate : List.of("main", "master")) {
                if (repo.resolve(candidate) != null) {
                    return candidate;
                }
            }
            return "HEAD";
        }
    }

    private void extractSingleRepo(
            Path repoPath,
            String branch,
            String target,
            List<CommitInfo> commits,
            StringBuilder diffs,
            List<RepoFile> changedFiles)
            throws IOException {
        Path gitDir = repoPath.resolve(".git");
        if (!gitDir.toFile().isDirectory()) {
            gitDir = repoPath;
        }

        String repoName = repoLabel(repoPath);

        try (Repository repo = new FileRepositoryBuilder()
                .setGitDir(gitDir.toFile())
                .readEnvironment()
                .build()) {

            ObjectId branchHead = resolveRef(repo, branch);
            ObjectId targetHead = resolveRef(repo, target);

            extractCommits(repo, branchHead, targetHead, repoName, commits);
            extractDiff(repo, branchHead, targetHead, repoName, diffs, changedFiles);
        }
    }

    private ObjectId resolveRef(Repository repo, String refName) throws IOException {
        ObjectId id = repo.resolve(refName);
        if (id == null) {
            throw new IOException("Cannot resolve ref '" + refName + "' in " + repo.getDirectory());
        }
        return id;
    }

    private void extractCommits(
            Repository repo, ObjectId branchHead, ObjectId targetHead, String repoName, List<CommitInfo> commits)
            throws IOException {
        try (RevWalk walk = new RevWalk(repo);
                DiffFormatter df = new DiffFormatter(org.eclipse.jgit.util.io.DisabledOutputStream.INSTANCE)) {
            df.setRepository(repo);
            df.setDetectRenames(true);

            walk.markStart(walk.parseCommit(branchHead));
            walk.markUninteresting(walk.parseCommit(targetHead));

            for (RevCommit commit : walk) {
                if (commit.getParentCount() > 1) {
                    continue;
                }

                var ident = commit.getAuthorIdent();
                var member = roster.findByEmail(ident.getEmailAddress());

                int filesChanged = 0;
                int insertions = 0;
                int deletions = 0;

                AbstractTreeIterator parentTree = (commit.getParentCount() > 0)
                        ? prepareTreeParser(repo, commit.getParent(0).getId())
                        : new EmptyTreeIterator();
                AbstractTreeIterator commitTree = prepareTreeParser(repo, commit.getId());

                List<DiffEntry> diffs = df.scan(parentTree, commitTree);
                filesChanged = diffs.size();
                for (DiffEntry entry : diffs) {
                    var edits = df.toFileHeader(entry).toEditList();
                    for (var edit : edits) {
                        insertions += edit.getEndB() - edit.getBeginB();
                        deletions += edit.getEndA() - edit.getBeginA();
                    }
                }

                commits.add(new CommitInfo(
                        commit.getName(),
                        ident.getName(),
                        ident.getEmailAddress(),
                        member.map(TeamMember::role).orElse("unknown"),
                        member.map(TeamMember::team).orElse("unknown"),
                        commit.getShortMessage(),
                        ident.getWhenAsInstant(),
                        filesChanged,
                        insertions,
                        deletions,
                        repoName));
            }
        }
    }

    private void extractDiff(
            Repository repo,
            ObjectId branchHead,
            ObjectId targetHead,
            String repoName,
            StringBuilder diffs,
            List<RepoFile> changedFiles)
            throws IOException {
        try (Git git = new Git(repo)) {
            var diffOutput = new ByteArrayOutputStream();
            AbstractTreeIterator oldTree = prepareTreeParser(repo, targetHead);
            AbstractTreeIterator newTree = prepareTreeParser(repo, branchHead);

            List<DiffEntry> entries = git.diff()
                    .setOldTree(oldTree)
                    .setNewTree(newTree)
                    .setOutputStream(diffOutput)
                    .call();

            diffs.append(diffOutput.toString(StandardCharsets.UTF_8));

            for (DiffEntry entry : entries) {
                String path =
                        switch (entry.getChangeType()) {
                            case DELETE -> entry.getOldPath();
                            default -> entry.getNewPath();
                        };
                changedFiles.add(new RepoFile(repoName, path));
            }
        } catch (org.eclipse.jgit.api.errors.GitAPIException e) {
            throw new IOException("Failed to compute diff", e);
        }
    }

    private AbstractTreeIterator prepareTreeParser(Repository repo, ObjectId objectId) throws IOException {
        try (RevWalk walk = new RevWalk(repo)) {
            RevCommit commit = walk.parseCommit(objectId);
            var treeId = commit.getTree().getId();

            var parser = new CanonicalTreeParser();
            try (var reader = repo.newObjectReader()) {
                parser.reset(reader, treeId);
            }
            return parser;
        }
    }
}
