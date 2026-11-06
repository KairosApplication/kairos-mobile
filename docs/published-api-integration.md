# Integração Android com a API publicada

> Favoritos agora são locais por conta; ofertas foram retiradas do escopo.
> O app utiliza somente os serviços existentes, sem publicação de backend.
> Consulte [a entrega atual](customer-features-integration.md).

> A leitura de fotos de notas agora usa [Firebase AI Logic](firebase-ai-receipts.md)
> diretamente no Android. Não exige publicação de novas rotas Spring.

Em 05/11/2026, a documentação pública do Spring Boot não contém `/api/v1/mobile/*`.
O aplicativo passou a usar o contrato publicado. Não foram alterados backend,
banco remoto, contas existentes ou infraestrutura.

## Sessão

- `GET /api/v1/auth/login`: obtém CSRF e cookie.
- `POST /api/v1/auth/login`: formulário com e-mail/senha, CSRF e cookies.
- `GET /api/v1/auth/me`: valida o token no servidor e retorna e-mail/papéis.
- `POST /api/v1/auth/refresh`: renova a sessão, com CSRF e refresh cookie.
- `POST /api/v1/auth/logout`: encerra a sessão no servidor; a sessão local é
  removida mesmo se a rede falhar, com aviso de que a revogação não foi confirmada.
- `POST /api/v1/users/registration`: cadastro público com CSRF, cookies, dados
  pessoais e código de mercado informado pelo usuário. Após HTTP 201, valida
  o token retornado em `/auth/me` e abre a Home de cliente.

Access token e cookies ficam criptografados com AES-GCM/Android Keystore em
`noBackupFilesDir`, associados à origem da API. Senhas nunca são persistidas.
A rotação de refresh é serializada. Requisições de dados usam Bearer, sem cookies
e sem o antigo header de identidade Firebase; mutações não são repetidas automaticamente.
Os dados mínimos do JWT são usados apenas para identificar a sessão; `/auth/me`
deve confirmar a identidade antes de liberar a Home. Papéis vêm de `/auth/me`.

Cliente `CUSTOMER` abre a Home sem exigir empresa, customerId ou perfil Firestore.
`EMPLOYEE` e `MANAGER` abrem o fluxo operacional. Permissões reais continuam
sendo verificadas pelo servidor em cada recurso. Erros 403 de dados ficam na
tela correspondente e não redirecionam para verificação de empresa.

Contas apenas no Firebase não são automaticamente contas PostgreSQL. O login
usa a credencial da API e informa a recusa sem criar usuário ou vínculo.
Os adaptadores antigos Firebase permanecem no projeto para evitar remoções
desnecessárias e preservar os testes, mas não são o login configurado em runtime.

## Homes

A Home do repositor lê alertas e localizações por streaming com limite de
32 MB por lista. A API publicada pode retornar listas acima dos 4 MB do leitor
JSON comum; isso anteriormente interrompia a Home inteira. Apenas alertas
abertos atribuídos ao funcionário retornado por `/restockings/week` são
mantidos. Prateleiras e corredores são consultados somente quando necessários
e apenas suas localizações relevantes ficam em memória. Limites de registros
geram erro explícito, sem apresentar uma lista truncada como completa.

Validação em 05/11/2026: build debug e testes unitários passaram. Quatro testes
no aparelho passaram, incluindo leitura acima de 4 MB, seleção de localizações,
limite de registros e carregamento real da Home com a sessão do repositor.

- Cliente: catálogo real em `GET /api/v1/products/list`, busca e detalhes.
- Repositor: `GET /api/v1/restockings/week`, `/aisles/list`, `/shelves/list`
  e `/alerts/list`, com prefixo `/api/v1`. Resolução usa
  `PATCH /api/v1/alerts/update/{id}` com `status=RESOLVED`.
- Catálogo de reposição usa `/restockings/options`, produtos, corredores e
  prateleiras existentes. Não presume IDs de funcionário ou empresa.
- Quantidade de avarias é calculada nos registros semanais retornados (limite
  de 500); se houver mais registros, essa soma não cobre a semana inteira.

## Pendências explícitas

- O cadastro publicado exige `companyId` e não há lista pública de empresas.
  O formulário pede o código fornecido pelo mercado. Não há seleção automática,
  tentativa de IDs ou criação de uma conta Firebase paralela. O backend valida
  se o mercado existe e define o papel de cliente. Essa exigência é do contrato
  de cadastro; a abertura da Home continua sem exigir empresa na sessão.
- Não há contrato publicado para recuperação de senha ou edição da própria conta.
  O app informa essa indisponibilidade e não altera apenas o Firebase.
- Favoritos, ofertas, localização para cliente, recomendações sem customerId,
  notas fiscais e reportes não são apresentados como listas vazias confirmadas.
  As ações indisponíveis ficam desabilitadas e a Home explica a limitação.
- O registro de reposição existe, mas o fluxo atual do scanner usa lote atômico
  e commandId para recuperação. O servidor publicado não oferece esse contrato.
  A finalização fica explicitamente indisponível em vez de criar reposições
  duplicadas ao tentar novamente. Adaptar isso é uma etapa separada.
- IA/chat continuam sob responsabilidade do integrante que mantém esses serviços;
  sua compatibilidade com o token da API precisa de validação própria.

Os adaptadores legados `/mobile` e patches de backend em `docs/delivery` não são
requisitos para o login e as Homes atuais. Não publicar esses patches como
consequência desta adaptação sem revisão separada.

## Validação

Testes locais de contrato usam transporte simulado e dados fictícios. Validam
CSRF/cookies, formulário, papéis, restauração, rotação, falhas, troca de conta e
isolamento entre origens. Teste Android valida criptografia real no Keystore.
O teste de login com conta real na API depende de execução pelo titular; não
foram extraídos tokens Firebase nem usadas senhas de contas existentes.

Compilar: `./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`.
Para testes de UI isolados, usar `-Pkairos.isolatedTests=true`.

Verificação desta entrega: 67 testes unitários passaram; 5 testes Android passaram
no Samsung em pacote isolado (Keystore, três cenários da Home cliente e segurança).
O início público do login hospedado retornou HTTP 200, `X-CSRF-TOKEN`, cookie e
`Cache-Control: no-store`. Login com conta real e dados operacionais remotos ainda
dependem de teste pelo titular. APK normal em `build/delivery/kairos-hosted-api-debug.apk`.

## Arquivos desta adaptação

Novos: `model/network/SpringSession.kt`, `model/auth/EncryptedSessionStore.kt`,
`model/auth/SpringAuthRepository.kt`, `model/cliente/PublishedCustomerRepository.kt`,
`SpringSessionTest.kt`, `EncryptedSessionStoreTest.kt` e este documento.

Alterados: `AuthDependencies.kt`, `AuthRepository.kt`, `SignedInUser.kt`,
`KairosApi.kt`, `HttpStockerHomeRepository.kt`, `ScannerRepository.kt`,
`CustomerRepository.kt`, `AuthViewModel.kt`, `CustomerViewModel.kt`,
`MainActivity.kt`, `CustomerActivity.kt`, `CustomerHomeView.kt`, `ScannerActivity.kt`,
`settings/SecurityView.kt`, `settings/ProfileView.kt`, `AuthViewModelTest.kt`,
`CustomerHomeTest.kt` e `docs/customer-flow.md`.
O cadastro acrescenta `model/auth/CustomerRegistration.kt` e
`androidTest/.../CustomerRegistrationUiTest.kt`, além de atualizar
`AuthEntryView.kt`, `SpringSessionTest.kt` e os adaptadores acima. Valida CPF,
data, código positivo e confirmação da senha; o servidor continua a autoridade
final. Não persiste CPF, nascimento ou senha para recriar a tela.
Uma falha de rede após o envio não prova que a conta não foi criada; o app pede
para tentar o login antes de reenviar. HTTP 201 seguido de falha na sessão
informa que a conta foi criada e solicita login, sem repetir o cadastro.
O teste isolado do cadastro passou no celular: valida rolagem, preenchimento de
todos os campos e envio ao repositório simulado. Nenhuma conta remota foi criada.
Após integrar o cadastro, a compilação principal e os 73 testes unitários passaram,
incluindo a resposta 404 para mercado inexistente. A análise Android anterior
terminou sem erros (98 avisos). O APK de entrega foi atualizado com essa versão.
O campo de nascimento usa `view/DateInputMask.kt`: teclado numérico e barras
automáticas em DD/MM/AAAA, com preservação do cursor durante edição. A validação
estrita da data continua antes do envio. O teste de formulário digita `01012000`
e confere `01/01/2000`, sem fornecer as barras.

O catálogo publicado é lido por fluxo em `PublishedCatalogReader.kt`, em vez de
montar toda a resposta JSON em memória e aplicar o limite genérico de 4 MiB.
A leitura mantém apenas ID, nome, marca e preço; ignora metadados não utilizados.
Tem limite próprio de 32 MiB e 10.000 produtos; se atingir a quantidade, a Home
avisa que o catálogo é parcial. Cada seção mostra até 40 cards e orienta a usar
a busca, que filtra todos os produtos carregados. A API ainda não tem paginação.
Esses limites não são uma substituição para paginação no backend.
Foram mantidos os 73 testes unitários passando e passaram seis testes no celular:
três da leitura do catálogo (resposta maior que 4 MiB, lista parcial e produto
inválido) e três da Home. O teste novo é `PublishedCatalogReaderTest.kt` em
`app/src/androidTest/java/com/example/kairos`. O catálogo real da conta do usuário
não foi consultado nestes testes; os cenários usam dados fictícios.

Notas fiscais e reportes continuam indisponíveis no contrato publicado, conferido
novamente no Swagger. A indicação dessa dependência aparece mesmo se o catálogo
falhar; não são enviados reportes de cliente pela rota de alertas de funcionário.
Os caminhos Kotlin seguem os pacotes existentes em `app/src/main/java/com/example/kairos`;
testes seguem os diretórios `test` e `androidTest` correspondentes.
