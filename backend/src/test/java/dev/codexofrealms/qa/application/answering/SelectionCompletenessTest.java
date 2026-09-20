package dev.codexofrealms.qa.application.answering;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;
class SelectionCompletenessTest {
    @Test void detectsMissingQuantityEvenWhenBothSourcesAreCited() {
        var permission=TestQaFixtures.evidence(1,.9,"Nara abre Ámbar con el sello de Iria.");
        var amount=TestQaFixtures.evidence(2,.9,"Li debe pagar 12 monedas en Ón.");
        var distractor=TestQaFixtures.evidence(3,.9,"Bo debe pagar 24 monedas.");
        String question="¿Qué necesita Nara para abrir Ámbar y cuántas monedas debe pagar Li?";
        assertThat(SelectionCompleteness.parts(question)).hasSize(2);
        var offered=List.of(permission,amount,distractor);
        assertThat(SelectionCompleteness.missing(question,offered,List.of(permission,distractor)))
            .contains("cuántas monedas debe pagar Li?");
        assertThat(SelectionCompleteness.missing(question,offered,List.of(permission,amount))).isEmpty();
    }
    @Test void supportsSpelledNumbersAndDoesNotSplitEntityConjunctions() {
        var amount=TestQaFixtures.evidence(1,.9,"Li debe pagar doce monedas.");
        assertThat(SelectionCompleteness.missing("¿Cuántas monedas debe pagar Li?",List.of(amount),List.of(amount))).isEmpty();
        assertThat(SelectionCompleteness.parts("¿Dónde viven Li y Bo?")).hasSize(1);
        assertThat(SelectionCompleteness.parts("¿Qué hace Li y dónde vive Bo?")).hasSize(2);
    }
    @Test void checksEveryPartWithoutConfusingANameWithASubstring() {
        var actual=TestQaFixtures.evidence(1,.9,"Li paga 12 monedas.");
        var distractor=TestQaFixtures.evidence(2,.9,"Lina paga 12 monedas.");
        assertThat(SelectionCompleteness.missing("¿Cuántas monedas paga Li?",List.of(actual,distractor),List.of(distractor))).isNotEmpty();
    }
}
