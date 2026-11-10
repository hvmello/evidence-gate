# Aprendizados

## Por que o filtro junta só sequências de letras isoladas (#11)

Uma palavra proibida escrita com separadores (`d e p r e s s ã o`, `p-i-x`) não formava o radical e passava.
A saída óbvia, remover todo espaço do texto, cria falsos positivos entre palavras reais: `sinto mais`
viraria `sintomais`, que contém `sintoma`.

Por isso o `PolicyFilter` só junta sequências de três ou mais letras isoladas (uma letra, separador, uma
letra...), sem letra ou número colado nas pontas, e confere os termos bloqueados contra a forma junta com as
mesmas regras de borda (`d e p r e s s a` vira `depressa` e continua permitido). Cortes no meio da palavra
(`depre ssão`) ficam de fora de propósito: pegá-los exigiria juntar palavras reais.

A guarda contra falsos positivos é a suíte R6 (respostas legítimas), que precisa continuar com a mesma
contagem de bloqueios, mais os casos de `doesNotBlockInnocentWords`.
