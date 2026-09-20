package dev.codexofrealms.qa.web;

import dev.codexofrealms.qa.LoreAnswer;
import dev.codexofrealms.qa.application.answering.QuestionAnsweringService;
import dev.codexofrealms.realm.AuthenticatedUser;
import dev.codexofrealms.realm.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/realms/{realmId}/questions")
class LoreQuestionController {

    private final QuestionAnsweringService service;

    LoreQuestionController(QuestionAnsweringService service) {
        this.service = service;
    }

    @PostMapping
    LoreAnswer answer(
        @CurrentUser AuthenticatedUser user,
        @PathVariable UUID realmId,
        @Valid @RequestBody QuestionRequest request
    ) {
        return service.answer(realmId, user.id(), request.question());
    }

    record QuestionRequest(@NotBlank @Size(max = 1000) String question) {
    }
}
