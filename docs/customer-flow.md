# Fluxo do cliente — entrega e execução

> Atualização de 05/11/2026: este documento descreve o fluxo original dependente
> do backend mobile local. Para o app configurado com as APIs hospedadas, o
> contrato vigente e as limitações estão em [published-api-integration.md](published-api-integration.md).

## Escopo e estado

Implementação nativa Kotlin/Views, sem substituir o fluxo do repositor.
As três telas principais são Home do cliente, Scanner de nota fiscal e Reportar avaria.
Busca, favoritos, histórico, detalhes de produtos e acompanhamento ficam dentro
da Home, em seções/diálogos. A câmera fotográfica existente é reaproveitada.
O visual reutiliza UiKit/InfoCardDialog, tokens DayNight e Montserrat existentes;
a comparação pixel a pixel com o Figma não foi realizada nesta sessão.

A integração mobile do Spring não estava publicada no remoto. Foi reconstruída
na cópia independente `.integration/kairos-springboot`, baseada na main b99a91a.
A cópia de dados é `.integration/kairos-data`, baseada na main 9d9a284.
Nenhum merge, push ou alteração em banco de produção foi feito.

**Importante:** .integration é ignorada no Git do Android. Os patches independentes
em `docs/delivery/` preservam a entrega do backend/dados no repositório mobile,
mas precisam ser revisados e aplicados nos respectivos repositórios antes do deploy.
Não basta publicar somente o APK.

## Autorização e reconstrução

`BusinessIdentity` passa a incluir customerId. CUSTOMER com vínculo SQL válido
abre CUSTOMER_HOME; STOCKER/MANAGER mantêm HOME. Cargo, empresa e IDs são derivados
do banco após validar assinatura RS256, issuer, audience, expiração e claims do ID
token Firebase. O vínculo é por UID imutável; mudar e-mail não transfere a conta.
O fluxo existente de login web/JWT/CSRF permanece em sua própria filter chain.

A validação Firebase usa chaves públicas Google/Nimbus, não segredos Admin no APK.
Revogação administrativa de tokens já emitidos não é consultada a cada chamada;
eles continuam sujeitos à expiração. Para revogação imediata será necessário
integrar Firebase Admin/checkRevoked no servidor. Não confundir isso com validação
de assinatura, que é obrigatória e foi preservada.

Vínculo inicial exige a senha da conta SQL de mesmo e-mail e possui limite de
tentativas persistido. Cadastro público cria somente CUSTOMER na empresa definida
pelo servidor em MOBILE_PUBLIC_COMPANY_ID; sem esse parâmetro, fica indisponível.
Não existe escolha de cargo/empresa no request nem abertura de endpoints
administrativos a CUSTOMER.

Endpoints reconstruídos:

- GET /api/v1/mobile/session
- POST /api/v1/mobile/link
- POST /api/v1/mobile/registration
- GET /api/v1/mobile/catalog
- GET /api/v1/mobile/work-summary
- GET /api/v1/mobile/alerts
- POST /api/v1/mobile/alerts/{id}/resolve
- POST /api/v1/mobile/restockings/registration
- POST /api/v1/mobile/restockings/batch
- GET /api/v1/restockings/week é reutilizado com identidade Firebase mapeada.

Reposições reutilizam RestockingService, estoque, outbox e histórico existentes.
CommandId é idempotente e validado contra UID/payload. A confirmação em lote
participa de uma única transação.

## Contratos do cliente

Todos usam a mesma infraestrutura KairosApi e o mesmo ID token:

| Método e caminho /api/v1/mobile/customer | Operação |
| --- | --- |
| GET /home | Catálogo, locais, ofertas, recomendações, favoritos, compras e reportes |
| PUT /favorites/{productId} | {enabled: boolean}; valida empresa e persiste no PostgreSQL |
| POST /receipts/preview | {qrCode: string}; valida chave, domínio permitido, modelo, dígito e CNPJ |
| POST /receipts/interpret | {imageBase64: JPEG}; sugestões não verificadas do Gemini |
| POST /receipts | {qrCode, items: [{productId, quantity, amount}]} após conferência |
| GET /receipts | Histórico do cliente autenticado, com itens e origem |
| POST /damage-reports | {commandId, productId, shelfId?, problemType, description} |
| GET /damage-reports | Reportes e status do alerta associado |

Recomendações reutilizam CustomerService/ProductRepository. A consulta foi
reforçada com filtro da empresa do cliente. Não há recomendações fabricadas
quando o histórico ainda está vazio. Ofertas vêm de customer_offers com vigência
ativa; não são inventadas nem há endpoint administrativo público de criação.

## Nota fiscal, câmera e IA

Referências oficiais consultadas:

- [Manual DANFE/NFC-e](https://portal.fazenda.sp.gov.br/servicos/nfce/Downloads/Manual%20de%20Especificacoes%20Tecnicas%20do%20DANFE%20NFC-e%20QR%20Code%20%20-%20Versao%204.1.pdf)
- [QR Code NFC-e — Paraná](https://sped.fazenda.pr.gov.br/NFCe/Pagina/QR-Code)
- [Gemini: compreensão de imagens](https://ai.google.dev/gemini-api/docs/generate-content/image-understanding)

QRCode não é tratado como uma lista de produtos. CameraX + ZXing lê o QR localmente.
URLs lidas não são abertas automaticamente nem buscadas pelo servidor. A allowlist
fiscal é explícita; para UF não suportada, o cliente pode digitar a chave de acesso.
Não se faz scraping, bypass de CAPTCHA ou uso de certificados fiscais de terceiros.

A consulta atual extrai/valida a chave e CNPJ e oferece preenchimento manual.
Isso não atesta a autorização/autenticidade fiscal da nota. A compra fica marcada
CUSTOMER_CONFIRMED. IDs dos produtos são escolhidos do catálogo real da empresa.
O total informado é o total da linha, não o preço unitário. Quantidades são inteiras,
respeitando o esquema atual; compras fracionadas/por peso não são suportadas.

Para foto, o cliente autoriza explicitamente o envio ao servidor e ao Google Gemini.
A imagem é reduzida no Android, limitada a JPEG de até 3 MB, validada no backend e
não persistida. A câmera existente recebe captureMode=receipt sem mudar a gôndola.
Gemini retorna somente sugestões de descrição, quantidade, total e chave legível.
Os produtos continuam sendo associados pelo cliente. Não há gravação automática.
Fotos são descartadas do cache após processamento; formulário usa FLAG_SECURE.

GEMINI_API_KEY fica somente no servidor. A configuração privada local autorizada
está em .integration/secrets/gemini.env e é ignorada pelo Git. Não copiar seu
conteúdo para commits, APK, logs ou exemplos. GEMINI_VISION_MODEL é configurável.
O modelo configurado nesta entrega é gemini-3.8-flash. O adaptador faz uma tentativa,
com timeout, limite por usuário de dez segundos e no máximo duas chamadas simultâneas
por processo. Em múltiplas instâncias, adicionar limite global no gateway.

O serviço de análise de gôndolas existente permanece em CHAT_API_URL; não foi
substituído por um serviço simulado nem reimplementado neste escopo. Configure o
mesmo segredo no ambiente privado desse serviço se ele também usar Gemini.

## Avaria e alerta

O cliente seleciona produto/local real, tipo DAMAGED/TAMPERED/EXPIRED/OTHER e descrição.
O backend revalida identidade/empresa/produto/local. O trigger PostgreSQL
customer_damage_alert cria e associa um alerta dentro da transação do reporte.

- Ocorrência: customer_damage_reports.
- Trabalho operacional: alerts (OPEN/RESOLVED/CANCELLED).
- Reposição real: restockings, somente pelo fluxo operacional já existente.

Nenhum estoque é alterado por relato ou importação de nota.
CommandId protege reenvio. O protocolo só é retornado após sucesso no servidor.
Android guarda a confirmação em SharedPreferences privado, por UID/empresa/protocolo,
somente após resposta confirmada; não é fila offline nem substitui PostgreSQL.
Falhas não mostram sucesso. Sem localização, o reporte fica para triagem da gerência;
com gôndola, respeita atribuição direta/corredor do funcionário.
O repositor abre os detalhes e pode resolver o alerta. O cliente vê o status real.
Enquanto MainActivity está ativa, há atualização a cada 20 segundos. Não há push.
Uma regressão também corrigiu a ação Limpar filtros do histórico: a busca agora
é limpa na StockerHomeView, não procurada na subárvore do próprio botão. O teste
verifica tanto o estado interno quanto o texto exibido e os resultados restaurados.

## Migrações

No backend:

- V5__firebase_mobile_identity.sql: vínculos, tentativas e idempotência de reposições.
- V6__customer_receipts_reports_favorites.sql: favoritos, associação a compras,
  ofertas, ocorrências, limite IA, ajuste de alertas e trigger transacional.

A base oficial consultada termina em V4 (DAU). Não aplicar diretamente em ambientes
que tenham as V4/V5 antigas/perdidas de outra branch. Inspecionar flyway_schema_history,
comparar DDL, preparar backup e migração compatível; nunca usar repair para ocultar
divergência. As únicas aplicações executadas nesta sessão foram em PostgreSQL
descartável criado e removido pelo Testcontainers.

## Executar e testar

1. Revisar/aplicar os patches do backend e dados nos repositórios correspondentes.
2. Configurar um PostgreSQL/Redis de desenvolvimento autorizado e os parâmetros
   existentes DB_URL, DB_USERNAME, DB_PASSWORD, AUTH_SIGNING_KEY, issuer/audience.
3. Definir FIREBASE_PROJECT_ID=germinimobile e MOBILE_PUBLIC_COMPANY_ID com o ID real
   da empresa. Não presumir que ID 1 é a empresa correta.
4. Fornecer GEMINI_API_KEY por ambiente/gerenciador de segredos ou arquivo privado
   carregado pelo Spring. Para Compose local, combinar compose.dev.yaml e
   compose.mobile.yaml com KAIROS_PRIVATE_CONFIG apontando ao arquivo privado.
   Não usar credenciais/defaults de compose.dev.yaml em produção.
5. Revisar o banco alvo antes de iniciar Spring, pois Flyway executa no startup.
   Não executar seed nem migrações em banco remoto sem aprovação.
6. Definir as origens HTTPS reais no build Android:

    ./gradlew.bat :app:assembleDebug -Pkairos.springUrl=https://API -Pkairos.chatUrl=https://IA

### Serviços hospedados

O `gradle.properties` define os endereços padrão para debug e release:

- Spring Boot: https://d3bmuckfmecew.cloudfront.net
- IA: https://kairos-inteligencia-artificial.onrender.com

Essas configurações dispensam Docker local e `adb reverse` para acesso às APIs.
Para desenvolvimento local, substitua explicitamente as propriedades com
`-Pkairos.springUrl=http://127.0.0.1:8081 -Pkairos.chatUrl=http://127.0.0.1:8000`
em uma compilação debug e configure o encaminhamento de portas.

Em 05/11/2026, ambos os endpoints `/health` retornaram HTTP 200 e `status: UP`.
A sessão mobile retornou HTTP 401 sem credenciais; isso não comprova que as
rotas mobile e as migrações deste fluxo estejam publicadas. É necessário validar
com uma conta autorizada antes de considerar a integração concluída.

Não incluir caminhos, tokens ou parâmetros nas origens. Loopback de debug ainda
depende do encaminhamento local; esta implementação não publicou túneis nem
hospedou serviços.

Testes Android:

    ./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug

Build para testes de UI sem conta real:

    ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest -Pkairos.isolatedTests=true

O app isolado é com.example.kairos.integration e o runner é
com.example.kairos.customerflow.test/androidx.test.runner.AndroidJUnitRunner.
Não substituir/desinstalar o app principal para executar esses testes.

Testes backend:

    ./mvnw.cmd test checkstyle:check
    ./mvnw.cmd "-Dtest=CustomerPostgresIntegrationTest,NfceKeyTest" "-Dintegration.customer=true" test

O segundo comando exige Docker e cria seu próprio PostgreSQL temporário. Os testes
de integração Redis que já existiam continuam opt-in e devem ser registrados
como ignorados quando não executados.

## Arquivos

Android novos:
- model/cliente/CustomerRepository.kt
- viewmodel/CustomerViewModel.kt
- view/CustomerHomeView.kt, CustomerActivity.kt, ReceiptScannerActivity.kt,
  DamageReportActivity.kt, QrCamera.kt
- test/.../customer/ReceiptItemTest.kt, androidTest/.../CustomerHomeTest.kt

Android adaptados:
- app/build.gradle.kts, AndroidManifest.xml
- SignedInUser.kt, KairosApi.kt, AuthViewModel.kt, MainActivity.kt
- ShelfCameraActivity.kt
- UiKit.kt (seletores reutilizáveis com fonte e cores consistentes)
- StockerHomeRepository.kt, HttpStockerHomeRepository.kt, RestockingHistory.kt,
  StockerHomeView.kt, StockerHomeViewModel.kt
- AuthViewModelTest.kt
- StockerHomeTest.kt (seletores explícitos de diálogo, clique nativo após animação
  e expectativas atualizadas para mensagem de erro/estado vazio já existentes)

Backend:
- novo pacote mobile com configuração de segurança, identidade, controllers,
  serviço operacional, serviço do cliente, validação NFC-e e adaptador Gemini.
- migrations V5/V6, compose.mobile.yaml
- AlertMapper/Alert (local opcional), ProductRepository (escopo das recomendações)
- testes mobile/NfceKeyTest, CustomerPostgresIntegrationTest,
  MobileSecurityIntegrationTest, ReceiptVisionServiceTest
- fixture CustomerRecommendationIntegrationTest ajustada para empresa explícita,
  incluindo produto estrangeiro que não pode aparecer nas recomendações.

Dados:
- docs/customer-mobile.md descreve entidades/trigger, ownership e aplicação via Flyway.

## Limites e validação pendente

Validações de código concluídas nesta sessão:

- 54 testes unitários Android aprovados.
- Build debug aprovado e release não assinado aprovado.
- Lint debug sem erros, com 98 avisos; lint vital release sem erros/avisos.
  A baseline não foi alterada para ocultar problemas.
- Regressão Java completa: 237 testes aprovados e 26 opt-in ignorados.
- Execução PostgreSQL separada: os cinco testes de CustomerPostgresIntegrationTest
  aprovados com V1–V6 em banco descartável. Após essa execução, os relatórios
  acumulados somam 242 aprovados e 21 ignorados, sem falhas.
- NfceKeyTest, ReceiptVisionServiceTest e MobileSecurityIntegrationTest aprovados:
  URLs maliciosas/dígitos inválidos, limites/saídas IA e autorização HTTP.
- Nove testes instrumentados aprovados no Samsung, em pacote isolado:
  CustomerHomeTest (2), AccountScreensTest (1) e StockerHomeTest (6). A regressão
  cobriu filtro/limpeza, restauração por proprietário, resultados atrasados de
  outra conta, retry, logout/navegação e geometria do menu.
  Fixtures existem somente nos testes/debug e não substituem o backend de produção.
- Os patches de backend/dados foram conferidos com git apply --reverse --check
  sobre as cópias de trabalho, sem aplicar/reverter nenhuma alteração.

Os APKs normais preservados estão em build/delivery/kairos-customer-debug.apk e
build/delivery/kairos-customer-release-unsigned.apk. São diferentes do pacote
isolado instalado para a instrumentação; o app principal não foi substituído.

- Consultas da Home têm limites: 1000 produtos e 100 registros de compras/reportes.
  Paginação para catálogos maiores ainda não foi implementada.
- Não há importação automática oficial dos itens pela SEFAZ nem certificação fiscal.
- Testes do Gemini usam cliente HTTP simulado. Não foi enviada foto pessoal nem
  realizada chamada paga ao provedor; chave/cota/modelo ainda precisam ser validados
  com uma nota autorizada.
- Captura de QR/foto real e o ciclo completo com uma conta Firebase CUSTOMER real
  dependem de ambiente configurado e uma nota autorizada; não declarar aprovação
  desses passos com base só nos testes de API/SQL ou nas fixtures de interface.
- Validar uma foto NFC-e real por UF e o reconhecimento da chave em condições reais.
- O histórico antigo da integração perdida não foi recuperado; a reconstrução
  exige revisão antes de substituir um servidor que ainda tenha aquela versão.
