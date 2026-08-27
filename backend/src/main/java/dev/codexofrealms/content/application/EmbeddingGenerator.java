package dev.codexofrealms.content.application;

import java.util.List;

public interface EmbeddingGenerator {

    List<float[]> embed(List<String> texts);
}
