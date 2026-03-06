# Quality Report CLI

A Java CLI tool that generates audit-ready quality reports (testrapporten) by combining
local Git data, Jira context, and LLM-based risk analysis.

## Prerequisites

- Java 21+
- Maven 3.9+

## Build

```bash
mvn clean package -DskipTests
```

## Run

```bash
# Basic usage
java -jar target/quality-report-cli.jar \
    --repo ./path/to/repo \
    --branch feature/PROJ-12345

# Custom roster location
java -jar target/quality-report-cli.jar \
    --repo ./path/to/repo \
    --branch feature/PROJ-12345 \
    --roster ./team-roster.json

# Use a specific Ollama model
java -jar target/quality-report-cli.jar \
    --repo ./path/to/repo \
    --branch feature/PROJ-12345 \
    --provider ollama \
    --model qwen2.5-coder:3b

# Skip LLM analysis (output raw data as JSON)
java -jar target/quality-report-cli.jar \
    --repo ./path/to/repo \
    --branch feature/PROJ-12345 \
    --no-llm
```

## Behavior Notes

### Target branch auto-detection
When `--target` is not specified, the tool auto-detects the default branch by
trying `main`, then `master`, falling back to `HEAD`. Use `--target <branch>` to
override.

### Multi-repo partial failure
When multiple repos are passed via `--repo`, a single repo failing ref validation
no longer aborts the entire run. Valid repos are processed and a per-repo warning
is printed. If **all** repos fail, the run aborts with a clear error.

### Merge commit filtering
Merge commits (those with more than one parent) are excluded from the commit list
by default. This removes "Merge branch 'x' into 'y'" noise from the context sent
to risk agents.

### Binary diff handling
Binary files (images, compiled artifacts) are detected in the diff and represented
as compact `binary file` metadata instead of passing garbled patch content to the
LLM.

### Empty diff guardrail
If the source and target branches produce zero commits and zero changed files, the
run aborts with a clear error message instead of producing an empty report.

### Roster mismatch warnings
When commit author emails are not found in the team roster, a warning is printed
in the CLI summary showing the count and specific unmatched emails.

### Yes/No prompt language support
Interactive prompts accept `y/yes/ja` and `n/no/nee` (Dutch + English). Unrecognised
input triggers a single re-prompt instead of silently applying the default.

### Report filename collision safety
If a report file with the same name already exists, a numeric suffix (`-2`, `-3`, ...)
is appended instead of silently overwriting the previous report.

### Single-agent deliberation wording
When running with a single agent (`CONSENSUS_AGENT_COUNT=1`), the report labels the
consensus as "single agent" rather than "unanimous".

### DoD as governance gate
The Definition of Done (DoD) status is rendered prominently in the report for human
reviewers but is intentionally **not** included in the risk-agent deliberation context.
Agents assess deployment risk (technical complexity, evidence quality, impact);
DoD completeness is a release governance question for human decision-making.

## Configuration

Settings are resolved with precedence: **`--model` CLI flag > environment variable > `application.properties` > code default**.

The properties file is at `src/main/resources/application.properties`.

| Setting | Env Variable | Property Key | Default | Description |
|---------|-------------|--------------|---------|-------------|
| Anthropic API key | `ANTHROPIC_API_KEY` | — | (required) | API key for Claude |
| Anthropic model | `ANTHROPIC_MODEL` | — | `claude-sonnet-4-5-20250929` | Claude model name |
| Ollama URL | `OLLAMA_BASE_URL` | — | `http://localhost:11434` | Ollama server URL |
| Ollama model | `OLLAMA_MODEL` | — | `qwen2.5-coder:3b` | Ollama model name (overridable with `--model`) |
| Ollama context size | `OLLAMA_NUM_CTX` | — | `32768` | Context window in tokens (overridable with `--num-ctx`) |
| Ollama repeat penalty | `OLLAMA_REPEAT_PENALTY` | — | `1.1` | Penalises recently used tokens; raises this to reduce repetition loops |
| Ollama repeat last n | `OLLAMA_REPEAT_LAST_N` | — | `64` | How many past tokens to check for repetition |
| Ollama temperature | `OLLAMA_TEMPERATURE` | — | `0.7` (model-specific profiles may differ) | Sampling temperature; lower = more deterministic |
| Ollama top-k | `OLLAMA_TOP_K` | — | `40` | Limits vocabulary pool per step |
| Ollama top-p | `OLLAMA_TOP_P` | — | `0.9` | Nucleus sampling cutoff |
| Agent count | `CONSENSUS_AGENT_COUNT` | `consensus.agent.count` | `3` | Number of deliberation agents (1-3) |
| Tool loop | `TOOL_LOOP_ENABLED` | `tool.loop.enabled` | `false` | Enable agent tool requests during deliberation |
| Evidence-first | `EVIDENCE_FIRST_ENABLED` | `evidence.first.enabled` | `false` | Deterministic fact extraction before narrative generation |
| Narrative verify | `NARRATIVE_VERIFY_ENABLED` | `narrative.verify.enabled` | `false` | Post-generation check for must-mention identifiers |
| Repair retries | `NARRATIVE_REPAIR_MAX_RETRIES` | `narrative.repair.max-retries` | `1` | Max LLM repair attempts when verification fails (0-3) |
| Debug artifacts | `NARRATIVE_DEBUG_ARTIFACTS_ENABLED` | `narrative.debug.artifacts.enabled` | `false` | Emit evidence/draft/verification JSON to target/ |
| Domain keywords | `DOMAIN_KEYWORDS_PATH` | `domain.keywords.path` | (none) | Path to domain-keywords JSON file for term enrichment |

### Model-specific sampling profiles

Small/distilled models (e.g. `deepcoder:1.5b`, `qwen3.5:0.8b`) are prone to repetition loops at
default sampling settings. The `ModelSamplingProfiles` class provides per-model defaults
(lower temperature, stronger repeat penalty) that are applied automatically when the model
name matches. Explicit env var overrides still take precedence over profile defaults.

## Test

```bash
# Unit + integration tests (default)
mvn test

# Ollama E2E tests (requires running Ollama instance)
mvn test -Pe2e

# Qwen accuracy E2E tests
mvn test -Paccuracy-e2e

# Tool-use E2E tests (validates Qwen tool-calling behavior)
mvn test -Ptool-e2e

# Report E2E tests (20 scenarios with quality gates)
OLLAMA_MODEL=qwen2.5-coder:3b OLLAMA_VERBOSE=true mvn test -Preport-e2e -Dsurefire.useFile=false
```

Results from tool-use E2E tests are written to `target/tool-e2e/` as JSON metrics and markdown summaries.

### Git fixture testing

Accuracy and tool-use E2E tests exercise the full git extraction pipeline by
creating temporary JGit repositories at test time (via `ScenarioGitRepoBuilder`).
A contract test (`AccuracyScenarioFixturesTest`) validates extraction in the
default `mvn test` lane without requiring Ollama.

Do **not** commit `.git` directories or nested repositories inside this project.
GitHub treats tracked `.git` entries as submodule gitlinks, which creates
confusing behavior. All git test fixtures must be ephemeral, created under
JUnit `@TempDir` and cleaned up automatically.

## Evidence-First Architecture

When `--evidence-first` is enabled, the system uses a deterministic pipeline for grounding
LLM narratives:

1. **Deterministic extraction** — `DeterministicEvidenceExtractor` parses changed files,
   diffs, and Jira data to extract typed facts (tickets, class names, SQL migrations,
   annotation literals, API paths, config keys) without any LLM calls.

2. **Retrieval-aware context** — `NarrativeContextAssembler` builds LLM context with
   explicit `<must_mention>` identifiers and targeted diff hunks around those identifiers,
   replacing the fixed 4000-character truncation.

3. **Structured draft** — The LLM is prompted to return a JSON IR (`NarrativeDraft`) with
   individual claims citing evidence fact IDs. This is rendered deterministically into prose.

4. **Verify/repair loop** — `NarrativeVerifier` checks all must-mention identifiers appear
   in the output. Missing identifiers trigger a targeted repair prompt (bounded retries).

All features are behind flags and default to off. Enable incrementally:

```bash
java -jar target/quality-report-cli.jar \
    --repo ./path/to/repo \
    --branch feature/PROJ-12345 \
    --provider ollama \
    --evidence-first \
    --verify-narrative \
    --debug-artifacts
```

### Domain Keyword Enrichment

When evidence-first is enabled, the system can further enrich the evidence bundle with
domain-specific keywords from two complementary sources:

**External dictionary** — A JSON file containing known critical terms. Terms found in
the change context (diffs, commits, Jira text) are promoted as must-mention facts.

```json
{"terms": ["BRP mutatie", "GBA-V", "BSN", "SQL injection", "PASPOORT"]}
```

Point to the file via `DOMAIN_KEYWORDS_PATH` env var or `domain.keywords.path` property.
A default dictionary is provided at `domain-keywords.json` in the project root.

**LLM-based Jira extraction** — `JiraKeywordExtractor` sends Jira text (title,
description, acceptance criteria) to the LLM with a prompt optimized for Dutch and
English technical term extraction. Extracted keywords are only promoted to must-mention
when they can be deterministically anchored in the code context (diff, commit messages,
file paths), preventing hallucinated terms from affecting verification.

Both sources are merged by `EvidenceKeywordAugmenter`, which deduplicates against
existing evidence facts and preserves stable ordering. The system fails open: if the
dictionary file is missing or the LLM extraction fails, generation continues with
the deterministic baseline.

### Rollback

Disable any flag to revert to previous behavior instantly. No data migration required.

### Acceptance gates

- All existing ReportE2E scenarios must pass (section coverage, hallucinations, Dutch prose)
- Evidence-coverage metric ≥ existing fact recall baseline
- No regression in fact recall across repeated runs

## Report Template

The final report is rendered from a single default markdown template bundled at
`src/main/resources/report-template.md`. The template uses named `{{placeholder}}`
tokens (e.g. `{{tickets_body}}`, `{{risk_score}}`) that are replaced with
deterministic section content produced by `ReportSections`.

**Key classes:**

- `ReportTemplateRenderer` — loads the template from classpath and performs strict
  placeholder replacement. Fails fast if any placeholder is unresolved or if a
  supplied key does not match any template token.
- `ReportTemplateValidator` — validates the rendered markdown before it is written
  to disk: all required headings must be present, in the correct order, and each
  must appear exactly once.
- `ReportSections` — deterministic, side-effect-free section body builders that
  supply content for template slots without embedding headings.

**Customizing the template:** edit `src/main/resources/report-template.md`. All
`{{placeholder}}` names must match the keys populated by `ReportGenerator`. Adding
or removing a placeholder requires a corresponding code change in
`ReportGenerator.assembleMarkdown()`. The validator will catch structural drift at
runtime.

## Narrative Grounding (Legacy)

Without evidence-first, narrative generation sends enriched context to the LLM including
changed file paths, commit messages, agent vote reasoning, and a truncated diff excerpt
(capped at 4000 characters). The narrative prompt requires the LLM to reference specific
artifacts from the input.

## Main → Public Migration

The `public` branch is a PII-free release of the codebase with renamed packages
(`nl.example` → `nl.example`) and anonymized identifiers. A migration script automates
porting changes from `main` to `public`.

### Quick start

```bash
# Dry-run: preview what would be migrated (no files changed)
bash scripts/migrate-main-to-public.sh --commit-range HEAD~1..HEAD --dry-run

# Migrate the last 3 commits, review on a feature branch
bash scripts/migrate-main-to-public.sh --commit-range HEAD~3..HEAD

# Migrate specific commits with auto-merge into public
bash scripts/migrate-main-to-public.sh --commits abc1234,def5678 --auto-merge

# Custom feature branch name
bash scripts/migrate-main-to-public.sh --commit-range HEAD~1..HEAD --feature-name report-template
```

### What it does

1. Collects changed files from the specified commit range on `main`.
2. Creates a feature branch from `public`.
3. Copies each file with path and content transformations applied:
   - Package paths: `nl/example` → `nl/example`
   - Package/import declarations: `nl.example` → `nl.example`
   - Ticket prefixes, email domains, product names, team labels (see `scripts/public-migration-rules.env`)
4. Validates no forbidden tokens remain (`nl.example`, `PROJ-`, `@example.nl`, etc.).
5. Runs the full Maven test suite.
6. Commits and optionally merges into `public`.

### Options

| Option | Description |
|--------|-------------|
| `--source-branch` | Source branch (default: `main`) |
| `--target-branch` | Target branch (default: `public`) |
| `--commit-range` | Git commit range (e.g. `HEAD~3..HEAD`) |
| `--commits` | Comma-separated commit hashes |
| `--feature-name` | Custom feature branch suffix |
| `--rules-file` | Path to substitution rules (default: `scripts/public-migration-rules.env`) |
| `--auto-merge` | Merge feature branch into target after gates pass |
| `--skip-tests` | Skip Maven test gates |
| `--dry-run` | Preview operations without writing files |

### Adding substitution rules

Edit `scripts/public-migration-rules.env` to add new token mappings. Path rules
use the `PATH:` prefix; all other lines are content substitution rules applied via `sed`.

### Smoke tests

```bash
bash scripts/test-migrate-main-to-public.sh
```

## Project Structure

See `docs/specs/cli-tool-v1-spec.md` for the full specification.
