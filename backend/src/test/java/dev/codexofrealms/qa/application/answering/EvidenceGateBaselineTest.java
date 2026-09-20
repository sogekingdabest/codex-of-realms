package dev.codexofrealms.qa.application.answering;

import static org.assertj.core.api.Assertions.assertThat;

import dev.codexofrealms.lore.RetrievedEvidence;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class EvidenceGateBaselineTest {

    @Test
    void classifiesTheVersionedSpanishBaselineBeforeAnyModelCall() throws Exception {
        EvidenceGate gate = new EvidenceGate(TestQaFixtures.properties());
        var baseline = JsonMapper.builder().build().readTree(Files.readString(
            repositoryPath("demo/evaluation/baseline.json"), StandardCharsets.UTF_8
        ));

        for (var evaluationCase : baseline.get("cases")) {
            String expected = evaluationCase.get("expectedOutcome").asString();
            if ("FORBIDDEN".equals(expected)) continue;
            String actor = evaluationCase.get("actor").asString();
            var decision = gate.evaluate(
                evaluationCase.get("question").asString(), visibleEvidence(actor)
            );
            if (decision.sufficient()) decision = gate.screenPassages(evaluationCase.get("question").asString(), decision.evidence());
            assertThat(decision.sufficient())
                .as(evaluationCase.get("id").asString())
                .isEqualTo("ANSWERED".equals(expected));
        }
    }

    private static List<RetrievedEvidence> visibleEvidence(String actor) throws Exception {
        List<String> paths = new ArrayList<>(List.of(
            "demo/lore/public/01-el-meridiano-y-lumbrevela.md",
            "demo/lore/public/02-personas-facciones-y-objetos.md",
            "demo/lore/public/03-rutas-y-vida-civica.md"
        ));
        if ("gm_ines".equals(actor)) {
            paths.add("demo/lore/gm-only/01-la-deuda-de-la-aguja.md");
            paths.add("demo/lore/gm-only/02-el-pacto-del-velo.md");
            paths.add("demo/lore/spoilers/01-el-recuerdo-de-nara.md");
            paths.add("demo/lore/spoilers/02-la-campana-de-vidrio.md");
        } else if ("player_tala".equals(actor)) {
            paths.add("demo/lore/spoilers/01-el-recuerdo-de-nara.md");
            paths.add("demo/lore/spoilers/02-la-campana-de-vidrio.md");
        }
        List<RetrievedEvidence> evidence = new ArrayList<>();
        for (int index = 0; index < paths.size(); index++) {
            String content = Files.readString(repositoryPath(paths.get(index)), StandardCharsets.UTF_8);
            evidence.add(TestQaFixtures.evidence(index + 1, 0.90, content));
        }
        return evidence;
    }

    private static Path repositoryPath(String relativePath) {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path repositoryRoot = Files.isDirectory(workingDirectory.resolve("demo"))
            ? workingDirectory
            : workingDirectory.getParent();
        return repositoryRoot.resolve(relativePath);
    }
}
