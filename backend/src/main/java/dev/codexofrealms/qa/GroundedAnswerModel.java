package dev.codexofrealms.qa;

public interface GroundedAnswerModel {

    GroundedAnswerDraft generate(GroundedAnswerRequest request);

    ModelDescriptor descriptor();
}
