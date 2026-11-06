# Notas fiscais com Firebase AI Logic

O Android envia a foto ao serviço gerenciado do Firebase AI Logic, usando
Gemini 3.8 Flash. Não exige novas rotas ou publicação do Spring Boot. O login
e o catálogo continuam usando a API Spring existente.

## Configuração

1. Usar o app Android `com.example.kairos` do projeto `germinimobile`, com seu
   arquivo de configuração Firebase. Ativar Firebase AI Logic com Google AI.
2. No desenvolvimento, iniciar o APK debug no aparelho. O SDK gera um segredo
   por instalação. Cadastrar esse valor em App Check > Apps > Kairos Mobile >
   Gerenciar tokens de depuração, com um nome que identifique o aparelho.
3. O segredo de depuração deve permanecer privado. Não versionar, inserir em
   BuildConfig nem distribuir junto ao APK. Reinstalar com limpeza de dados
   pode exigir novo cadastro do token.
4. A variante release usa Play Integrity, não o provedor de depuração. Antes
   de distribuir, configurar esse provedor para o aplicativo e sua assinatura
   no console e validar a distribuição usada.

A chave Gemini privada fornecida anteriormente não é usada pelo Android.
O SDK usa a configuração Firebase e seu proxy gerenciado.

## Comportamento e limites

A foto exige consentimento antes do envio. A leitura aceita JPEG de até 3 MB
e 16 megapixels, uma solicitação por vez, com espera máxima de 20 segundos
para validar o aparelho e mais 75 segundos para a leitura do modelo.
Descrições, quantidades e totais retornados são sugestões não verificadas.
Campos inesperados são descartados; o app não registra uma compra, valida
fiscalmente a nota nem movimenta estoque. O arquivo temporário da câmera é
removido ao concluir ou sair do fluxo.

O botão depende da configuração Firebase local; a autorização remota ainda
é validada na solicitação e pode falhar se AI Logic/App Check não estiverem
configurados. A variante isolada `.integration` não inicializa essa integração.

## Validação

Os testes unitários do parser cobrem quantidades fracionadas, valores
inválidos, respostas vazias, campos permitidos e limites de tamanho. O teste
real no serviço exige o token do aparelho registrado e uma foto autorizada.
Não confundir compilação bem-sucedida com validação de uma chamada remota.

O teste `FirebaseReceiptLiveTest` é ignorado por padrão. Para executá-lo,
instalar os APKs debug e androidTest no aparelho registrado e chamar o runner
com `-e class com.example.kairos.FirebaseReceiptLiveTest -e firebaseReceiptLive true`.
Ele valida App Check e envia somente uma imagem gerada com dois produtos
fictícios. A opção adicional `-e firebasePreflight true` habilita uma consulta
de diagnóstico ao modelo antes da leitura.

Em 05/11/2026, o App Check aceitou o token registrado do aparelho de teste.
O serviço recusou Gemini 2.5 Flash por indisponibilidade para novos usuários e
indicou Gemini 3.8 Flash; o aplicativo foi atualizado para esse modelo.
Compilação debug e 77 testes unitários passaram após a atualização. A leitura
real da imagem fictícia ainda não passou: Gemini 3.8 Flash retornou
indisponibilidade temporária. O aplicativo agora apresenta uma mensagem
específica para esse caso, distinta de configuração ou limite de uso.

Atualização de 05/11/2026: após a configuração, o usuário confirmou que a
leitura de nota por IA funcionou no celular. O fluxo atual do cliente preserva
esse caminho Firebase; consulte
[customer-features-integration.md](customer-features-integration.md).

Referências: https://firebase.google.com/docs/app-check/android/debug-provider
e https://firebase.google.com/docs/ai-logic/get-started?platform=android&api=dev.

## Falha de autorização após troca do identificador de depuração

Em nova conferência de 05/11/2026, a instalação do aparelho apresentava um
identificador de depuração diferente do salvo anteriormente. O teste real
falhou na obtenção do token do App Check, antes de chamar o modelo.
O motivo da mudança de identificador não foi determinado.

Foi restaurado, somente na preferência privada debug do mesmo aparelho, o
identificador previamente autorizado. Nenhum segredo foi embutido no APK,
commitado ou incluído neste documento, e a proteção não foi desativada.
O teste real com `firebaseReceiptLive=true` e `firebasePreflight=true` passou:
App Check, consulta ao modelo e leitura dos dois itens da nota fictícia.

`FirebaseReceiptAi` agora valida o App Check antes de enviar a imagem. Falhas
de autorização e tempo esgotado na validação têm mensagens próprias;
uma falha genérica de leitura não afirma mais que o Firebase está desativado.
Reinstalação com limpeza de dados pode gerar outro identificador, exigindo
novo cadastro de depuração. Atualizar o APK deve preservar os dados do app.

Na primeira repetição após instalar a versão com validação explícita,
App Check autorizou o aparelho, mas a leitura do modelo excedeu 75 segundos.
Isso é uma falha de tempo de resposta, distinta da recusa de autorização
inicial. Compilação debug e 76 testes unitários passaram.
Uma segunda tentativa retornou indisponibilidade temporária do modelo.
A autorização foi corrigida, mas a leitura remota não ficou estável durante
a validação final. O teste bem-sucedido anterior não elimina essa pendência.
