# Conta do cliente e relato de avaria

Conferência em 05/11/2026. Nenhuma alteração ou publicação de backend foi feita.

## Regras do aplicativo

- Nome, e-mail e senha são os únicos dados editáveis previstos.
- CPF e nascimento permanecem somente para consulta. Mercado vem do vínculo da conta.
- Não há recuperação de senha ou e-mail no escopo. A opção e suas chamadas foram removidas.
- Uma alteração só deve ser apresentada como salva após confirmação do servidor.

## Dados e edição de conta

Na API Spring publicada em `https://d3bmuckfmecew.cloudfront.net`,
`GET /api/v1/auth/me` retorna e-mail e permissões. `SpringSession.identity()`
não recebe nome, CPF, nascimento ou mercado para preencher os componentes compartilhados.

O contrato `UserUpdateRequest` contém apenas `email` e `cpf`, e
`/api/v1/users/**` exige gerente, exceto o cadastro público.
`UserResponse` expõe somente ID, nome e e-mail. Não existe nesse contrato
uma operação de edição própria de nome/senha para cliente ou repositor.
O próprio frontend documenta a limitação em
`docs/account-settings-session.md` do repositório `kairos-frontend`.

Por isso, `SpringAuthRepository` mantém as alterações indisponíveis;
`SecurityView` não permite editar CPF ou nascimento e não simula sucesso local.
Para concluir a integração, é necessário identificar uma operação acessível à
própria sessão que retorne os dados pessoais e o vínculo com o mercado e
permita as três alterações solicitadas. Não usar acesso de gerente no cliente.

## Fluxo de foto do repositor

Rastreado tanto no workspace quanto em `kairos-mobile/main`:

1. `ShelfCameraActivity`: captura com CameraX.
2. `ScannerActivity`: encaminha o arquivo para `ScannerViewModel`.
3. `ScannerViewModel.analyze()`: verifica a imagem, corrige orientação e compacta JPEG.
4. `HttpScannerRepository.analyze()`: envia para a origem da IA:

```http
POST /api/v1/me/shelf-analyses
Content-Type: application/json
```

```json
{
  "shelfId": 123,
  "mimeType": "image/jpeg",
  "imageBase64": "<JPEG em base64>"
}
```

O retorno esperado é uma análise com produtos candidatos, avisos e
`stockChanged: false`. Essa chamada não confirma reposição nem comprova
persistência de relato/alerta. Não foi encontrada em `main/app/api.py` do
repositório `kairos-inteligencia-artificial` na versão conferida.

As confirmações de reposição são outra operação. Não devem ser chamadas
para reportar avaria de cliente, pois reportar não é repor estoque.

`POST /api/v1/alerts/registration`, no `AlertController` conferido, cria
alerta via `AlertService.save`; o acesso exige funcionário ou gerente.
O nome pode estar sendo usado para outro fluxo em outra versão. Precisamos
do arquivo/versão ou rota em uso para resolver essa diferença com o backend.

A captura e o preparo da imagem podem ser compartilhados quando a operação
de relato for identificada. A análise por IA sozinha não substitui o registro
do relato, o vínculo ao mercado e o alerta ao repositor. Não foi habilitado
um envio que retorna sucesso sem persistência confirmada.

## Validação

Compilaram o APK debug e o pacote de testes Android. Passaram 76 testes
unitários, sem falhas. O teste unitário específico de envio de recuperação
foi removido junto com a funcionalidade, assim como os métodos equivalentes
dos dublês de teste. Nenhuma conta real foi alterada durante essa validação.
Também passaram 5 testes no aparelho: cadastro do cliente, configurações em
tema claro/escuro, isolamento da navegação por conta e formulários de edição
com repositório fictício. Esses testes não comprovam edição na API publicada.
O APK atualizado foi instalado preservando os dados do aplicativo.

## Arquivos desta revisão

Caminhos relativos a `app/src`:

- `main/java/com/example/kairos/view/AuthEntryView.kt`: remove o link de recuperação.
- `main/java/com/example/kairos/view/MainActivity.kt`: remove a tela de recuperação.
- `main/java/com/example/kairos/viewmodel/AuthViewModel.kt`: remove o estado e a ação de recuperação.
- `main/java/com/example/kairos/model/auth/AuthRepository.kt`, `AuthGateway.kt`,
  `FirebaseAuthRepository.kt`, `FirebaseAdapters.kt`, `SpringAuthRepository.kt`:
  removem os contratos e chamadas de recuperação.
- `main/res/values/preferences.xml`: ajuda sem indicação de recuperação.
- `test/java/com/example/kairos/auth/AuthViewModelTest.kt`,
  `FirebaseAuthRepositoryTest.kt`, `BusinessIdentityTest.kt`,
  `BusinessAvailabilityTest.kt`, `AccountRepositoryTest.kt` e
  `androidTest/java/com/example/kairos/CustomerRegistrationUiTest.kt`:
  adequação dos dublês ao contrato sem recuperação.

Este documento registra as pendências verificadas. Os componentes de conta e
o envio de avaria não foram habilitados com rotas inexistentes ou sem permissão.
