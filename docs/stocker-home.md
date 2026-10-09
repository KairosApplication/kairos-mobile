# Home do repositor

Referência: [Home-Repositor no Figma](https://www.figma.com/design/OFMvU7yImxJFUpV7e061Pj?node-id=418-1068).

`StockerHomeView` substitui o destino provisório após autenticação e conclusão do
perfil. Usa o nome da sessão e a data local. O conteúdo rola independentemente do
menu inferior. A Home usa Montserrat SemiBold (peso 600); Alertas e Histórico
usam Montserrat nos pesos do Figma. As telas anteriores do fluxo,
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
  exemplos do repositório. Ajustes abre os acessos de conta, preferências e suporte;
  os dados da conta e a saída real ficam em Perfil. Veja [fluxo de ajustes](settings-flow.md).
- Verificar gôndola informa que a função por foto ainda está indisponível.
- Voltar nas abas retorna ao Início; voltar no Início encerra a atividade sem
  desconectar a conta. A aba selecionada é restaurada na recriação da atividade.

## Alertas e histórico de reposições

Referências: [Alertas](https://www.figma.com/design/OFMvU7yImxJFUpV7e061Pj?node-id=713-1764)
e [Histórico](https://www.figma.com/design/OFMvU7yImxJFUpV7e061Pj?node-id=713-1606).

- Alertas apresenta pendências/estoque baixo e reposições concluídas no dia.
- Histórico agrupa as reposições por data, com busca por produto ou gôndola
  sem distinguir acentos ou maiúsculas. O botão de filtro combina período
  (todo, hoje, ontem ou últimos sete dias), gôndola e ordenação. Aplicar confirma
  as escolhas, Cancelar preserva o filtro anterior e Limpar filtros também limpa
  a busca. As escolhas persistem entre abas e na recriação da atividade para
  o mesmo UID. O bundle aguarda a confirmação da identidade antes de restaurar
  a aba e os filtros. Uma conta diferente, logout ou um bundle antigo sem UID
  descarta o estado; mudanças no perfil da mesma conta preservam os filtros.
- Os registros são exemplos locais em `restockingAlerts`/`restockingHistory`,
  separados dos eventos agregados da Home. O adaptador futuro preencherá essas
  listas sem depender de recursos Android no modelo.
- As três fotos de produtos em `drawable-nodpi/restock_*.png` são os originais
  dos frames. Os novos ícones `menu_*.xml` foram convertidos dos SVGs com o
  Svg2Vector do Android Studio. O relógio selecionado inclui os dois retângulos
  brancos sobrepostos no Figma.
- O menu mantém as suas views e anima a translação de um único indicador por
  280 ms. Os quatro ícones ficam preenchidos em verde quando selecionados e usam
  transição de opacidade. Início e Ajustes reutilizam os traçados dos ícones de
  contorno nas variantes `menu_house_active` e `menu_settings_active`.
- O ícone adaptativo normal, redondo e monocromático usa o símbolo vetorial
  Kairos existente, centralizado na área segura; o fundo é verde em gradiente.

`RestockingHistoryTest` cobre busca, filtros combinados, limites de datas locais
e ordenação. `StockerHomeTest` verifica a aplicação/limpeza de filtros, navegação,
restauração e destino do indicador animado. Capturas de alertas, histórico,
resultado filtrado e ícone ficam no cache do app durante o teste.

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
O teste de restauração verifica a confirmação do UID, a preservação para a mesma
conta e o descarte ao entrar com outra conta após uma falha de restauração,
trocar de conta diretamente, sair ou receber um bundle antigo sem proprietário.
O teste de interface usa uma sessão local de exemplo e não faz
login ou logout de uma conta real.
