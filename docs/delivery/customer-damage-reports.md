# Entrega: relato de avaria do cliente

O Android foi conectado a uma operação específica para cliente. A API precisa
receber a alteração deste pacote e ser publicada para habilitar o botão.
Nenhum backend ou banco remoto foi alterado durante a preparação.

## Aplicar no repositório kairos-springboot

O patch foi preparado sobre `main` no commit
`b99a91a5919af5cdab179617b25ca7efcf042b14` e contém somente o fluxo de avaria.
Favoritos continuam locais; não há ofertas nem mudanças na leitura de notas.

```powershell
git apply --check customer-damage-reports.patch
git apply customer-damage-reports.patch
./mvnw.cmd -Ppostgres-tests "-Dtest=CustomerDamageReportPostgresTest" test
./mvnw.cmd verify
```

Revisar o diff e publicar pelo processo habitual do backend. A migração Flyway
`V5__customer_damage_reports.sql` cria uma tabela, índice e trigger novos.
Se já existir V5 em outra branch/ambiente, alinhar a versão antes de publicar.
Não executar migrations manualmente nem habilitar `ddl-auto=update`.

Não são necessárias novas chaves, credenciais de IA ou permissões de gerente
no aplicativo cliente. A autenticação é o mesmo Bearer da API Spring existente.
O CSRF existente permanece para chamadas autenticadas por cookies.

## Rotas

Todas exigem `CUSTOMER` e usam o cliente/mercado encontrados na sessão:

- `GET /api/v1/customer/damage-reports/capabilities`: versão 1, mercado e suporte a foto.
- `GET /api/v1/customer/damage-reports/options`: produtos e localizações do próprio mercado.
- `POST /api/v1/customer/damage-reports`: registra e encaminha o relato.
- `GET /api/v1/customer/damage-reports`: últimos 100 relatos da própria conta, com status atual do alerta.

```json
{
  "commandId": "258e5269-c619-4a1c-ad50-b53342e0162a",
  "productId": 17,
  "shelfId": null,
  "problemType": "DAMAGED",
  "description": "Embalagem aberta",
  "imageBase64": "<JPEG opcional em base64>"
}
```

`problemType`: `DAMAGED`, `TAMPERED`, `EXPIRED` ou `OTHER`. Descrição de até
500 caracteres. Foto opcional JPEG de até 3 MiB e 4 milhões de pixels, validada
e regravada sem metadados pelo servidor. O Android ajusta orientação e tamanho.

```json
{
  "protocol": "b8b80b83-b86d-47c8-b8ec-8c0361fe3aa5",
  "alertId": 91,
  "status": "OPEN"
}
```

Protocolo é confirmado somente após persistência do relato e alerta na mesma
transação. Reenvios com o mesmo comando e conteúdo retornam o mesmo protocolo;
conteúdo diferente com o mesmo comando retorna 409. Cliente e mercado nunca
são aceitos do corpo da requisição.

A trigger seleciona o repositor atribuído à gôndola ou ao corredor no mesmo
mercado. Sem localização informada, encontra uma gôndola válida do produto
com repositor e identifica no alerta que o cliente não informou o local.
Sem vínculo válido, retorna conflito e não salva relato nem alerta incompleto.
Não altera quantidades, estoque, compras ou reposições.

O alerta entra na lista existente do repositor. Sua resolução atualiza o
status exibido ao cliente em Compras → Seus reportes. O registro local de
confirmação só é gravado depois do protocolo retornado pela API.
A foto fica armazenada no relato; esta entrega não adiciona visualizador de
anexos à tela do repositor.

## Android

- `CustomerDamageReports.kt`: contrato do cliente, validação e proteção contra troca de conta.
- `PublishedCustomerRepository.kt`: detecta a operação publicada e consulta relatos.
- `DamageReportActivity.kt`: formulário, foto opcional, revisão, envio e protocolo.
- `CapturedPhoto.kt`: preparo de JPEG compartilhado com o scanner do repositor.
- `ShelfCameraActivity.kt`: modo de captura de avaria na câmera existente.
- `CustomerHomeView.kt`: acompanhamento independente do histórico de compras.
- `CustomerRepository.kt`, `CustomerActivity.kt` e `customer-ui.xml`: suporte ao fluxo.

Sem a nova rota publicada, a Home continua funcionando e o reporte permanece
indisponível. Após a publicação, atualizar a Home do cliente.

## Ligacao com o repositor

- `GET /api/v1/alerts/customer-reports`: metadados de relatos abertos atribuidos ao funcionario autenticado, restritos ao mesmo mercado.
- `PATCH /api/v1/alerts/customer-reports/{alertId}/resolve`: confirma a resolucao e preserva a primeira data em reenvios.
- As respostas nao incluem foto nem dados pessoais do cliente e usam `Cache-Control: no-store`.
- `StockerDamageReports` associa os metadados pelo ID aos alertas ja filtrados. Nao cria alertas, nao infere relatos por descricao e nao modifica entradas.
- Relatos usam `OBSERVED`, sem analise de reposicao comum, e abrem os detalhes de avaria existentes.
- O mobile exige o novo contrato quando existem alertas. Erros 403/404 nao sao ignorados: sem metadados, nao e seguro apresentar uma avaria como reposicao comum.
- A resolucao so e confirmada depois da resposta com o mesmo `alertId` e `status=RESOLVED`; o recarregamento da Home foi preservado.
- A V5 nao substitui migrations existentes. O teste especifico de atividade diaria continua com target 4; a criacao integral espera cinco migrations e 24 tabelas nesta base.

## Validacao desta revisao

Veja `customer-damage-review.md` para comandos e resultados executados nesta maquina.
Resultados historicos de outros ambientes nao comprovam esta revisao.
A API esta somente preparada localmente. O teste completo no ambiente publicado exige alinhamento e publicacao pelo responsavel.
