# Dados de preparação da feira — 06/11/2026

Os registros ficam no PostgreSQL local `kairos_dev`, nos volumes persistentes do
Docker. O app continua consultando os endpoints reais; não recebe listas fixas
nem usa o repositório de demonstração.

Esta é uma carga de preparação, com preços e saldos iniciais escolhidos para
apresentação. Não representa operações físicas ou dados importados de um mercado.
A empresa local foi nomeada `Mercado Kairos · Feira` para identificar o ambiente.

## Conteúdo

- 12 produtos adicionados, além do produto de desenvolvimento já existente.
- 3 novos setores: Mercearia, Bebidas, Limpeza e higiene; cada um com corredor,
  gôndola e estoque de origem.
- 6 alertas abertos, compartilhados pelas duas contas autorizadas nesses setores.
- 4 reposições por conta, 8 no total, registradas pelo endpoint mobile.
- As reposições baixaram os saldos de origem, aumentaram os saldos de destino e
  contabilizaram as avarias. Não houve inserção direta de histórico ou datas retroativas.

As reposições foram registradas em 02/11/2026. Estão na mesma semana da feira do
dia 6, conforme o calendário de São Paulo usado pela API. O histórico mostra cada
funcionário separadamente. As categorias, produtos e alertas continuam persistidos
após fechar o app ou reiniciar os contêineres.

## Execução e conferência

Com os serviços locais iniciados:

```powershell
.\.integration\kairos-inteligencia-artificial\.venv\Scripts\python.exe tools/prepare-fair-data.py
.\.integration\kairos-inteligencia-artificial\.venv\Scripts\python.exe tools/prepare-fair-data.py --apply
```

Sem `--apply`, o comando apenas confere o ambiente e informa o plano. Com a opção,
faz backup antes de escrever e só usa o banco local fixo. Não aceita URL remota.
As associações de conta/cargo já existentes são verificadas, não criadas ou elevadas.

Reexecutar não zera os saldos nem reabre alertas resolvidos. As movimentações usam
UUIDs estáveis e a idempotência da API. O teste de repetição confirmou os mesmos
produtos, alertas, reposições e saldos antes/depois. Não foram feitas chamadas a IA.

Backups ficam em `.integration/backups/`; o manifesto com os IDs dos registros
fica em `.integration/fair-data-manifest.json`. Ambos estão fora do Git. O primeiro
backup desta carga é `before-fair-20261102T171034Z.dump`.

## Resultado no app

As duas contas retornaram 6 alertas e 4 reposições semanais cada. Home, alertas e
histórico foram conferidos no aparelho; capturas em `build/qa/fair-home.png`,
`fair-alerts.png` e `fair-history.png`.

A carga expôs um erro no app ao interpretar SQL DATE serializado como timestamp.
O parser agora aceita os dois formatos e preserva o dia sem deslocá-lo para a
véspera. Build e testes unitários Android passaram após a correção.
