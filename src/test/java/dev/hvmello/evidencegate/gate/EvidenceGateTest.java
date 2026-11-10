package dev.hvmello.evidencegate.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hvmello.evidencegate.domain.Classification;
import dev.hvmello.evidencegate.domain.Classification.Citation;
import dev.hvmello.evidencegate.domain.Classification.Intent;
import dev.hvmello.evidencegate.domain.Conversation;
import dev.hvmello.evidencegate.domain.Conversation.Message;
import dev.hvmello.evidencegate.domain.Conversation.Speaker;
import dev.hvmello.evidencegate.gate.EvidenceGate.Verdict;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceGateTest {

    private final EvidenceGate gate = new EvidenceGate();

    private final Redactor.Redacted redacted = new Redactor().redact(new Conversation("s", true, "Ana", "", List.of(
            new Message(1, Speaker.STAFF, "Podemos manter o horário de terça?"),
            new Message(2, Speaker.PATIENT, "Quero continuar o tratamento neste mês."),
            new Message(3, Speaker.PATIENT, "A Ana vai viajar na próxima semana."))));

    private Verdict verify(Citation... citations) {
        return gate.verify(new Classification(Intent.CONTINUE, List.of(citations), false), redacted);
    }

    private static Citation valid() {
        return new Citation(2, "Quero continuar o tratamento");
    }

    private static Verdict.Mismatch mismatch(String code) {
        return new Verdict.Mismatch(code);
    }

    @Test
    void acceptsAVerbatimQuoteAndTakesTheSpeakerFromTheRecord() {
        var verdict = (Verdict.Verified) verify(valid());

        assertThat(verdict.citations().getFirst().speaker()).isEqualTo(Speaker.PATIENT);
        assertThat(verdict.citations().getFirst().messageId()).isEqualTo(2);
    }

    @Test
    void rejectsAnEmptyQuote() {
        assertThat(verify(new Citation(2, ""))).isEqualTo(mismatch("QUOTE_TOO_SHORT"));
    }

    @Test
    void rejectsANullQuote() {
        assertThat(verify(new Citation(2, null))).isEqualTo(mismatch("QUOTE_TOO_SHORT"));
    }

    @Test
    void rejectsAQuoteOfOneCharacter() {
        assertThat(verify(new Citation(2, "Q"))).isEqualTo(mismatch("QUOTE_TOO_SHORT"));
    }

    @Test
    void placeholdersDoNotCountAsWords() {
        assertThat(verify(new Citation(3, "A [PATIENT_NAME] vai"))).isEqualTo(mismatch("QUOTE_TOO_SHORT"));
    }

    @Test
    void acceptsAQuoteThatContainsAPlaceholderBecauseTheModelSawIt() {
        assertThat(verify(new Citation(3, "A [PATIENT_NAME] vai viajar na próxima")))
                .isInstanceOf(Verdict.Verified.class);
    }

    @Test
    void rejectsAFabricatedQuote() {
        assertThat(verify(new Citation(2, "Não quero mais voltar"))).isEqualTo(mismatch("QUOTE_NOT_IN_MESSAGE"));
    }

    @Test
    void rejectsARealQuoteAttributedToTheWrongMessage() {
        assertThat(verify(new Citation(3, "Quero continuar o tratamento"))).isEqualTo(mismatch("QUOTE_NOT_IN_MESSAGE"));
    }

    @Test
    void rejectsAQuoteSpanningTwoMessages() {
        assertThat(verify(new Citation(2, "neste mês. A [PATIENT_NAME] vai viajar")))
                .isEqualTo(mismatch("QUOTE_NOT_IN_MESSAGE"));
    }

    @Test
    void rejectsAnUnknownMessage() {
        assertThat(verify(new Citation(99, "Quero continuar o tratamento"))).isEqualTo(mismatch("UNKNOWN_MESSAGE"));
    }

    @Test
    void rejectsTheWholeResultWhenAFabricationHidesPastTheCitationLimit() {
        assertThat(verify(valid(), valid(), valid(), new Citation(2, "Não quero mais voltar")))
                .isEqualTo(mismatch("QUOTE_NOT_IN_MESSAGE"));
    }

    @Test
    void keepsAtMostThreeCitations() {
        var verdict = (Verdict.Verified) verify(valid(), valid(), valid(), valid());

        assertThat(verdict.citations()).hasSize(3);
    }

    @Test
    void staffWordsAloneCannotSupportThePatientsIntent() {
        assertThat(verify(new Citation(1, "Podemos manter o horário"))).isEqualTo(mismatch("NO_PATIENT_CITATION"));
    }

    @Test
    void toleratesTypographicDifferences() {
        assertThat(verify(new Citation(2, "Quero  continuar​ o\ntratamento"))).isInstanceOf(Verdict.Verified.class);
    }

    @Test
    void cutPlaceholdersDoNotCountAsWords() {
        var withPlaceholders = new Redactor().redact(new Conversation("s", true, "", "", List.of(
                new Message(1, Speaker.PATIENT, "cpf 123.456.789-09 sim fone 98123-4567"))));

        assertThat(gate.verify(new Classification(Intent.CONTINUE, List.of(new Citation(1, "CPF] sim fone [PHONE")), false),
                withPlaceholders)).isEqualTo(mismatch("QUOTE_TOO_SHORT"));
    }

    @Test
    void rejectsAQuoteThatCutsWordsInHalf() {
        assertThat(verify(new Citation(2, "uero continuar o tr"))).isEqualTo(mismatch("QUOTE_NOT_IN_MESSAGE"));
    }

    @Test
    void theCapCannotLeaveOnlyStaffCitations() {
        var staff = new Citation(1, "Podemos manter o horário");

        var verdict = (Verdict.Verified) verify(staff, staff, staff, valid());

        assertThat(verdict.citations()).hasSize(3);
        assertThat(verdict.citations().getFirst().speaker()).isEqualTo(Speaker.PATIENT);
    }

    @Test
    void aMissingIntentIsAMismatch() {
        assertThat(gate.verify(new Classification(null, List.of(valid()), false), redacted))
                .isEqualTo(mismatch("NO_INTENT"));
    }

    @Test
    void anAbstentionIsNotAMismatch() {
        assertThat(gate.verify(new Classification(Intent.UNDECIDED, List.of(), true), redacted))
                .isInstanceOf(Verdict.Insufficient.class);
    }

    @Test
    void aClaimWithNoCitationsIsAMismatch() {
        assertThat(verify()).isEqualTo(mismatch("NO_CITATIONS"));
    }

    @Test
    void acceptsExactlyThreeWordsAndRejectsTwo() {
        assertThat(verify(new Citation(2, "Quero continuar o"))).isInstanceOf(Verdict.Verified.class);
        assertThat(verify(new Citation(2, "Quero continuar"))).isEqualTo(mismatch("QUOTE_TOO_SHORT"));
    }

    @Test
    void acceptsAQuoteAtTheStartAndAtTheEndOfTheMessage() {
        assertThat(verify(new Citation(2, "Quero continuar o"))).isInstanceOf(Verdict.Verified.class);
        assertThat(verify(new Citation(2, "tratamento neste mês."))).isInstanceOf(Verdict.Verified.class);
    }

    @Test
    void rejectsAQuoteInsideLongerWordsAtBothEdges() {
        var conversation = new Redactor().redact(new Conversation("s", true, "", "", List.of(
                new Message(1, Speaker.PATIENT, "xquero continuar o tratamento e sigo com a terapia hoje"),
                new Message(2, Speaker.PATIENT, "sigo com a terapiax hoje"))));

        assertThat(gate.verify(new Classification(Intent.CONTINUE, List.of(new Citation(1, "quero continuar o")), false),
                conversation)).isEqualTo(mismatch("QUOTE_NOT_IN_MESSAGE"));
        assertThat(gate.verify(new Classification(Intent.CONTINUE, List.of(new Citation(2, "sigo com a terapia")), false),
                conversation)).isEqualTo(mismatch("QUOTE_NOT_IN_MESSAGE"));
    }

    @Test
    void findsAWholeWordMatchThatComesAfterAMatchInsideAWord() {
        var conversation = new Redactor().redact(new Conversation("s", true, "", "", List.of(
                new Message(1, Speaker.PATIENT, "xsigo com a terapia e sigo com a terapia"))));

        assertThat(gate.verify(new Classification(Intent.CONTINUE, List.of(new Citation(1, "sigo com a terapia")), false),
                conversation)).isInstanceOf(Verdict.Verified.class);
    }
}
