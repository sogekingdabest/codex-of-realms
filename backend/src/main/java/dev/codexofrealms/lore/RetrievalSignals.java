package dev.codexofrealms.lore;
import java.util.List;
/** Internal diagnostics; cosine similarity is never replaced by a fusion score. */
public record RetrievalSignals(int vectorRank, int lexicalRank, double fusionScore,
        List<String> matchedTerms, List<String> missingTerms, boolean hybrid) {
    public RetrievalSignals { matchedTerms = List.copyOf(matchedTerms); missingTerms = List.copyOf(missingTerms); }
    public static RetrievalSignals vectorOnly(int rank) {
        return new RetrievalSignals(rank, 0, 0, List.of(), List.of(), false);
    }
    public double lexicalCoverage() {
        int total = matchedTerms.size() + missingTerms.size();
        return total == 0 ? 0 : (double) matchedTerms.size() / total;
    }
    public boolean sufficient(double threshold) {
        return hybrid && lexicalRank > 0 && matchedTerms.size() >= 2 && lexicalCoverage() >= threshold;
    }
}
