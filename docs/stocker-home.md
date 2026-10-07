# Home do repositor

Referência: [Home-Repositor no Figma](https://www.figma.com/design/OFMvU7yImxJFUpV7e061Pj?node-id=418-1068).

`StockerHomeView` substitui o destino provisório após autenticação e conclusão do
perfil. Usa o nome da sessão e a data local. O conteúdo rola independentemente do
menu inferior. A Home e suas abas usam Montserrat SemiBold (peso 600); as telas anteriores do fluxo,
incluindo login, cadastro e carregamento, mantêm suas fontes próprias.

Os ícones `drawable/home_*.xml` são conversões locais dos SVGs fornecidos pelo
frame, feitas com o conversor Svg2Vector do Android Studio. `home_arrow_small`
preserva o espaço transparente do componente original; a seta visível é
`home_camera_arrow`.

## Dados e ações

- `DemoStockerHomeRepository` fornece exemplos locais identificados na interface.
  Não consulta nem altera estoque real.
- `StockerHomeViewModel` carrega fora da thread da interface e descarta resultados
  de sessões anteriores. Cancela a consulta anterior e permite que a nova conta
  carregue mesmo quando a consulta antiga ignora interrupções. Oferece estados
  de carregamento, erro e nova tentativa.
- Pendências abre Alertas; Concluídas abre Histórico. Essas abas apresentam os
  exemplos do repositório. Configurações mantém os dados da conta e a saída real.
- Verificar gôndola informa que a função por foto ainda está indisponível.
- Voltar nas abas retorna ao Início; voltar no Início encerra a atividade sem
  desconectar a conta. A aba selecionada é restaurada na recriação da atividade.

## Integração futura

Foi consultado `KairosApplication/kairos-springboot`, commit
`7f421499` (29/09/2026). A API possui CRUDs de reposições, produtos em gôndolas e
associações de funcionários, mas ainda é necessário definir o contrato da Home:
contagens semanais, estados de pendências, prioridades, alertas de estoque baixo
e análises por foto.

O mobile autentica com Firebase; o backend usa sessão Spring, JSESSIONID e CSRF.
Antes de implementar um adaptador de `StockerHomeRepository`, é necessário
alinhar a autenticação, a identidade do funcionário, seu papel e o recorte da
loja. O UID Firebase não deve ser tratado como o ID numérico do backend.
Por enquanto, todos os usuários com perfil completo chegam à Home demonstrativa;
não há autorização de papel de repositor implementada nesta mudança.

## Validação

`StockerHomeTest` verifica navegação dos indicadores, restauração da aba, saída
por callback, erro/nova tentativa, estado vazio e descarte de respostas de uma
conta anterior. O teste de troca de conta mantém a consulta antiga bloqueada até
a nova concluir e rejeita qualquer publicação do resultado antigo. Um teste
adicional verifica a falha do repositório e a recuperação via retry forçado.
O teste de interface usa uma sessão local de exemplo e não faz
login ou logout de uma conta real.
