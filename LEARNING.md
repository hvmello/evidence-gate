# Aprendizados

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
