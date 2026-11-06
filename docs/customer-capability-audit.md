# Verificação dos serviços de cliente — 05/11/2026

> O escopo atual, com favoritos locais e sem ofertas ou novas rotas de backend,
> está em [customer-features-integration.md](customer-features-integration.md).

> Atualização: a interpretação de fotos agora utiliza Firebase AI Logic no
> Android. Consulte [a configuração atual](firebase-ai-receipts.md). Os resultados
> abaixo registram a auditoria do contrato Spring, não essa integração adicional.

Esta verificação é de leitura. Não foram criadas contas, alterados dados remotos,
publicadas APIs ou habilitados botões sem integração correspondente.

## Serviços conferidos

- Spring Boot: https://d3bmuckfmecew.cloudfront.net e seu `/v3/api-docs`.
- FastAPI: https://kairos-inteligencia-artificial.onrender.com.
- Código `main` de `KairosApplication/kairos-springboot`,
  `KairosApplication/kairos-inteligencia-artificial` e `KairosApplication/kairos-api-ia`.

Ambos os `/health` retornaram HTTP 200 ao final da verificação. Inicialmente
o Render demorou além do timeout; isso não foi considerado prova de falta de rota.
A versão exata implantada não foi identificada por commit.

## IA e nota fiscal

A [FastAPI principal](https://github.com/KairosApplication/kairos-inteligencia-artificial/blob/main/app/api.py)
desativa `/docs` e `/openapi.json` explicitamente. Sua ausência não basta para
concluir que uma operação não existe. A inspeção do código encontrou rotas de
chat/conversas, sem manipulador de nota fiscal ou análise de gôndola nessa main.

Verificações anônimas no Render:

| Caminho | Resposta |
| --- | --- |
| `/api/v1/me/chat` | 401, exigindo login |
| `/api/v1/me/shelf-analyses` | 404 |
| `/api/v1/me/receipts/interpret` | 404 |

O 404 desses caminhos não exclui uma implementação em outra branch, serviço ou
URL; os contratos conferidos não oferecem uma rota utilizável pelo app atual.
Nenhuma imagem foi enviada e nenhum modelo pago foi chamado.

O serviço [kairos-api-ia](https://github.com/KairosApplication/kairos-api-ia/blob/main/README.md)
é um CRUD de dados de IA/MongoDB. Seu README declara que não executa inferência.
Ele usa token de serviço, que não deve ser colocado no APK.

A [autenticação da FastAPI](https://github.com/KairosApplication/kairos-inteligencia-artificial/blob/main/app/auth.py)
interpreta Bearer como token Firebase; o fluxo Spring usa cookies e CSRF. O
Android atual usa Bearer Spring. Portanto, o chat também precisa alinhar esse
contrato antes de considerar uma conexão autenticada ponta a ponta concluída.
Não se deve tentar enviar a senha do banco, uma chave Gemini ou um token de
serviço como substituto da identidade do usuário.

## O que significa avaria no banco atual

No [schema oficial](https://github.com/KairosApplication/kairos-springboot/blob/main/src/main/resources/db/migration/V1__initial_schema.sql),
`qnt_avarias` pertence a `restockings`, com funcionário, estoque, prateleira,
produto e quantidade reposta obrigatórios. Não é uma tabela independente de
relatos do cliente.

O [serviço de reposição](https://github.com/KairosApplication/kairos-springboot/blob/main/src/main/java/com/kairos/kairosapipostgres/service/RestockingService.java)
valida avarias entre zero e a quantidade reposta, retira a quantidade bruta do
estoque de origem e adiciona à prateleira somente a parte aproveitável. Criar
uma reposição para representar um relato de cliente produziria uma operação
de estoque que não aconteceu.

Existe `alerts`, que pode ser reutilizada como ocorrência aberta. A rota geral
de alertas está autorizada para funcionário/gerente, não para cliente. A
verificação também encontrou ausência de validação de empresa no serviço geral
de alertas; simplesmente liberar essa rota para CUSTOMER não é uma integração
adequada. O relato deve derivar cliente/empresa da sessão e validar produto e
localização no servidor.

## Contrato mínimo proposto para o reporte

Proposta de evolução, não rota já publicada:

- `POST /api/v1/customer/damage-reports`, autenticado com a sessão Spring atual.
- Entrada: `commandId` UUID, `productId`, localização quando conhecida,
  `problemType` e descrição. Cliente, empresa, status e responsável não vêm
  do formulário.
- O servidor resolve CUSTOMER pela identidade autenticada e valida o escopo.
- Uma transação registra a ocorrência e cria um alerta OPEN, reaproveitando
  os alertas existentes. Não altera `qnt_avarias` nem quantidades de estoque.
- Retorna protocolo e estado após persistir; repetir o mesmo commandId não cria
  uma segunda ocorrência. Dados diferentes com o mesmo comando geram conflito.
- `GET` da mesma rota retorna somente os relatos do próprio cliente.

Um alerta simples pode usar a tabela existente. Para manter autoria, protocolo,
histórico próprio, tipo do problema e deduplicação, o fluxo completo precisa
de metadados ou de uma pequena associação de reportes a alertas. O schema atual
de alerts não contém todos esses dados. Localização opcional exige também
revisar a restrição de prateleira obrigatória antes de publicar a migração.

Já existe um rascunho local em `mobile/CustomerMobileService.java` e na migração
V6 dentro de `.integration/kairos-springboot`, mas ele pertence ao desenho antigo
Firebase/mobile e inclui outras funções. Não é uma alteração publicada e não
deve ser aplicado inteiro no banco remoto para resolver somente avarias.
É necessário extrair/adaptar o trecho de reportes ao login Spring existente e
revisar a sequência de migrações do ambiente de destino.

## Resultado para o Android

Há uma adição pequena e viável para o reporte, reutilizando os alertas e as
telas existentes, mas ela passa pelo backend autenticado. Não há gravação direta
PostgreSQL pelo APK e não se contabiliza relato como reposição confirmada.

Para nota fiscal, ainda é necessário fornecer uma rota de interpretação e uma
rota para registrar a compra confirmada pelo cliente. Leitura por IA e registro
da compra são operações distintas. A chave/modelo da IA permanecem no servidor.
