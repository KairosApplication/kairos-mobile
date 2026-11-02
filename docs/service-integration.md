# Integração mobile, identidade e IA
> Correção do limite Groq: uma pergunta envolve múltiplas etapas e as instruções originais eram extensas. Foram adicionadas instruções compactas no modo Groq, mantendo as verificações de acesso, guardrail e juiz. O teste completo posterior retornou HTTP 200, resposta aprovada e pergunta/resposta persistidas. A regressão passou com 141 testes, incluindo orçamento de instruções, ausência de retries e proteção dos metadados de cota. Esse resultado substitui a pendência anterior de resposta completa não validada. Nenhuma chave foi incluída no app ou no Git.
> Configuração Groq em 02/11/2026: o servidor agora aceita modo exclusivamente Groq, sem inicializar OpenAI. A chave autorizada fica em .integration/secrets/groq.env, ignorada pelo Git e carregada somente pelo script do servidor. O manual é consultado localmente com fontes de página. Foram aprovados 139 testes. No único teste real, a chave foi aceita e o guardrail respondeu; o passo seguinte recebeu HTTP 429 do Groq. Não houve resposta completa nem novas tentativas de IA após esse limite. Essa atualização substitui o estado anterior de chave ausente; a configuração está ativa, mas a resposta completa depende da disponibilidade da cota do provedor.
> Atualização de 02/11/2026: o visual do chat, scanner, câmera e estados vazios foi revisado com componentes consistentes, suporte aos temas e mensagens orientativas. O serviço Python em 8000, a API de persistência em 8082 e MongoDB local em 27018 estão ativos. Criar/listar/abrir conversas foi validado com as duas contas Firebase reais; somente os registros criados pela verificação foram removidos. As respostas automáticas continuam desativadas por ausência de OPENAI_API_KEY, conforme informado pelo responsável; o servidor retorna AI_NOT_CONFIGURED e o app explica essa condição sem culpar a internet. Não há respostas simuladas. Para retomar conversas, executar tools/start-local-chat.ps1 após o backend e então tools/connect-local-services.ps1. O script exige a API Mongo compilada e o ambiente Python já instalados em .integration.

> Validação visual/funcional: build debug e 46 testes unitários do app aprovados; 3 testes instrumentados de chat/scanner aprovados em pacote isolado; 135 testes Python aprovados. Capturas locais do chat e do processamento estão em build/qa. A versão final foi instalada preservando o login no aplicativo principal.

> Ajuste solicitado pelo responsável: cadastro, login, vínculo e chatbot não exigem confirmação de e-mail. O envio automático e a tela de verificação foram removidos. Essa decisão substitui as referências anteriores a e-mail verificado neste relatório. A autenticação por token e o vínculo PostgreSQL continuam obrigatórios.

> Login e disponibilidade dos serviços foram separados: configuração ausente, 403 ou indisponibilidade do servidor preservam a identidade Firebase e mostram a tela de acesso empresarial com aviso. Isso não concede cargo, empresa, Home operacional ou dados fictícios. Tokens rejeitados com 401 continuam invalidando o acesso. Os endereços dos serviços ainda precisam ser fornecidos/configurados para usar estoque e IA reais.

> A opção de demonstração foi retirada a pedido do responsável. A área operacional depende da configuração e execução dos serviços reais.

> O debug aponta para a API local real em `http://127.0.0.1:8081` e chatbot em `http://127.0.0.1:8000`, portas documentadas dos serviços. `tools/connect-local-services.ps1` verifica a API e configura adb reverse por USB. HTTP só é permitido em loopback no debug; release continua exigindo HTTPS. Isso não inicia os servidores nem transforma uma indisponibilidade em dados simulados.

> Após autorização explícita e reinício manual, WSL e Docker estão operacionais, com aplicativo e discos no D:. PostgreSQL, Redis, histórico e Spring estão rodando pelo Compose de desenvolvimento; nenhum banco remoto foi alterado. A credencial Admin fornecida para germinimobile é montada somente no servidor a partir de .integration/secrets, fora do Git, da imagem e do APK. As duas contas indicadas foram vinculadas explicitamente a funcionários STOCKER distintos no banco local. Os registros locais usam metadados de teste, não cadastros pessoais completos de produção.

> Verificação real: tokens Firebase das duas contas passaram por sessão, catálogo com prateleira/estoque/produto, alertas, resumo e histórico; acesso anônimo foi recusado. Testes HTTP de CSRF, renovação, logout e histórico com alteração/exclusão passaram com PostgreSQL/Redis reais. Um erro JDBC ao converter integer para Long.class foi corrigido em MobileIdentityService; os 10 testes mobile e Checkstyle passaram. O APK foi atualizado preservando os dados; adb reverse foi configurado e a Home do Breno foi conferida no celular em build/qa/connection-check.png.

> Para retomar: abrir Docker Desktop, executar tools/start-local-backend.ps1 e depois tools/connect-local-services.ps1 com o celular por USB. O primeiro comando mantém reinício automático dos contêineres e TTL de acesso legado de 15 minutos. Chatbot e análise Gemini ainda dependem dos respectivos serviços e credenciais de IA e não estão operacionais.

Entrega local em 01/11/2026. Os serviços precisam ser configurados e implantados em conjunto antes do uso real. Nenhum banco remoto foi alterado e nenhuma credencial de IA foi utilizada.

## Revisão e bases

| Repositório | Base consultada | Alterações locais |
| --- | --- | --- |
| kairos-mobile | `966f408`, igual a origin/main | Branch `codex/secure-service-integration` |
| kairos-springboot | `a1d0d84` | `.integration/kairos-springboot`, branch `codex/firebase-mobile-identity`, commit `4504ee0` |
| kairos-inteligencia-artificial | `76f987e` | `.integration/kairos-inteligencia-artificial`, branch `codex/mobile-identity-and-vision`, commit `b413d43` |
| kairos-api-ia | `cfcc77f` | Inspecionado; sem alteração |
| kairos-data | `0301d4d` | Inspecionado; sem alteração |

Os 50 arquivos Kotlin de produção de `Downloads/kairos/kairos-mobile.zip` são iguais aos da main consultada. O ZIP não foi sobreposto ao checkout. As alterações preexistentes da IDE foram preservadas e excluídas dos commits.

Foram examinados autenticação, DTOs, controllers, migrations, serviços, testes e documentação. `RestockingService.moveStock()` já existe e foi preservado integralmente. A documentação antiga que mencionava JSESSIONID como autenticação principal estava desatualizada: o Spring atual usa JWT e sessões Redis.

## Problemas e correções

| Causa original | Correção |
| --- | --- |
| AuthViewModel liberava Home com apenas um perfil Firestore | Exige e-mail verificado e identidade empresarial do servidor, com funcionário, empresa e cargo válidos |
| UID Firebase sem relação explícita com PostgreSQL | Migration V4 cria vínculo único UID → users.id; não há preenchimento automático por e-mail |
| Chatbot aceitava e-mail Firebase verificado como chave de associação | Exige vínculo explícito validado pelo Spring e confere novamente userId, empresa e cargo no PostgreSQL |
| MainActivity instanciava DemoStockerHomeRepository | Usa HttpStockerHomeRepository; fixture demonstrativa movida para src/debug |
| Home, alertas e perfil tinham números locais | Dados HTTP autorizados; resumo agregado oficial da semana; nenhuma substituição por exemplos em falha |
| Não havia chat operacional | Endpoints pessoais existentes, conversas, mensagens, fontes públicas, bloqueios, histórico e paginação |
| Scanner era uma demonstração estática | CameraX, permissão, captura, prévia, seleção da gôndola, análise, correção manual, andamento físico, foto de conferência e confirmação |
| Repetição após falha de rede podia registrar outra reposição | Migration V5 e comando UUID idempotente, persistido na mesma transação da movimentação |
| Checkbox exigia aceitar termos inexistentes | Informa indisponibilidade, sem exigir ou registrar consentimento fictício |
| Logs do grafo imprimiam justificativas geradas pelo modelo | Removido conteúdo dinâmico das justificativas e traceback de falha secundária |

## Identidade e autorização

Firebase permanece como provedor do Android para login, cadastro parcial, recuperação, verificação de e-mail e sessão. O Spring valida assinatura, projeto, expiração, revogação e usuário desabilitado com Firebase Admin `verifyIdToken(token, true)` em cada requisição mobile.

A cadeia de segurança mobile recebe exclusivamente Bearer Firebase e rejeita cookies. A cadeia JWT/Redis dos clientes existentes permanece intacta. As rotas mobile são restritas; um token Firebase não ganha acesso aos CRUDs genéricos que não foram revisados para esse fluxo.

O vínculo usa a senha já cadastrada no PostgreSQL, além do token Firebase com e-mail verificado. O e-mail é apenas uma chave de busca nessa comprovação, nunca prova suficiente. Tentativas são limitadas a cinco por UID a cada quinze minutos no Redis, sem fallback se Redis falhar. Hashes legados desconhecidos são rejeitados, sem comparação com senha em texto simples.

Depois do vínculo, a referência é o UID imutável: trocar o e-mail Firebase não transfere a identidade para outra conta PostgreSQL. Alterações de cargo e empresa são relidas no servidor. `plan` do Firestore não participa da autorização. O nome/e-mail pessoal do Firestore continua separado do cadastro comercial PostgreSQL; editar o contato Firebase não altera silenciosamente o e-mail comercial.

O cadastro público cria somente CUSTOMER, usando a empresa configurada no servidor em `MOBILE_PUBLIC_COMPANY_ID`. Com essa variável ausente/zero, o cadastro comercial público fica indisponível de forma controlada. Funcionários continuam sendo cadastrados pela gestão empresarial existente. Não há endpoint mobile para escolher cargo ou empresa. Contas existentes devem usar o vínculo, sem duplicação.

STOCKER e MANAGER podem abrir a área operacional. CASHIER não ganha acesso ao estoque ou ao chatbot de estoque. CUSTOMER vinculado tem entrada para o suporte, sujeito às permissões do chatbot existente. Os dados de gôndolas respeitam atribuição direta ou de corredor; estoques respeitam o vínculo do funcionário. MANAGER opera na própria empresa. A identidade do funcionário na confirmação vem do servidor.

Troca de UID, empresa, funcionário ou cargo limpa Home e chat. Resultados atrasados não são publicados em outra sessão. Tokens são obtidos novamente no SDK; a verificação de sessão força atualização das claims, inclusive após confirmação de e-mail. Escritas não são repetidas automaticamente.

Logout limpa a sessão local Firebase; revogação administrativa é verificada no servidor. Uma requisição já autorizada pode concluir. Não foi criado armazenamento próprio de senhas, JWTs ou refresh tokens. Backups e transferência de dados do aplicativo foram excluídos. A persistência interna de credenciais do SDK Firebase não foi auditada como criptografada por Android Keystore; uma exigência absoluta que inclua os arquivos internos do SDK continua pendente de validação antes de produção.

## Contratos utilizados

Todos os endpoints Android recebem `Authorization: Bearer <Firebase ID token>`. O cliente envia também `X-Kairos-Identity: firebase`; esse marcador seleciona a cadeia Firebase para o endpoint semanal existente, sem conferir qualquer permissão.

| Serviço e rota | Contrato |
| --- | --- |
| Spring GET `/api/v1/mobile/session` | firebaseUid, email comercial, userId, employeeId, companyId, role e companyName; role UNLINKED quando ainda não vinculado |
| Spring POST `/api/v1/mobile/link` | `{password}`; comprovação da conta existente |
| Spring POST `/api/v1/mobile/registration` | name, lastName, birthDate e cpf; somente cliente, empresa escolhida pelo servidor |
| Spring GET `/api/v1/mobile/catalog` | shelves autorizadas, inventories autorizados e products da empresa |
| Spring GET `/api/v1/mobile/alerts` | Alertas OPEN do escopo permitido, com descrição e data originais |
| Spring GET `/api/v1/mobile/work-summary` | Contagem e avarias da semana inteira, sem depender do limite do histórico |
| Spring GET `/api/v1/restockings/week?limit=500` | Contrato existente, incluindo campos snake_case dos itens e semana de São Paulo |
| Spring POST `/api/v1/mobile/restockings/registration` | commandId UUID, inventoryId, shelfId, productId, restockedQuantity e damagedQuantity |
| Python GET `/api/v1/me/chat` | Contexto pessoal já existente |
| Python GET/POST `/api/v1/me/chat/conversations` | Listar/criar conversas; paginação de vinte itens no Android |
| Python GET `/api/v1/me/chat/conversations/{id}` | Histórico da conversa autorizada |
| Python POST `/api/v1/me/chat/messages` | question e conversationId opcional; answer, conversationId, executionId, blocked, approved e sources |
| Python POST `/api/v1/me/shelf-analyses` | shelfId confirmado, mimeType JPEG/PNG e imageBase64; observações e candidatos sem movimentação |

O adaptador mobile de confirmação delega ao mesmo `RestockingService.save()` usado por `/api/v1/restockings/registration`. Acrescenta escopo de gôndola/estoque, identidade derivada e idempotência, sem alterar `moveStock()`, contratos existentes ou estornos. Um UUID repetido com outro conteúdo retorna 409. Avarias saem do estoque e não entram na gôndola, como no serviço atual.

A IA só propõe candidatos. `productId` permanece null no resultado; `candidateProductIds` contém apenas correspondências exatas de nome/marca no catálogo autorizado. A seleção final é manual, inclusive quando há um único candidato. Quantidade visível pode ser null e nunca representa saldo total ou quantidade a repor.

## Imagens e privacidade

CameraX 1.6.2 solicita CAMERA e vincula a captura ao ciclo de vida. Não há permissão de armazenamento compartilhado. O Android reduz a foto para até 1600 px no maior lado e reencoda JPEG, corrigindo rotação e removendo metadados. O backend revalida formato real, mínimo 256 px, máximo 4096 px/8 MP, imagem única e até 4 MiB. A requisição JSON é limitada a 6 MiB.

O backend mantém no máximo duas análises simultâneas por processo e limita novas análises por usuário/empresa a uma a cada dez segundos. Timeout, rate limit, bloqueio do provedor, JSON inválido e ausência de chave retornam erros HTTP. Não há resultado artificial de sucesso.

As fotos ficam no cache privado Android durante o fluxo; são removidas ao substituir, concluir ou encerrar normalmente. Capturas abandonadas por interrupção são limpas ao iniciar outra sessão do scanner. No backend, a imagem existe apenas na memória da requisição, sem arquivos, logs ou persistência MongoDB. A imagem é enviada ao Gemini; as condições de retenção do provedor devem ser aprovadas pela organização antes de habilitar o recurso. A foto posterior à reposição é uma conferência visual local e não comprova saldo.

O registro pendente contém somente IDs e quantidades em armazenamento privado, separado por UID, sem foto, senha ou token. Ele preserva o UUID após encerramento do processo para repetir com segurança uma confirmação de resultado incerto. Não se deve apagar esse estado para contornar conflitos.

## Bancos e compatibilidade

PostgreSQL mantém dados comerciais e movimentação; o APK não o acessa. Redis continua com sessões JWT e cache semanal existentes, além do limite de tentativas de vínculo. O fallback do histórico para PostgreSQL continua no Spring. MongoDB permanece atrás da API de persistência de IA existente, com LangGraph e controles de propriedade/escopo preservados. O modo pessoal exige `PERSISTENCE_BACKEND=api`.

Neo4j possui scripts de projeção e validação em kairos-data; não há integração Spring automática no código consultado. Essa projeção independente não foi alterada nem passou a autorizar o mobile. As permissões usam vínculos oficiais PostgreSQL.

V4 e V5 são migrations aditivas e não foram aplicadas remotamente. Devem ser revisadas e testadas em PostgreSQL descartável antes do deploy. O bootstrap dos testes usa H2 e não executa a aplicação com variáveis de produção.

## Configuração e execução

### Android

Use JDK 21+ e o SDK Android 36. Mantenha o google-services.json correto para `com.example.kairos` fora de commits. Defina origens HTTPS sem caminho, query ou credenciais em um arquivo de propriedades Gradle local:

```properties
kairos.springUrl=https://api.seu-dominio.example
kairos.chatUrl=https://ia.seu-dominio.example
```

```powershell
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug
```

Sem essas origens, o app informa configuração pendente e não usa demonstrações. O release gerado é não assinado; configure assinatura de distribuição fora do repositório. A fixture DemoStockerHomeRepository só existe em src/debug, sem ser injetada pela MainActivity.

### Spring

Configure as variáveis já existentes de PostgreSQL, `AUTH_SIGNING_KEY` e Redis em ambiente descartável. Acrescente `FIREBASE_PROJECT_ID`, credenciais ADC do Firebase Admin no processo do servidor e, quando o cadastro público for aprovado para uma loja, `MOBILE_PUBLIC_COMPANY_ID`. Nunca coloque credenciais Admin no Android.

```powershell
cd .integration/kairos-springboot
./mvnw.cmd test checkstyle:check
# Somente após configurar banco descartável; iniciar executa Flyway.
./mvnw.cmd spring-boot:run
```

Configure o Redis de histórico pelos parâmetros documentados no próprio backend. O Compose de desenvolvimento existente usa banco/cache locais; nenhuma URL remota é necessária para os testes H2.

### Chatbot, scanner e persistência

Configure `SPRING_AUTH_URL` com a origem do Spring e `FIREBASE_PROJECT_ID` igual ao Android/Spring. Para o chat, configure `PERSISTENCE_BACKEND=api`, `AI_API_URL`, `AI_API_TOKEN`, os provedores LLM já existentes e as credenciais PostgreSQL do serviço Python, com permissões adequadas. Tokens internos nunca são fornecidos ao APK.

Na API MongoDB, mantenha escrita desabilitada por padrão. Habilite `APP_MONGODB_WRITES_ENABLED=true` somente em banco descartável autorizado ou após aprovação explícita do responsável pelo ambiente real. Esta entrega não alterou essa opção.

Para visão, configure uma **nova** `GEMINI_API_KEY` apenas no servidor e, opcionalmente, `GEMINI_VISION_MODEL` e `VISION_TIMEOUT_SECONDS`. O modelo padrão é `gemini-3.8-flash`, configurável. A chave mencionada no pedido não foi consultada nem usada. Sua revogação e substituição ainda cabem ao responsável pela credencial.

```powershell
cd .integration/kairos-inteligencia-artificial
python -m venv .venv
./.venv/Scripts/python.exe -m pip install -r requirements.txt pytest
./.venv/Scripts/python.exe -m pytest -q
./.venv/Scripts/python.exe -m uvicorn api:app --host 127.0.0.1 --port 8000
```

Use terminação TLS e limites de corpo/concorrência também no gateway antes de disponibilizar os serviços. A limitação do scanner em memória é por processo, não uma quota global distribuída.

## Validação e limites da entrega

- Kotlin: 45 testes unitários aprovados após separar autenticação e disponibilidade empresarial; build debug repetido e aprovado. O build release e lint anteriores foram aprovados; lint debug teve 92 avisos, sem novos erros, e lint vital release não teve erros. Relatórios Gradle locais preservados. A tarefa testReleaseUnitTest não existe na configuração AGP deste projeto; a tentativa foi rejeitada antes de executar e não conta como teste aprovado.
- Java: suíte de 251 casos contabilizados após acrescentar o histórico Firebase; 240 aprovados e 11 pulados por dependerem de infraestrutura opcional. Checkstyle aprovado. Há dez testes novos de integração mobile em H2.
- Python: 135 testes aprovados, incluindo dezoito novos de identidade/visão, sem acesso a provedores ou bancos reais.
- Android 14 físico: cinco testes instrumentados aprovados em `com.example.kairos.integration`, com repositórios falsos e sem Firebase real. Um teste visual foi repetido após o ajuste da fonte. A instalação normal foi recusada por assinatura diferente; a instalação existente foi preservada.
- Capturas locais revisadas: `build/qa/analysis.png` e `build/qa/support.png`. Frames consultados: scanner 713:1448, conferência 713:1466, análise 713:1502, conclusão 713:1511 e suporte 713:2397. Os SVGs foram convertidos pelo Svg2Vector do Android Studio; a ampulheta segue o ciclo de dois segundos do Figma.
- Não foram executados login/cadastro/verificação por e-mail reais, captura física da câmera, inferência Gemini ou conversas com MongoDB real. Faltam ambientes e credenciais autorizados para esses testes ponta a ponta.
- Docker/PostgreSQL/Redis/MongoDB/Neo4j descartáveis não estavam disponíveis nesta sessão; testes reais desses bancos e aplicação de V4/V5 em PostgreSQL continuam pendentes. Os testes existentes de fallback Redis usam os mecanismos da suíte do backend.
- O histórico mostra até 500 registros da semana, limite do endpoint existente; não há paginação de semanas antigas no app. O resumo agregado não é truncado.
- A confirmação operacional é por produto, com uma transação e um UUID. Não há lote transacional de vários produtos. A revisão manual e a seleção do catálogo continuam obrigatórias.
- Termos de Serviço aprovados, política do provedor de imagens e auditoria da persistência interna Firebase são condições pendentes de liberação, não aprovações presumidas.
- A UI funcional preserva os tokens e fontes existentes e usa os frames como referência. A câmera física e todos os estados operacionais ainda precisam de homologação visual no aparelho com o ambiente integrado.

## Revisão e publicação

As três branches são locais, por repositório; não houve push nem escrita na main. As cópias `.integration` são repositórios Git independentes ignorados pelo mobile, não submódulos. Os commits do backend e chatbot precisam ser publicados nos respectivos repositórios; copiar só o APK ou só o commit mobile não implanta os novos contratos.

O autor foi informado durante a sessão; a identidade é aplicada somente aos comandos de commit, sem alterar a configuração global. Patches independentes para revisão ficam em `build/delivery/mobile.patch`, `springboot.patch` e `chatbot.patch`, excluindo os arquivos da IDE. Não houve publicação remota.

Antes de publicar, atualizar as referências remotas e revisar divergências novamente. Preparar PRs separados: identidade/contratos no Spring; vínculo de identidade e visão no chatbot; integração e telas no Android. Não há alterações necessárias nesta entrega nos repositórios MongoDB e kairos-data.

Referências técnicas: [Firebase ID tokens](https://firebase.google.com/docs/auth/admin/verify-id-tokens), [revogação](https://firebase.google.com/docs/auth/admin/manage-sessions), [Firebase Admin Java](https://firebase.google.com/support/release-notes/admin/java), [CameraX](https://developer.android.com/jetpack/androidx/releases/camera), [Gemini structured outputs](https://ai.google.dev/gemini-api/docs/generate-content/structured-output), [modelos Gemini](https://ai.google.dev/gemini-api/docs/models).
