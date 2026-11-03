# Persistência das conversas no MongoDB Atlas

O chatbot usa o banco **kairos_ai no MongoDB Atlas**. O app acessa o serviço de chat,
que grava pela API de persistência; a coleção `conversations` contém o histórico.
A URI autenticada existe somente no servidor, no arquivo privado e ignorado
`.integration/secrets/mongodb-atlas.json`. Ela não vai no APK, no Git ou nos logs públicos.

Use `tools/mongodb-atlas.example.json` como modelo, preenchendo as credenciais
somente na cópia privada. O script recusa configuração ausente e não inicia
MongoDB local como alternativa.

```powershell
./tools/start-local-chat.ps1 -Restart
./tools/connect-local-services.ps1
```

`-Restart` encerra apenas os processos identificados do chat e da API de persistência
nas portas 8000 e 8082, aplica a configuração Atlas e confere os dois health checks.
Sem `-Restart`, um serviço de persistência já ativo é recusado para evitar manter
silenciosamente a configuração anterior. O backend de negócio deve estar ativo.

## Correção do armazenamento anterior

O uso anterior de `kairos_chat_local` foi substituído pelo Atlas. Foram copiadas
as oito conversas e nove execuções de IA pertencentes às duas contas autorizadas.
A migração manteve IDs, mensagens e escopos de acesso, verificou conflitos antes
de inserir e não sobrescreveu documentos existentes. Uma cópia BSON privada ficou
em `.integration/backups/chat-before-atlas-*`; o volume local original foi preservado,
com o contêiner MongoDB antigo desligado.

## Validação

- Autenticação e consulta de coleções no Atlas aprovadas.
- Nova conversa criada pela API com identidade Firebase e localizada diretamente no Atlas.
- Uma pergunta real ao Groq retornou HTTP 200 e resposta aprovada; as duas mensagens
  foram confirmadas no Atlas e lidas novamente pela API.
- A nova conversa não foi gravada no banco local.
- A tentativa de leitura dessa conversa pela outra conta retornou HTTP 404.
- Depois de desligar o MongoDB local e reiniciar os serviços, chat e persistência
  continuaram com health checks HTTP 200.

As credenciais do Atlas não mudam o isolamento por conta: o serviço continua
validando identidade e escopo de acesso antes de listar ou abrir uma conversa.
Os serviços ainda executam no computador de desenvolvimento; usar Atlas não
publica automaticamente o backend na internet.
