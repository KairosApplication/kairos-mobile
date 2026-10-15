# Inventário mobile e implementação de telas

Referência: arquivo Figma `OFMvU7yImxJFUpV7e061Pj`, página Mobile,
seção **Kairos - Protótipo Mobile v2** (`713:1430`). A seção v1 e os frames
marcados como descartados não criam novas rotas.

## Estado das telas

| Tela / referência | Implementação | Estado |
| --- | --- | --- |
| Splash / `713:3931` | MainActivity + view_splash.xml | Existente |
| Onboarding / `713:3929` | MainActivity + view_onboarding.xml | Existente, 3 etapas |
| Entrada, login e cadastro | AuthEntryView + AuthViewModel | Firebase real, existente |
| Recuperar senha / completar perfil | MainActivity + AuthViewModel | Firebase real, existente; layout básico |
| Home / `713:3847` | StockerHomeView + StockerHomeViewModel | Existente, estoque demonstrativo |
| Alertas / `713:1764` | StockerHomeView | Existente, estoque demonstrativo |
| Histórico / `713:1606` | StockerHomeView + HistoryFilter | Existente, busca/filtros locais |
| Ajustes / `713:1826` | SettingsDestination + StockerHomeView | Existente |
| Perfil / `713:2029` | ProfileView | Sessão real, resumo de trabalho demonstrativo |
| Segurança / `713:2121` | SecurityView + AccountEditorDialog | Edição de conta real; cards existentes |
| Notificações / `713:2168` | SettingsContentView | Implementada: switches com preferências locais por UID |
| Aparência / `713:2333` | SettingsContentView + StockerPreferences | Implementada: claro/escuro/sistema |
| Ajuda / `713:2362` | SettingsContentView | Implementada: busca, perguntas e respostas locais |
| Resultado / `713:2503` | StockerHomeView + ShelfAnalysis | Implementada: demonstração com seleção local |
| Modal de alerta / `713:2920` | Diálogo de informação existente | Pendente reprodução visual específica |
| Scanner inicial / `713:1448` | Aviso na Home | Pendente tela visual |
| Analisando / `713:1502` | Referência obtida | Pendente tela visual; IA real fora do escopo |
| Reposição em andamento / `713:2616` | Aviso no resultado | Pendente tela visual |
| Scanner de confirmação / `713:1466` | Não implementado | Pendente tela visual |
| Conclusão / `713:1511` | Não implementado | Pendente tela visual |
| Suporte / `713:2397` | SettingsTemplateView | Pendente layout; chatbot/IA real fora do escopo |
| Sobre / `713:2480` | SettingsTemplateView | O frame inventariado contém só cabeçalho, fundo e menu |

Não foram encontrados frames v2 independentes de catálogo de produtos, detalhe de
produto ou setores/corredores atribuídos. Models de domínio existentes não são,
por si só, autorização para inventar essas telas.

## Arquivos envolvidos

- `model/home/StockerPreferences.kt`: preferências locais e aplicação do tema.
- `model/home/StockerHomeRepository.kt`: contrato de resultado demonstrativo `ShelfAnalysis`.
- `view/settings/SettingsContentView.kt`: conteúdo das três telas de ajustes.
- `view/settings/AccountUiStyle.kt`: superfície adaptável ao tema.
- `view/settings/ProfileView.kt`, `SecurityView.kt`: contraste no modo escuro.
- `view/StockerHomeView.kt`: integração das rotas, demonstração do resultado,
  seleção/restauração por UID e cores de tema.
- `view/MainActivity.kt`: restauração e aplicação de tema.
- `res/values/preferences.xml`, `analysis.xml`, `colors.xml`: textos, IDs e tokens.
- `res/values-night/home-colors.xml`: tokens escuros.
- `res/drawable-nodpi/restock_shelf.png`: foto original do frame de resultado.
- `androidTest/StockerPreferencesTest.kt`: persistência por usuário, busca de ajuda,
  restauração da seleção e capturas das telas.
- `androidTest/StockerSettingsTest.kt`: validação dos novos destinos específicos.
- `androidTest/StockerHomeTest.kt`: import ausente de MainActivity corrigido para compilar a suíte existente.

## Limites desta entrega

O conector Figma atingiu a cota Starter durante a leitura dos frames restantes.
Foram obtidos contexto detalhado e screenshot de Notificações, Aparência, Ajuda,
Resultado e Análise. A tentativa alternativa pelo navegador terminou em sessão
expirada. As demais telas precisam de nova leitura detalhada antes de prometer
fidelidade visual. A IA é responsabilidade de outro integrante e não precisa
funcionar nesta etapa; esse fato não elimina as telas visuais pendentes.

O resultado corrige o título inconsistente "Segurança" do frame para "Resultado
da análise". Os produtos, contagens e localização vêm do repositório de demonstração,
sem vincular automaticamente a identidade Firebase a um funcionário do backend.

## Validação

Comandos: `:app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug`.
Para o erro local Windows/Java `UnixDomainSockets.connect / Invalid argument`,
esta sessão usou `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=` com um diretório
inexistente em `build`, fazendo o Java recorrer ao TCP. Nenhuma configuração
permanente de Gradle/Java foi alterada.

As capturas só são produzidas ao executar os testes instrumentados em dispositivo
ou emulador funcional. Compilar o APK de testes não confirma sua execução nem
a fidelidade visual. O emulador local apresentou falha de WHPX na inicialização.
Uma segunda tentativa sem aceleração permaneceu offline; os processos iniciados
para essa tentativa foram encerrados.

Resultado em 14/10/2026: build do app, 26 testes unitários, build dos testes
instrumentados e lint concluídos com sucesso. O lint registrou 56 avisos e um
erro filtrado pelo baseline preexistente. A suíte instrumentada foi compilada,
mas não executada. As telas ainda precisam de comparação visual em dispositivo.

## Próximos passos

1. Obter as referências detalhadas restantes e concluir scanner, andamento,
   confirmação, conclusão, modal de alerta e suporte visual, mantendo IA simulada.
2. Validar capturas em Android pequeno e padrão, também com fonte ampliada e tema escuro.
3. Definir contratos de estoque/reposição e ligar os adaptadores, sem usar UID Firebase
   como ID SQL de funcionário e sem modificar a camada de IA de outro integrante.
