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

## Palavra de uma letra colada no termo espaçado (#11, revisão)

Uma palavra real de uma letra (`o`, `a`, `e`, `é`, `à`) ao lado do termo espaçado entra na sequência
(`faça o P I X` junta `opix`), e os termos com borda (`pix`, `crise`, `urgente`) deixavam de casar. Agora a
sequência inteira é conferida, e também sem essas letras nas pontas. Só `a`, `e` e `o` são aparadas (acentos
já foram removidos), e uma forma aparada não vale dentro de palavra inocente: `d e p r e s s a` continua
permitido, embora `depress` apareça ao aparar o `a`. Aparar qualquer subtrecho seria mais simples, mas
bloquearia `depressa`.

## Mutation testing: o que a cobertura de linhas não mede

Cobertura de linhas diz que uma linha foi executada durante os testes, não que algum teste falharia se a linha
estivesse errada. Um teste sem `assert` dá 100 % de cobertura e não protege nada.

O PIT altera o código de produção de propósito (troca `>=` por `>`, remove um `if`, inverte um retorno). Cada
alteração é um mutante. Se algum teste falha, o mutante foi morto; se todos passam, ele sobreviveu, e isso aponta uma
asserção que falta. O score é mortos dividido por gerados.

- Os limites (`MIN_WORDS` exatamente 3, nome de uma letra, citação no começo ou no fim do texto) são onde os
  sobreviventes aparecem, porque o teste "feliz" fica longe da borda.
- Mutante equivalente é o que não muda comportamento; não se escreve teste para ele, registra-se o motivo.
- `pitest:mutationCoverage` chamado direto não recompila os testes: rode `test-compile` antes, senão o PIT mede
  testes velhos.
- O score só vale para o escopo e para o conjunto de mutadores escolhidos (aqui, pacote `gate` e `DEFAULTS`).

## Recall de redação: medir no conjunto certo (#19)

Recall é a fração dos identificadores que o `Redactor` removeu: capturados dividido por total. Precisão é o
inverso: do que ele trocou por placeholder, quanto era mesmo identificador. Aqui o risco é vazar, então o número
publicado é recall. Precisão só aparece de lado, como `FILLER_DAMAGED`, quando a redação estraga a frase em volta.

- **Vazamento parcial conta como vazamento.** `4567` sobrando de `98123-4567` ainda identifica. Por isso o corte
  reduz resultado e frase a letras e dígitos e confere o que sobrou entre o prefixo e o sufixo. Qualquer sobra é
  vazamento. Na dúvida, o registro conta contra o redator.
- **O conjunto vem depois do congelamento.** Se o redator é ajustado em cima do conjunto e medido no mesmo
  conjunto, o recall sobe sem que o redator tenha melhorado, é a mesma ideia de ajustar o prompt no treino e
  medir no teste. O manifesto guarda o hash do `Redactor.java` (com fim de linha normalizado para LF, senão o
  hash muda entre Windows e Linux) e o teste falha se o arquivo mudar sem uma versão nova do conjunto.
- **Classes sem regra entram na tabela.** RG, CEP, CNS, data de nascimento e endereço vazam, e o relatório mostra
  isso como número em vez de uma frase de limitação.
- **Intervalo de Wilson.** Com 50 registros por classe, 45 acertos não são 90 % de verdade: o intervalo de 95 % vai
  de cerca de 79 % a 96 %. Wilson funciona melhor que a aproximação normal perto de 0 % e 100 %, onde o intervalo
  normal passaria de 100 % ou ficaria abaixo de 0 %.
- **Independência é processual.** Quem escreveu o gerador já viu o redator. Linguagem diferente, nenhuma leitura do
  código, variantes tiradas de especificações públicas e um teste de sobreposição com os testes existentes
  reduzem o viés, mas não o eliminam, e o relatório diz isso.
