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
        "{NAME}, sua d e p r e s s ã o passou. | BLOCKED_TERM",
        "{NAME}, D.E.P.R.E.S.S.Ã.O passou. | BLOCKED_TERM",
        "{NAME}, sua a n s i e d a d e melhorou. | BLOCKED_TERM",
        "{NAME}, pague com p-i-x. | BLOCKED_TERM",
        "{NAME}, pague com P I X hoje. | BLOCKED_TERM",
        "{NAME}, o d_i_a_g_n_ó_s_t_i_c_o saiu. | BLOCKED_TERM",
        "{NAME}, ú l t i m a  c h a n c e de voltar. | BLOCKED_TERM",
        "{NAME}, temos d . e . s . c . o . n . t . o hoje. | BLOCKED_TERM",
        "{NAME}, ultimachance de voltar. | BLOCKED_TERM",
    })
    void blocks(String draft, String code) {
        assertThat(filter.violation(draft)).contains(code);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "{NAME}, saí depressa ontem.",
        "{NAME}, vi um pixel na tela.",
        "{NAME}, em 2026 voltamos.",
        "{NAME}, saí d e p r e s s a ontem.",
        "{NAME}, escolha a opção A, B ou C.",
        "{NAME}, as opções A, B e C seguem abertas.",
        "{NAME}, sinto mais calma hoje.",
        "{NAME}, a e i o u.",
    })
    void doesNotBlockInnocentWords(String draft) {
        assertThat(filter.violation(draft)).isEmpty();
    }

    @Test
    void zeroWidthCharacterBetweenSeparatedLettersStillBlocks() {
        assertThat(filter.violation("{NAME}, sua d​ e p r e s s ã o passou.")).contains("BLOCKED_TERM");
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
