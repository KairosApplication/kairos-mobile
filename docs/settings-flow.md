# Ajustes do repositor

A página principal segue o frame [Tela-Config, 713:1826](https://www.figma.com/design/OFMvU7yImxJFUpV7e061Pj?node-id=713-1826).
O título é Ajustes; a aba continua identificada como Ajustes.

| Grupo | Acesso | Destino |
| --- | --- | --- |
| Conta | Perfil | Avatar, dados da sessão, resumo do trabalho e logout |
| Conta | Segurança | Dados da conta e formulários de edição real no Firebase |
| Preferências | Notificações | Preferências gerais, reposições, atualizações, som e vibração |
| Preferências | Aparência | Seleção de modo claro, escuro ou automático |
| Suporte | Ajuda e Suporte | Busca nas perguntas frequentes e acesso a Falar com o suporte |
| Suporte | Sobre o KAIROS | Template de Sobre |

O segundo destino de ajuda/suporte é Suporte, correspondente ao futuro chatbot
do frame `713:2397`. Nesta etapa não há atendimento ou envio de mensagens.
Suporte e Sobre usam `SettingsTemplateView` com o aviso Em breve; cada destino
tem uma chave em `SettingsDestination` para receber sua implementação específica.

`SettingsContentView` implementa os frames v2 `713:2168`, `713:2333` e `713:2362`,
reutilizando `AccountUiStyle`. `StockerPreferences` salva escolhas locais por UID.
Os switches armazenam preferências; o projeto ainda não possui entrega de notificações
push ou canais Android associados. O tema usa AppCompat DayNight e recursos `values-night`;
o modo automático segue o sistema. O tema escuro adapta os tokens existentes, pois
o Figma disponível mostra apenas o modo claro dessas telas.
As respostas das perguntas frequentes descrevem o comportamento implementado,
incluindo a ausência de análise por IA e de reposições reais. Não há envio de mensagens.

O botão Voltar e o retorno do sistema levam Suporte → Ajuda e Suporte →
Ajustes → Início. As demais telas filhas voltam diretamente a Ajustes.
O menu inferior permanece disponível e mantém Ajustes selecionado nas telas filhas.
Tocar em Ajustes enquanto uma tela filha está aberta retorna à página principal.
Trocar para outra aba e voltar também abre a página principal de Ajustes.

O destino participa do bundle existente, vinculado ao UID. A restauração aguarda
a autenticação da mesma conta; logout e troca de usuário descartam o destino.
A página de ajustes não depende do carregamento dos dados de estoque.

Os sete ícones `drawable/settings_*.xml` foram convertidos dos SVGs originais
fornecidos pelo Figma com Svg2Vector do Android Studio. Os cartões reutilizam os
estilos e tokens da Home, com fonte Montserrat, fundo em gradiente e menu animado.

`StockerSettingsTest` cobre os sete destinos, os retornos, a troca de abas,
a restauração por UID, o logout, o bloqueio durante a saída e o fallback de rotas
desconhecidas. A prévia `settings-preview.png` é gerada nos arquivos internos
do app no teste, após concluir a animação do menu.

Os formulários de nome, e-mail e senha reutilizam a superfície e os botões planos
de `InfoCardDialog`, com campos arredondados, tokens claro/escuro e conteúdo rolável.
`AccountEditorDialog.Editor` mantém o formulário aberto enquanto aguarda o backend,
desabilita os campos e ações durante o envio e só fecha após sucesso confirmado.
`AuthUiState.accountOperationSucceeded` distingue confirmação real de falha;
o resultado não é inferido pelo texto da mensagem nem pela ausência de loading.
Falhas aparecem dentro do formulário, permitem tentar novamente e limpam a senha
atual. A reautenticação rejeitada pelo Firebase informa senha atual incorreta.
O e-mail continua inalterado até a confirmação do link. Senhas não são salvas,
registradas ou incluídas no bundle; a janela mantém FLAG_SECURE.
`AccountEditorFeedbackTest` cobre erro e nova tentativa em e-mail e senha usando
somente dados fictícios, sem conexão nem mutações no Firebase.
