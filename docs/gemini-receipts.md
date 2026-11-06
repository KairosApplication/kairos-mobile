# Leitura de notas com Gemini

> Atualização: o aplicativo agora usa o SDK Firebase AI Logic diretamente para
> interpretar fotos, sem novas rotas ou publicação do Spring Boot. Consulte
> [Configuração atual](firebase-ai-receipts.md). As instruções abaixo descrevem
> a alternativa anterior de servidor, preservada para referência.

## Entrega

O Android fotografa os itens da nota após confirmação explícita do usuário,
envia um JPEG ao Spring Boot com a sessão Spring existente e mostra as descrições,
quantidades e totais retornados como sugestões não verificadas. A foto temporária
é removida após o envio ou ao encerrar a tela. Não há chave Gemini no APK.

Esta entrega faz interpretação, não registro de compra, validação fiscal ou
movimentação de estoque. O botão de avaria continua independente e indisponível.

## Ativação no serviço hospedado

O código do servidor está no patch independente
`docs/delivery/springboot-gemini-receipts.patch`, para o repositório
`KairosApplication/kairos-springboot`. Não aplicar os patches antigos de
Firebase/mobile para habilitar esta função. Este patch não cria tabelas nem
exige as migrações V5/V6 da implementação local antiga.

1. Em uma branch do backend, conferir/aplicar o patch com `git apply --check`
   e depois `git apply`, informando o caminho do arquivo recebido.
2. Executar os testes do backend e revisar a alteração de segurança.
3. Definir `GEMINI_API_KEY` no gerenciador de segredos do ambiente do Spring Boot.
   Usar a mesma chave privada já fornecida pelo usuário; não colocá-la em código,
   PR, argumentos de comando, configuração Android ou arquivos versionados.
4. Definir `GEMINI_VISION_MODEL=gemini-2.5-flash` (também é o padrão desta entrega).
5. Publicar a nova versão do Spring Boot pelo processo existente do projeto.
6. Com uma conta CUSTOMER real, conferir capabilities e a leitura de uma foto
   autorizada. Instalar o APK atualizado e tocar em Atualizar na Home.

O servidor hospedado não foi alterado nesta tarefa. Instalar apenas o APK não
publica essas rotas nem configura o segredo no servidor.

## Contrato

- `GET /api/v1/customer/receipts/capabilities`: exige CUSTOMER e cadastro SQL;
  retorna `photoInterpretation`, `purchaseConfirmation=false`, `qrPreview=false`.
- `POST /api/v1/customer/receipts/interpret`: mesma sessão e papel; corpo JSON
  `imageBase64` com JPEG de até 3 MiB (base64 até 4 MiB). Sem userId/companyId
  fornecidos pelo app. A identidade é resolvida a partir da sessão autenticada.
- Resposta: `accessKey` opcional e `items` com `description`, `quantity`, `amount`.
  `amount` é o total da linha, nunca um preço unitário inferido.
  `source=AI_SUGGESTION_UNVERIFIED` deixa explícito que requer conferência.
- Valores ilegíveis/fora dos limites são nulos. A chave deve conter 44 dígitos
  visíveis; isso não comprova autenticidade fiscal.

As rotas têm autorização explícita em `SecurityConfig`. São compatíveis com o
Bearer Spring usado pelo app; cookies continuam sujeitos ao CSRF existente.
O controle de uso compartilhado usa Redis (uma solicitação por usuário a cada
10 segundos); a instância limita duas chamadas simultâneas. Há limite de imagem
de 16 megapixels, saída do provedor até 256 KiB e até 100 itens interpretados.
Não há retentativa automática, gravação da imagem, compra ou alteração de estoque.

O Gemini recebe texto de instrução e imagem inline via HTTPS, com chave somente
no header `x-goog-api-key`. Usa saída JSON estruturada e valida os dados novamente
no servidor. Referências: [imagens](https://ai.google.dev/gemini-api/docs/image-understanding)
e [JSON estruturado](https://ai.google.dev/gemini-api/docs/structured-output).

## Android e disponibilidade

A Home habilita Escanear nota fiscal somente quando o servidor anuncia a leitura.
Uma rota ausente, acesso negado ou leitura desativada não é tratado como sucesso.
O registro de compras não é habilitado com essa capacidade.

A tela de leitura funciona sem carregar todo o catálogo, pois a interpretação
da imagem não depende de vincular produtos. Se o catálogo da Home falhar, mas
a capacidade de leitura estiver ativa, ela continua disponível com aviso.
Não há confirmação fictícia de compra salva: a ação final é Concluir leitura.

## Validação executada

- Android: 73 testes unitários passaram; quatro testes da Home passaram no celular,
  incluindo habilitação da leitura sem habilitar compras ou avarias.
- APK principal compilado e lint sem erros (97 avisos). A busca pelo valor da
  chave privada no código e recursos do APK gerado não encontrou a chave.
- Backend: três testes do adaptador legado, quatro de autorização/resolução da
  conta e controle de uso, dois de segurança HTTP/JWT e um teste real de visão.
  As verificações de estilo também passaram.
- Teste real: imagem inteiramente sintética, sem dados pessoais; o Gemini 2.5 Flash
  retornou os dois itens visíveis. Nenhuma nota do usuário foi enviada.
- O 3.8 Flash respondeu com sobrecarga/timeout. O 2.5 Flash foi escolhido após
  conferir disponibilidade. Um limite de array no schema foi removido após
  rejeição por complexidade do provedor; o limite de 100 continua validado no
  servidor. O schema corrigido foi usado no teste real aprovado.
- O teste real é opt-in (`integration.gemini=true`) e exige segredo no ambiente;
  testes normais não consomem Gemini. Não foram criadas contas nem compras remotas.
- O patch foi conferido com `git apply --reverse --check` na cópia modificada,
  sem aplicar/reverter alterações. Deve ser revisado novamente no checkout alvo.

## Arquivos

Android alterados: `CustomerRepository.kt`, `PublishedCustomerRepository.kt`,
`CustomerActivity.kt`, `CustomerHomeView.kt`, `ReceiptScannerActivity.kt` e
`CustomerHomeTest.kt`. Os diretórios/pacotes existentes foram preservados.

Backend para publicação: novos `receipt/GeminiReceiptReader.java`,
`receipt/CustomerReceiptController.java`, três classes de teste e duas regras
adicionais em `config/SecurityConfig.java`. Sem dependências novas no pom.

Somente na cópia local de compatibilidade, `mobile/ReceiptVisionService.java`
agora delega ao leitor compartilhado, preservando o fluxo antigo e seus testes.
Essa refatoração não faz parte do patch independente, pois a main publicada
não possui o módulo mobile legado. O arquivo privado ignorado `gemini.env`
teve somente o modelo atualizado; sua chave não foi copiada para a entrega.
