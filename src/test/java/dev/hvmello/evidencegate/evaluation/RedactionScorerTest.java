package dev.hvmello.evidencegate.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hvmello.evidencegate.evaluation.RedactionScorer.Label;
import dev.hvmello.evidencegate.evaluation.RedactionScorer.Outcome;
import org.junit.jupiter.api.Test;

class RedactionScorerTest {

    private static final String PREFIX = "Meu número é ";
    private static final String SUFFIX = " e pode ligar.";

    private static RedactionScorer.Score score(String redacted, String expected) {
        return RedactionScorer.score(PREFIX, "98123-4567", SUFFIX, redacted, expected);
    }

    @Test
    void fullCatchIsCaughtAndCorrectlyLabelled() {
        var score = score("Meu número é [PHONE] e pode ligar.", "[PHONE]");

        assertThat(score.outcome()).isEqualTo(Outcome.CAUGHT);
        assertThat(score.label()).isEqualTo(Label.CORRECT);
        assertThat(score.leaked()).isFalse();
    }

    @Test
    void fullLeakIsReported() {
        var score = score("Meu número é 98123-4567 e pode ligar.", "[PHONE]");

        assertThat(score.outcome()).isEqualTo(Outcome.LEAK_FULL);
        assertThat(score.leaked()).isTrue();
    }

    @Test
    void leftoverDigitsAreAPartialLeak() {
        var score = score("Meu número é [PHONE]4567 e pode ligar.", "[PHONE]");

        assertThat(score.outcome()).isEqualTo(Outcome.LEAK_PARTIAL);
        assertThat(score.leftover()).isEqualTo("4567");
    }

    @Test
    void leftoverNameFragmentIsAPartialLeak() {
        var score = RedactionScorer.score("Fale com ", "Maria Silva", " hoje.", "Fale com [PATIENT_NAME] Silva hoje.",
                "[PATIENT_NAME]");

        assertThat(score.outcome()).isEqualTo(Outcome.LEAK_PARTIAL);
        assertThat(score.leftover()).isEqualTo("Silva");
    }

    @Test
    void twoPlaceholdersThatCoverTheIdentifierCountAsCaught() {
        var score = score("Meu número é [PHONE] [PHONE] e pode ligar.", "[PHONE]");

        assertThat(score.outcome()).isEqualTo(Outcome.CAUGHT);
        assertThat(score.label()).isEqualTo(Label.CORRECT);
    }

    @Test
    void aWrongPlaceholderIsCaughtButMislabelled() {
        var score = score("Meu número é [CPF] e pode ligar.", "[PHONE]");

        assertThat(score.outcome()).isEqualTo(Outcome.CAUGHT);
        assertThat(score.label()).isEqualTo(Label.WRONG);
    }

    @Test
    void aMixOfRightAndWrongPlaceholdersIsMislabelled() {
        assertThat(score("Meu número é [PHONE] [CPF] e pode ligar.", "[PHONE]").label()).isEqualTo(Label.WRONG);
    }

    @Test
    void aClassWithoutARuleHasNoLabel() {
        var score = score("Meu número é [PHONE] e pode ligar.", null);

        assertThat(score.outcome()).isEqualTo(Outcome.CAUGHT);
        assertThat(score.label()).isEqualTo(Label.NOT_APPLICABLE);
    }

    @Test
    void overRedactionThatDamagesTheFillerIsALeakFlaggedFillerDamaged() {
        var score = score("Meu [PHONE]", "[PHONE]");

        assertThat(score.outcome()).isEqualTo(Outcome.FILLER_DAMAGED);
        assertThat(score.leaked()).isTrue();
    }

    @Test
    void aDamagedSuffixIsFillerDamagedEvenWhenTheIdentifierIsGone() {
        assertThat(score("Meu número é [PHONE] e pode", "[PHONE]").outcome()).isEqualTo(Outcome.FILLER_DAMAGED);
    }

    @Test
    void whitespaceAndPunctuationChangesAloneAreNotALeak() {
        var score = score("Meu  número é... [PHONE]  e pode ligar", "[PHONE]");

        assertThat(score.outcome()).isEqualTo(Outcome.CAUGHT);
    }

    @Test
    void skeletonKeepsLettersAndDigitsOnly() {
        assertThat(RedactionScorer.skeleton("Zé, 98123-4567!")).isEqualTo("Zé981234567");
    }
}
