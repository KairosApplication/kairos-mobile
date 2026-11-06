# Revisao do fluxo de avarias - 2026-11-05

## Bases e local das alteracoes

- Mobile: PR #34 ainda aberta na consulta inicial; base `chore/estruturacao-cliente`, commit `fd96df17212fcc1a35465c8a7dd9e5dc2921e619`.
- Branch local mobile: `fix/customer-damage-stocker-link`.
- Backend oficial: `main`, commit `b99a91a5919af5cdab179617b25ca7efcf042b14`.
- Branch local backend: `feat/customer-damage-report-flow`.
- Copias isoladas em `%TEMP%/kairos-damage-review-20261105/mobile` e `backend`. Checkouts existentes preservados. Nenhuma fonte `.integration` utilizada.

## O que ja existia

A PR mobile ja implementava o formulario de avaria, foto opcional, envio idempotente, protocolo, acompanhamento do cliente e interface do repositor para `customerReport`.
O backend desse fluxo estava apenas no patch de entrega da PR mobile; nao na main oficial. A main atual coincidiu com a base do patch, que passou em `git apply --check` antes da aplicacao local.
A lacuna foi confirmada: a lista comum de alertas nao fornecia os metadados que alimentam `customerReport`.

## Arquivos e motivos

### Mobile

- `StockerDamageReports.kt`: associacao por ID somente a alertas carregados; copia das entradas; validacao de UUID, Instant e quatro tipos de problema; sem classificacao por descricao.
- `HttpStockerHomeRepository.kt`: consulta metadados, enriquece os alertas e utiliza a rota especifica de resolucao; exige confirmacao com ID e status corretos. Mantem a UI, os estados OBSERVED, a ausencia de analise de reposicao em relatos e o recarregamento existente.
- `StockerDamageReportsTest.kt`: associacao, preservacao, ausencia de mutacao, metadados sem alerta, descricao comum e rejeicao de dados invalidos.
- `StockerListReaderTest.kt`: leitura apenas dos seis campos do contrato, descartando foto e dados do cliente.
- `docs/delivery`: patch completo do backend e documentacao atualizados.
- `.gitattributes`: preserva as linhas de contexto vazias do patch de entrega sem trata-las como espacos finais do codigo.

403/404 nos metadados nao sao ignorados. Se houver alertas e o contrato estiver ausente ou negado, a Home apresenta erro, evitando tratar uma avaria como reposicao comum. Por isso, publicar a API antes do app.

### Backend

- Patch original incorporado: `CustomerDamageReportController`, `CustomerDamageReportRequest`, `CustomerDamageReportService`, `V5__customer_damage_reports.sql` e permissoes CUSTOMER especificas.
- `StockerDamageReportController` e `StockerDamageReportService`: consulta sem dados pessoais/foto, identidade da sessao, atribuicao e mercado conferidos, `Cache-Control: no-store`, resolucao transacional com bloqueio do alerta e primeira data preservada.
- `CustomerDamageReportPostgresTest`: 11 cenarios, incluindo os originais e a ligacao com o repositor; sem alteracao de estoque, compras ou reposicoes.
- `FlywaySchemaIntegrationTest`: cinco migrations/24 tabelas nesta base; teste especifico de atividade diaria limitado ao target 4.
- `AuthControllerIntegrationTest`: cliente recebe 403 nas rotas de repositor.
- `StockerDamageReportControllerTest`: rotas, identidade recebida, resposta e no-store.
- `CustomerDamageReportServiceTest` e `StockerDamageReportServiceTest`: regras do fluxo tambem cobertas na suite padrao da CI.
- Comparacoes de autoridades tornadas seguras para valores nulos apos diagnostico do SpotBugs; limite de cobertura mantido em 90%.

## Contratos novos na API

Cliente: GET capabilities, GET options, POST e GET em `/api/v1/customer/damage-reports`.
Repositor: GET `/api/v1/alerts/customer-reports` e PATCH `/api/v1/alerts/customer-reports/{alertId}/resolve`.
As permissoes EMPLOYEE/MANAGER existentes para `/api/v1/alerts/**` foram preservadas, com verificacao adicional de atribuicao, cargo e mercado no servico.

## Validacao local

Java 21 e SDK Android instalado.

- `gradlew.bat --no-daemon :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest`: sucesso; 84 testes unitarios, zero falhas. APK de app e APK de testes gerados. Testes instrumentados nao executados em aparelho/emulador nesta revisao.
- `mvnw.cmd -B -ntp clean verify`: sucesso; 270 casos, 238 executados e 32 ignorados pelos perfis existentes. Cobertura 92,29%; Checkstyle zero; SpotBugs zero.
- Sete testes focados novos de servico/controller: sucesso.
- A primeira rodada completa PostgreSQL executou 254 testes, incluindo 11 de avarias, sem falhas; o build foi bloqueado por tres avisos de nulidade, corrigidos depois. A primeira rodada padrao identificou cobertura de 86%, corrigida com testes de servico, sem reduzir o limite.
- `mvnw.cmd -B -ntp -Ppostgres-tests clean verify`: sucesso na versao final; 270 casos, 261 executados, 9 ignorados. Zero falhas/erros. Cobertura 92.95%; Checkstyle zero; SpotBugs zero. Os 11 testes de avarias e dois de Flyway passaram.

Os testes PostgreSQL usam somente Testcontainers descartaveis. Nenhum segredo foi copiado para codigo, patch ou documentacao.

## Ambiente publicado e pendencias

- Ambos os logins fornecidos autenticaram na API publicada; perfis EMPLOYEE e CUSTOMER confirmados.
- Consulta somente leitura confirmou que o repositor pertence ao mercado 57 e o cliente ao mercado 1. Portanto, essas contas nao podem validar o mesmo relato. Usar contas autorizadas do mesmo mercado, com repositor vinculado a gondola/corredor do produto.
- OpenAPI publicado: nenhuma rota de avaria disponivel na verificacao.
- Banco configurado: historico Flyway ate V3; tabela `customer_damage_reports` ausente. Nenhuma migration foi executada nesse banco. A publicacao deve aplicar as migrations pendentes pelo Flyway habitual; nao renumerar migrations nem usar ddl-auto=update.
- API somente preparada localmente: nenhum push, PR, merge ou deploy nesta revisao. Nenhuma conta foi criada/modificada e nenhuma avaria ficticia foi enviada ao ambiente publicado.
- Fluxo publicado ainda nao concluido. Apos alinhar com o responsavel, publicar API, disponibilizar o app e validar envio com/sem foto, protocolo, alerta correto, resolucao, acompanhamento, reenvios/falha de rede e isolamento entre contas/mercados.
