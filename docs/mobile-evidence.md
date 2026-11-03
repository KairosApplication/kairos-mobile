# Evidências de recursos do mobile

Atualização de 02/11/2026. GPS está fora do escopo solicitado pelo responsável.

## Avatar

Em Ajustes → Perfil, o lápis abre as ações Escolher foto e Remover foto. O seletor
de fotos do Android não exige acesso amplo à galeria. A imagem é recortada no centro,
reduzida para 512 × 512 e gravada como JPEG sem os metadados originais. O avatar
persiste na área privada do aplicativo, separado por conta. Não existe upload ou
sincronização entre aparelhos; desinstalar o aplicativo remove o arquivo local.

Implementação: `AvatarStore`, `ProfileView` e o seletor de `MainActivity`.
O teste instrumentado `AvatarNotificationsTest` verifica persistência, isolamento,
remoção e rejeição de gravação após mudança da sessão, sem usar fotos pessoais.
Para o documento, anexar o perfil com a foto escolhida pelo próprio responsável.

## Capturas do chatbot

A janela do chatbot permite capturas de tela. A remoção de FLAG_SECURE é restrita
ao chat; não altera a proteção dos formulários de senha ou da câmera. Uma captura
de conversa só comprova geração real quando houver uma resposta do serviço; a
tela vazia e os testes com repositórios fictícios não são essa evidência.

O serviço já teve validação completa com Groq, HTTP 200, resposta aprovada e
pergunta/resposta persistidas, conforme o registro em `service-integration.md`.
O destino atual das conversas é MongoDB Atlas, banco `kairos_ai`, pela API de
persistência. Detalhes da correção e configuração em [chat-atlas.md](chat-atlas.md).
Não incluir credenciais, dados pessoais de terceiros ou mensagens privadas nas evidências.

## Notificações Android locais

`LocalAlertNotifications` cria canais do Android, pede POST_NOTIFICATIONS a partir
do Android 13 e abre a aba Alertas ao tocar no aviso, respeitando a conta ativa.
Som e vibração selecionam canais com as respectivas configurações; o Android pode
silenciá-los conforme as escolhas do usuário e o modo Não perturbe.

Ao receber um novo conjunto de alertas da API, o app compara os IDs com o conjunto
anterior por conta. A primeira consulta cria uma referência, sem notificar alertas
antigos. Novos IDs geram notificação quando Notificações Gerais e Reposições estão
ativadas e a permissão foi concedida. Reconsultar o mesmo conjunto não repete o aviso.

Em Ajustes → Notificações, Enviar notificação de teste publica um aviso identificado
como teste no próprio Android. Esse botão não cria alertas no banco e não usa IA.
Sua captura comprova notificação local exibida pelo Android, não push remoto.
Não há Firebase Cloud Messaging, execução periódica em segundo plano ou garantia
de receber alertas com o aplicativo fechado. O teste instrumentado verifica o
aviso no NotificationManager e a detecção de novos IDs, em pacote isolado.

No Samsung SM-M236B com Android 14, o botão foi acionado no aplicativo real,
a permissão foi concedida no diálogo do Android e o aviso “Kairos · Notificação
de teste” foi confirmado na barra de notificações. A captura deve incluir apenas
esse card, sem e-mails ou notificações particulares de outros aplicativos.

Validação da entrega: build debug e testes unitários aprovados; os dois testes
instrumentados de avatar e notificações passaram no pacote isolado. O teste de
notificação aguarda a publicação assíncrona pelo Android antes de verificar o aviso.

## Evidências ainda dependentes da apresentação

- Captura de resposta real do chatbot no celular.
- Foto de perfil selecionada pelo responsável e exibida após reabrir o app.
- Captura da notificação Android local, com a origem de teste descrita no relatório.
- Testes e capturas em tablet, se continuarem exigidos pelo professor.
- Confirmação de acesso dos professores aos repositórios e identificação do APK utilizado.
