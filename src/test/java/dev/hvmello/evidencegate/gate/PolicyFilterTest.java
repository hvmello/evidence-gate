package dev.hvmello.evidencegate.gate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PolicyFilterTest {

    private final PolicyFilter filter = new PolicyFilter();

    @Test
    void allowsAPlainDraft() {
        assertThat(filter.violation("Olá, {NAME}. Como prefere seguir?")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Olá. Como prefere seguir? | NAME_MARKER_MISSING",
        "{NAME}, tudo bem? Podemos falar? | TOO_MANY_QUESTIONS",
        "{NAME}, seu cpf [CPF] consta aqui. | PLACEHOLDER_LEAK",
        "{NAME}, sua ansiedade melhorou. | BLOCKED_TERM",
        "{NAME}, última chance de voltar. | BLOCKED_TERM",
        "{NAME}, temos DESCONTO hoje. | BLOCKED_TERM",
        "{NAME}, última  chance de voltar. | BLOCKED_TERM",
        "{NAME}, pague com PIX. | BLOCKED_TERM",
        "{NAME}, veja https://exemplo.com agora. | CONTACT_DATA",
        "{NAME}, ligue (62) 98123-4567 hoje. | CONTACT_DATA",
        "{NAME}, o antidepressivo chegou. | BLOCKED_TERM",
        "{NAME}, autodiagnóstico não ajuda. | BLOCKED_TERM",
        "{NAME}, caso assintomático. | BLOCKED_TERM",
        "{NAME}, _ansiedade_ passou. | BLOCKED_TERM",
        "{NAME}, __depressão__ passou. | BLOCKED_TERM",
        "{NAME}, pague com _pix_ hoje. | BLOCKED_TERM",
        "｛NAME｝, tudo bem. | NAME_MARKER_MISSING",
    })
    void blocks(String draft, String code) {
        assertThat(filter.violation(draft)).contains(code);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{NAME}, saí depressa ontem.",
        "{NAME}, vi um pixel na tela.",
        "{NAME}, em 2026 voltamos.",
    })
    void doesNotBlockInnocentWords(String draft) {
        assertThat(filter.violation(draft)).isEmpty();
    }

    @Test
    void fullWidthQuestionMarksAreCounted() {
        assertThat(filter.violation("{NAME}, tudo bem？ Podemos falar？")).contains("TOO_MANY_QUESTIONS");
    }

    @Test
    void aLineBreakCannotSplitABlockedPhrase() {
        assertThat(filter.violation("{NAME}, última\nchance de voltar.")).contains("BLOCKED_TERM");
    }

    @Test
    void anInvisibleCharacterCannotHideAPlaceholder() {
        assertThat(filter.violation("{NAME}, seu [C​PF] consta.")).contains("PLACEHOLDER_LEAK");
    }

    @Test
    void zeroWidthCharactersDoNotHideABlockedTerm() {
        assertThat(filter.violation("{NAME}, sua de​pressão melhorou.")).contains("BLOCKED_TERM");
    }

    @Test
    void blocksAnOverlongDraftAndAllowsOneAtTheLimit() {
        var atLimit = "{NAME}" + "a".repeat(PolicyFilter.MAX_LENGTH - 6);

        assertThat(filter.violation(atLimit)).isEmpty();
        assertThat(filter.violation(atLimit + "a")).contains("TOO_LONG");
    }
}
