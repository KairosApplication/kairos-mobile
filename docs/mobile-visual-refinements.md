# Revisão visual e navegação de alertas

## Alterações

- Engrenagem e relógio selecionados usam recortes transparentes reais. A tintura
  global que apagava o centro e os ponteiros foi removida. Os vetores inativos usam
  um token próprio de cor; ícones ativos e indicador usam o acento do tema.
- O tema escuro usa superfícies verdes neutras, cabeçalhos menos saturados,
  bordas discretas, texto secundário suave e cores semânticas de status.
  A câmera recebe um tile verde escuro e ícone verde claro; fotos de produtos
  mantêm um fundo neutro claro para preservar contraste e cores da imagem.
- `InfoCardDialog` reutiliza `AccountUiStyle` para orientar a verificação de
  gôndolas e apresentar detalhes dos alertas, com conteúdo rolável, cantos
  arredondados, hierarquia de texto e botões de altura confortável.
  Na revisão dos diálogos, foram removidos os ícones decorativos de todos os
  detalhes/avisos e o subtítulo redundante da verificação de gôndola. Voltar
  passou a ser uma ação plana. Botões e janela não usam elevação nem animação
  nativa de sombra.
- O filtro do histórico reutiliza essa mesma superfície: título e orientação,
  seletores arredondados para período/gôndola/ordenação e ações de Aplicar,
  Cancelar e Limpar filtros. Cancelar preserva as escolhas aplicadas anteriormente.
- O botão Ver análise resolve um `analysisId` explícito em `StockerHomeData`.
  Os mocks foram separados por gôndola/análise e incluem vínculos para alertas,
  eventos e registros concluídos. O vínculo é validado pela gôndola; análises
  ausentes desabilitam a ação e exibem uma explicação. Não há fallback silencioso
  para A12. Voltar retorna à aba de origem e a restauração preserva o resultado
  selecionado apenas para a mesma conta.
- Login e cadastro usavam fonte padrão/Inter; agora usam Montserrat com pesos
  explícitos. Onboarding, carregamento e o tema padrão também usam Montserrat.
- A IA continua sem integração real, conforme combinado. Nenhum estoque é alterado.

## Arquivos principais

- `view/StockerHomeView.kt`, `view/InfoCardDialog.kt`
- `model/home/StockerHomeRepository.kt`, `model/home/RestockingHistory.kt`
- `view/AuthEntryView.kt`, `view/LoadingView.kt`
- `view/settings/AccountUiStyle.kt`, `SecurityView.kt`, `ProfileView.kt`, `SettingsContentView.kt`
- `res/layout/view_onboarding.xml`, `res/values/themes.xml`, `res/values-night/themes.xml`
- `res/values/home-palette.xml`, `dialogs.xml`, `analysis.xml`, `res/values-night/home-colors.xml`
- `res/drawable/menu_settings_active.xml`, `menu_clock_selected.xml`,
  `home_house.xml`, `home_clock.xml`, `home_settings.xml`, `menu_bell.xml`

## Verificação

- `ShelfAnalysisMappingTest` valida os vínculos de todos os exemplos, separação de
  gôndolas e comportamento de análises ausentes/incompatíveis.
- `StockerPreferencesTest` cobre a navegação Alerta → Ver análise → gôndola correta → Voltar.
- `NavigationIconTest` verifica pixels transparentes no centro da engrenagem e nos
  ponteiros do relógio, nos temas claro e escuro.
- Os testes instrumentados precisam de dispositivo conectado. Nenhum dispositivo
  estava disponível durante esta revisão; compilar os testes não equivale a executá-los.

Validação final: `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest` e
`lintDebug` concluídos com sucesso. Foram aprovados 29 testes unitários; o lint
registrou 61 avisos e nenhum erro. Os testes novos estão em
`test/.../home/ShelfAnalysisMappingTest.kt`, `androidTest/.../NavigationIconTest.kt`
e `androidTest/.../StockerPreferencesTest.kt`.

## Revisão dos diálogos e filtros

- Removidos os ícones decorativos de verificação de gôndola, detalhes de análise
  (alertas e histórico) e aviso de início de reposição. A verificação também não
  repete o subtítulo pequeno "Verificar gôndola".
- O título passou a abrir o conteúdo. Gôndola e horário aparecem abaixo do título
  quando são informações do registro, sem o antigo cabeçalho decorativo.
- Voltar usa ação de texto plana; botões têm `stateListAnimator = null`, elevação
  e translação Z zeradas e fundo sem tintura automática do tema.
- O filtro compartilha `InfoCardDialog.panel/button/present`, com seletores
  arredondados, descrição breve e ações de aplicar, cancelar e limpar.
- `StockerHomeTest` agora também verifica que Cancelar descarta escolhas ainda
  não aplicadas. `DialogScreenshots.kt` captura a janela completa dos diálogos.

Build, 29 testes unitários e lint passaram. O APK foi atualizado no Samsung
conectado com instalação incremental, sem desinstalar o Kairos nem limpar seus
dados. O teste dos recortes transparentes dos ícones passou no aparelho. A primeira
execução dos outros três testes de interface foi impedida pela tela apagada e
bloqueada: sem Activity visível, não foi possível validar os diálogos/filtros.

## Onboarding e formulários de segurança — 15/10/2026

Onboarding com pesos Montserrat aplicados após inflação, coluna central alinhada,
conteúdo rolável e botão independente acima da área segura do sistema. O fluxo
continua aguardando restauração da sessão antes de oferecer a apresentação.

Os editores de nome, e-mail e senha reutilizam os cards/botões planos existentes,
com campos arredondados e suporte claro/escuro. O envio não fecha o formulário:
somente uma resposta de sucesso confirmada o fecha. Erros ficam visíveis no próprio
editor, desbloqueiam uma nova tentativa e, quando vêm do backend, limpam a senha
atual. Validações locais, como confirmação divergente, preservam os demais campos.
A reautenticação inválida informa explicitamente senha atual incorreta.

Arquivos desta rodada (caminhos relativos a `app/src`, salvo documentação):

- `main/java/com/example/kairos/model/auth/FirebaseAdapters.kt`
- `main/java/com/example/kairos/viewmodel/AuthViewModel.kt`
- `main/java/com/example/kairos/view/MainActivity.kt`
- `main/java/com/example/kairos/view/StockerHomeView.kt`
- `main/java/com/example/kairos/view/OnboardingStyle.kt` (novo)
- `main/java/com/example/kairos/view/settings/AccountEditorDialog.kt`
- `main/res/layout/view_onboarding.xml`
- `main/res/values/account.xml`
- `main/res/values-night/account-colors.xml` (novo)
- `test/java/com/example/kairos/auth/AuthViewModelTest.kt`
- `androidTest/java/com/example/kairos/AccountScreensTest.kt`
- `androidTest/java/com/example/kairos/AccountEditorFeedbackTest.kt` (novo)
- `androidTest/java/com/example/kairos/OnboardingLayoutTest.kt` (novo)
- `androidTest/java/com/example/kairos/AuthUiPreviewTest.kt` (novo)
- Documentação: `docs/auth-design.md`, `docs/settings-flow.md` e este arquivo.

Validação: build e 34 testes unitários passaram; lint sem erros, com 57 avisos.
No Samsung desbloqueado, os quatro testes de interface acima passaram. Foram
verificados os três títulos do onboarding em três dimensões e dois tamanhos de
fonte, os estados de erro/reenvio, a edição validada e as prévias renderizadas.
Os testes usam dados fictícios e não fazem alterações no Firebase. A resposta
real do servidor não foi testada com credenciais do usuário. O APK foi atualizado
com `install -r -t`, sem desinstalar o app nem apagar seus dados.
