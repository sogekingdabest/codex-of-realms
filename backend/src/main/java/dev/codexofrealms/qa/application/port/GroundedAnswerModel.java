package dev.codexofrealms.qa.application.port;

public interface GroundedAnswerModel {

    GroundedAnswerDraft generate(GroundedAnswerRequest request);

    ModelDescriptor descriptor();
}
