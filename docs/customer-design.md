# Visual e configurações do cliente

## Interface

Home, Favoritos e Compras usam cabeçalho verde, fundo off-white, tipografia
Montserrat, cards com bordas discretas e navegação inferior fixa. O destaque
da Home abre a leitura de nota existente. A busca continua no catálogo real;
favoritos continuam locais por conta. Ofertas e chatbot não fazem parte deste
fluxo. O ícone de Compras usa um vetor com traço e transparência, legível nos
dois temas.

O cabeçalho compartilhado das telas de leitura de nota e reporte de avaria
também recebeu o mesmo acabamento, com retorno compacto e conteúdo separado
do cabeçalho. A disponibilidade das operações continua respeitando a API atual.

## Configurações reutilizadas

- `SettingsMenuView`: menu compartilhado entre cliente e repositor.
- `ProfileView`: componente existente com resumo de cliente; exibe favoritos
  locais e compras quando os dados existem, sem contadores de trabalho.
- `SecurityView` e `AccountEditorDialog`: mesmos componentes e permissões.
  Na sessão Spring atual, dados de acesso permanecem em consulta; nenhuma
  edição fictícia ou gravação só local substitui uma alteração da conta.
- `SettingsContentView`: aparência, preferências de notificação e ajuda.
  O cliente usa perguntas próprias e não recebe o botão de atendimento por chat.
- `StockerPreferences`: mesmo armazenamento e aplicação de temas já existentes,
  por UID; inclui a preferência de atividades do cliente. Essas opções não
  implementam um novo serviço de notificações push.
- `AboutView`: página existente de versão e informações do Kairos.

A tela/destino de configurações e a busca são restaurados somente para a
mesma conta. Logout e troca de conta limpam a navegação anterior. O tema é
aplicado também no fluxo CUSTOMER_HOME. O chatbot não recebe a identidade do
cliente no MainActivity.

## Arquivos

| Arquivo | Mudança |
| --- | --- |
| `view/CustomerHomeView.kt` | Home, catálogo, favoritos, compras, navegação e integração das configurações |
| `view/CustomerActivity.kt` | Cabeçalho comum dos formulários do cliente |
| `view/MainActivity.kt` | Estado da conta, restauração, tema e exclusão do cliente no chatbot |
| `view/StockerHomeView.kt` | Reutiliza o menu compartilhado |
| `view/settings/SettingsMenuView.kt` | Novo menu comum |
| `view/settings/SettingsContentView.kt` | Ajuda e notificações adaptadas ao cliente |
| `view/settings/ProfileView.kt` | Resumo de cliente e apresentação do perfil |
| `view/settings/SecurityView.kt` | Consulta da conta sem ações de edição indisponíveis |
| `res/values/customer-ui.xml` e variante noturna | Textos, IDs e fundo do cliente |
| `res/drawable/customer_favorite.xml` e `customer_receipt.xml` | Ícones vetoriais |
| `androidTest/.../CustomerDesignTest.kt` | Navegação, perfil, ajuda, preferências, restauração e temas |
| `androidTest/.../StockerSettingsTest.kt` | Destino correto da página Sobre já existente |
| `debug/.../AccountTestActivity.kt` | Mantém a tela ligada apenas no host de testes |

Os caminhos de código acima são relativos a `app/src/main/java/com/example/kairos`
e os caminhos de recursos a `app/src/main`. Não há alteração de backend.

## Validação

APK e pacote de testes compilaram. Passaram 77 testes unitários e 16 testes
no aparelho, incluindo navegação dos dois perfis, persistência de favoritos e
preferências, restauração por conta e telas em tema claro/escuro. Um teste
antigo de alertas perdeu o foco da janela na primeira tentativa; passou na
repetição com o aparelho desbloqueado. O host debug mantém a tela ligada
somente durante os testes.

As prévias foram geradas com contas/produtos fictícios em
`build/delivery/customer-design/`. Foram revisados os temas, alinhamentos,
contraste dos ícones e os estados de dados indisponíveis. O APK atualizado foi
instalado no celular; nenhuma sessão ou credencial foi embutida no aplicativo.
