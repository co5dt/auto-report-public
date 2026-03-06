package nl.example.qualityreport.llm;

/**
 * Detects repetition loops in streaming LLM output by checking whether the
 * tail of the accumulated text contains a repeating block.
 *
 * <p>Four-tier detection:
 * <ul>
 *   <li><b>Tier 1 — Short exact blocks</b> (≤ {@code windowSize/minRepeats}): require
 *       {@code minRepeats} exact consecutive copies within the fixed tail window.
 *   <li><b>Tier 2 — Large exact blocks</b> (&gt; {@code windowSize/minRepeats}): require 2
 *       exact consecutive copies, scanning the full accumulated text.
 *   <li><b>Tier 3 — Half-window near-duplicate</b>: compare adjacent equal-length blocks from
 *       the tail using Ratcliff/Obershelp similarity. Multiple half-sizes are probed to avoid
 *       alignment-dependent misses. Threshold: {@code SIMILARITY_THRESHOLD} (0.95).
 *   <li><b>Tier 4 — Paragraph-level near-duplicate</b>: split the tail into paragraphs
 *       (on {@code \n\n}) and check if the last {@code PARA_WINDOW} consecutive paragraphs are
 *       all similar to each other (average similarity ≥ {@code PARA_SIMILARITY_THRESHOLD}).
 *       This catches multi-variant cycles (e.g. ABCDABCD) where each paragraph shares structure
 *       but differs in one class/file name — a pattern the half-window check misses because
 *       no single window split aligns with the cycle period.
 * </ul>
 */
final class RepetitionDetector {

    static final int DEFAULT_WINDOW_SIZE = 800;
    static final int DEFAULT_MIN_BLOCK_SIZE = 40;
    static final int DEFAULT_MIN_REPEATS = 3;
    static final double SIMILARITY_THRESHOLD = 0.95;
    static final double PARA_SIMILARITY_THRESHOLD = 0.85;
    static final int PARA_WINDOW = 4;

    private static final int MIN_NEAR_DUP_HALF = 120;

    private final int windowSize;
    private final int minBlockSize;
    private final int minRepeats;

    RepetitionDetector() {
        this(DEFAULT_WINDOW_SIZE, DEFAULT_MIN_BLOCK_SIZE, DEFAULT_MIN_REPEATS);
    }

    RepetitionDetector(int windowSize, int minBlockSize, int minRepeats) {
        this.windowSize = windowSize;
        this.minBlockSize = minBlockSize;
        this.minRepeats = minRepeats;
    }

    /**
     * @return the repeated fragment if a loop is detected, or {@code null} if no loop.
     */
    String detectLoop(CharSequence accumulated) {
        int len = accumulated.length();

        // Standard check: short blocks (≤ windowSize/minRepeats) within a fixed tail window.
        if (len >= windowSize) {
            String tail = accumulated.subSequence(len - windowSize, len).toString();
            int maxBlock = windowSize / minRepeats;
            for (int blockLen = minBlockSize; blockLen <= maxBlock; blockLen++) {
                String candidate = tail.substring(tail.length() - blockLen);
                if (countTailRepeats(tail, candidate) >= minRepeats) {
                    return truncateFragment(candidate, 120);
                }
            }
        }

        // Large-block check: exact 2-copy repeat for blocks larger than windowSize/minRepeats.
        int maxLargeBlock = len / 2;
        if (maxLargeBlock > windowSize / minRepeats) {
            String full = accumulated.toString();
            for (int blockLen = (windowSize / minRepeats) + 1; blockLen <= maxLargeBlock; blockLen++) {
                String candidate = full.substring(len - blockLen);
                if (countTailRepeats(full, candidate) >= 2) {
                    return truncateFragment(candidate, 120);
                }
            }
        }

        // Tier 3: half-window near-duplicate (Ratcliff/Obershelp). Compares adjacent equal-length
        // blocks from the tail. Multiple half-sizes are probed to handle alignment variance.
        if (len >= MIN_NEAR_DUP_HALF * 2) {
            int maxHalf = Math.min(len / 2, MIN_NEAR_DUP_HALF * 4);
            for (int half = MIN_NEAR_DUP_HALF; half <= maxHalf; half += MIN_NEAR_DUP_HALF / 2) {
                String second = accumulated.subSequence(len - half, len).toString();
                String first =
                        accumulated.subSequence(len - half * 2, len - half).toString();
                if (similarity(first, second) >= SIMILARITY_THRESHOLD) {
                    return truncateFragment(second, 120);
                }
            }
        }

        // Tier 4: paragraph-level near-duplicate. Split on \n\n, take last PARA_WINDOW paragraphs,
        // and check if average pairwise similarity (each vs the first) exceeds threshold.
        String result = checkParagraphSimilarity(accumulated);
        if (result != null) {
            return result;
        }

        return null;
    }

    private String checkParagraphSimilarity(CharSequence accumulated) {
        String text = accumulated.toString();
        String[] parts = text.split("\n\n");
        if (parts.length < PARA_WINDOW) return null;

        int count = 0;
        String[] tail = new String[PARA_WINDOW];
        for (int i = parts.length - 1; i >= 0 && count < PARA_WINDOW; i--) {
            String trimmed = parts[i].strip();
            if (!trimmed.isEmpty() && trimmed.length() >= DEFAULT_MIN_BLOCK_SIZE) {
                tail[PARA_WINDOW - 1 - count] = trimmed;
                count++;
            }
        }
        if (count < PARA_WINDOW) return null;

        String reference = tail[0];
        double totalSim = 0;
        for (int i = 1; i < PARA_WINDOW; i++) {
            totalSim += similarity(reference, tail[i]);
        }
        double avgSim = totalSim / (PARA_WINDOW - 1);
        if (avgSim >= PARA_SIMILARITY_THRESHOLD) {
            return truncateFragment(tail[PARA_WINDOW - 1], 120);
        }
        return null;
    }

    /**
     * Ratcliff/Obershelp similarity: 2 * (matching chars) / (total chars in both strings).
     * Matching chars are found recursively via the longest common substring.
     */
    static double similarity(String a, String b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        int matched = matchingChars(a, 0, a.length(), b, 0, b.length());
        return 2.0 * matched / (a.length() + b.length());
    }

    private static int matchingChars(String a, int a0, int a1, String b, int b0, int b1) {
        int bestLen = 0, bestA = a0, bestB = b0;
        for (int i = a0; i < a1; i++) {
            for (int j = b0; j < b1; j++) {
                int len = 0;
                while (i + len < a1 && j + len < b1 && a.charAt(i + len) == b.charAt(j + len)) {
                    len++;
                }
                if (len > bestLen) {
                    bestLen = len;
                    bestA = i;
                    bestB = j;
                }
            }
        }
        if (bestLen == 0) return 0;
        int left = matchingChars(a, a0, bestA, b, b0, bestB);
        int right = matchingChars(a, bestA + bestLen, a1, b, bestB + bestLen, b1);
        return bestLen + left + right;
    }

    private int countTailRepeats(String text, String block) {
        int count = 0;
        int pos = text.length();
        while (pos >= block.length()) {
            String segment = text.substring(pos - block.length(), pos);
            if (!segment.equals(block)) break;
            count++;
            pos -= block.length();
        }
        return count;
    }

    private static String truncateFragment(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
