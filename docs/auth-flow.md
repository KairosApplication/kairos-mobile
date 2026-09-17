# Autenticação Firebase e perfis Firestore

## Organização MVVM

| Pasta | Arquivos | Responsabilidade |
| --- | --- | --- |
| model/auth | Registration, ProfileDetails | Validação dos campos do cadastro |
| model/auth | UserProfile, SignedInUser | Perfil Firestore sem senha, sessão identificada pelo UID String |
| model/auth | AuthRepository, FirebaseAuthRepository | Cadastro, login, restauração da sessão, recuperação e logout |
| model/auth | AuthGateway, FirebaseAdapters | Chamadas ao Firebase Auth e ao Firestore |
| model/auth | AuthDependencies | Inicialização do SDK e injeção dos adaptadores |
| viewmodel | AuthViewModel | Estado, navegação, erros, execução em background |
| view | MainActivity | Formulários básicos; recuperação por link |
| test/.../auth | FirebaseAuthRepositoryTest | Testes da coordenação, retomada de cadastro e dados |
| tools/firebase-tests | rules.test.mjs | Testes reais das regras no emulador Firestore |

## Configurar antes de testar com sua conta

1. No Firebase Console, crie ou selecione o projeto desejado.
2. Adicione um aplicativo Android com o pacote **com.example.kairos**.
3. Baixe seu google-services.json e coloque em **app/google-services.json**.
   O arquivo do Login.zip é para com.example.login e não serve diretamente para o Kairos.
   Não altere manualmente o pacote do JSON.
4. Em Authentication > Sign-in method, habilite **Email/Password**.
5. Em Authentication > Templates, revise remetente e mensagem de recuperação de senha.
6. Crie o **Cloud Firestore**, banco padrão **(default)**, escolhendo sua região.
7. Publique o conteúdo de **firestore.rules** na aba Rules do Firestore.
   Alternativamente, com Firebase CLI autenticado:
   `firebase deploy --only firestore:rules --project SEU_PROJECT_ID`
   Este projeto não publica regras automaticamente.
8. Sincronize o Gradle e execute o app. Ambos os builds, debug e release, usam Firebase real quando configurados.

Sem o JSON, o app compila e informa a configuração pendente. Não existe fallback de autenticação falsa.
O plugin Google Services só é aplicado quando app/google-services.json existe.
O JSON é configuração do cliente; não colocar chave privada de conta de serviço no Android.

## Fluxos

Cadastro cria a credencial no Firebase Auth e, em seguida, salva o perfil no Firestore.
A tela principal só é acessada depois de carregar um perfil completo.
Auth e Firestore não compartilham uma transação: em caso de falha na segunda etapa,
a conta permanece no Auth e a tela pede a conclusão do perfil. Login posterior e
restauração da sessão retomam essa etapa sem criar uma segunda conta.
Não se apaga uma conta automaticamente diante de erro de rede.

Uma transação Firestore cria users/{uid} e cpfClaims/{cpfNormalizado} juntos.
As regras exigem os dois documentos e impedem que outro usuário reutilize o CPF.
Os perfis são imutáveis neste primeiro fluxo; edição/exclusão deve atualizar também a reserva de CPF.
Uma reserva de outro usuário não pode ser consultada pelo app; CPF já reservado pode aparecer
como falha de permissão/conclusão do cadastro, sem expor os dados do titular.

Login usa email e senha no Firebase Auth e lê o perfil no servidor Firestore.
O Firebase mantém a sessão; na abertura do app ela é recarregada antes de ler o perfil.
Logout chama signOut. Não há armazenamento próprio de senha ou token.
A leitura do perfil exige rede; cache do Firestore é apenas em memória.

Recuperação usa sendPasswordResetEmail. O usuário abre o link do email, troca a senha
na página hospedada pelo Firebase e volta ao app para entrar.
Não há código numérico, senha nova na tela do app ou serviço SMTP próprio.
O app responde genericamente para email inexistente. Limites e validade do link são gerenciados pelo Firebase.

## Campos e alcance da migração

users/{uid} contém uid, name, lastName, birthDate, cpf, email, zipCode e plan.
birthDate usa AAAA-MM-DD; CPF e CEP são strings só com dígitos. Os limites dos campos
originais foram mantidos. CPF é validado por formato, não por dígitos verificadores.
O app valida a data; as regras conferem seu formato.
Senha vai exclusivamente para Auth e é excluída do documento. A validação básica exige
6 caracteres no cadastro; configure no Console uma política mais forte conforme necessário.

O plano é texto cadastral informado pelo usuário, sem conferir assinatura, cargo ou permissões.
Uma futura autorização de benefícios pagos deve ser controlada por servidor.

Esta mudança migra o fluxo de autenticação/perfil do app. **Não importa usuários antigos do
PostgreSQL, não altera a API e não migra produtos, estoque ou compras.**
Users.kt e os outros models relacionais continuam como representações do SQL, fora deste fluxo.
Firebase UID String não é o id SERIAL Int do PostgreSQL. Integrações futuras com a API precisam
de vínculo explícito e validação do token Firebase no servidor.
As senhas existentes no PostgreSQL não são automaticamente reconhecidas pelo Firebase.

## Verificação

Build e testes Kotlin:
`./gradlew.bat :app:testDebugUnitTest :app:assembleDebug`

Regras Firestore, sem conta real (Java 21+ e Node instalados):
`cd tools/firebase-tests`
`npm ci`
`npm test`
O projeto demo-kairos é local; a suíte não publica regras nem acessa seu banco remoto.

Teste manual após a configuração:
- Cadastrar uma conta; conferir perfil e reserva de CPF, sem campo password.
- Sair, entrar e reiniciar o app para verificar restauração.
- Solicitar recuperação, abrir o email e definir outra senha.
- Confirmar que a senha antiga falha e a nova permite login.
- Interromper a conexão após criar a conta e concluir o perfil ao voltar.
- Testar segundo usuário e confirmar que não lê o perfil do primeiro.

## Referências

- https://firebase.google.com/docs/android/setup
- https://firebase.google.com/docs/auth/android/password-auth
- https://firebase.google.com/docs/auth/android/manage-users
- https://firebase.google.com/docs/firestore/security/rules-fields
