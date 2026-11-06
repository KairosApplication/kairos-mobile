# Cliente: API existente e favoritos locais

## Escopo atual

Favoritos são preferências do aparelho, separados por conta e pela origem da
API. O app guarda somente os IDs dos produtos em SharedPreferences. Favoritar
e remover favorito não fazem chamadas HTTP nem recarregam o catálogo. A lista
é reaplicada aos produtos ao carregar a Home. Os favoritos persistem ao sair
e entrar na mesma conta; limpar os dados ou desinstalar o app pode removê-los.
Não há sincronização entre aparelhos.

Ofertas, promoções, queima de estoque e etiquetas de desconto foram retiradas
da interface do cliente. A leitura de nota por Firebase AI Logic permanece no
fluxo validado pelo usuário.

Esta entrega usa a API publicada, sem novas rotas nem publicação de backend.
A proposta anterior de patch Spring foi retirada da entrega ativa. A cópia
experimental em `.integration` é ignorada pelo Git e não foi publicada.

## Contrato existente conferido em 05/11/2026

- `GET /api/v1/products/list`: catálogo usado pela Home.
- `GET /api/v1/customers/{id}/recommendations`: existe, mas exige o customerId
  correto; esse identificador não é retornado pela sessão atual do app. Não
  presumir que userId e customerId sejam iguais nem tentar enumerar IDs.
- `GET /api/v1/purchases/{id}/total`: consulta de total; não é criação de compra
  nem listagem do histórico do cliente. O contrato consultado não publica
  operações de cadastro/listagem de compras para CUSTOMER.
- `POST /api/v1/alerts/registration`: existe para funcionário/gerente; a
  configuração de segurança atual não autoriza CUSTOMER nessa operação.
  Um relato de cliente não pode ser enviado com a identidade de um funcionário.

Por isso, registro de compra/histórico e reporte de avaria continuam indicando
indisponibilidade. Não foi criada uma compra local apresentada como registro
no PostgreSQL, nem alterada a permissão do servidor. Recomendações continuam
sem habilitação enquanto não houver uma identificação própria utilizável
pelo cliente. O APK não chama mais as rotas propostas `/customer/capabilities`,
`/customer/home`, `/customer/favorites` ou `/stocker/customer-reports`.

## Arquivos principais

- `CustomerFavorites.kt`: armazenamento local por conta e serviço.
- `KairosApi.kt`: inicialização das preferências com o contexto da aplicação.
- `PublishedCustomerRepository.kt`: catálogo publicado, favorito local e
  leitura Firebase; remove dependências de rotas novas.
- `CustomerViewModel.kt`: atualiza favorito na tela sem consulta à API.
- `CustomerHomeView.kt`: favorito local disponível e retirada das ofertas.
- `CustomerFavoritesTest.kt`: persistência, isolamento e inclusão/remoção.
- `CustomerHomeTest.kt`: seções removidas e disponibilidade do favorito local.
- Repositório/ViewModel do repositor: retirada das chamadas que dependiam da
  proposta de backend; preserva a correção de leitura de listas grandes.

O conversor de Home e seu teste, criados somente para a proposta de novas
rotas, foram removidos porque deixaram de ter uso. O patch de backend foi
retirado para evitar aplicação/publicação acidental de um escopo descartado.

## Validação

Em 05/11/2026, o APK e o pacote de testes compilaram e os 77 testes unitários
Android passaram. A validação no aparelho cobre os favoritos locais, seu
isolamento, persistência e remoção, além das telas de cliente sem ofertas.
Nenhuma alteração ou publicação de backend foi feita para esta mudança.
# Atualização: reporte de avaria

O fluxo Android de avaria agora está conectado ao contrato específico de
cliente preparado em [customer-damage-reports](delivery/customer-damage-reports.md).
A entrega inclui patch separado da API, ainda não publicado. O botão é
habilitado quando o ambiente confirmar suporte à nova operação. Favoritos
continuam locais, ofertas seguem fora do escopo e notas usam Firebase AI Logic.

