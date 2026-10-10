# Ajustes do repositor

A página principal segue o frame [Tela-Config, 713:1826](https://www.figma.com/design/OFMvU7yImxJFUpV7e061Pj?node-id=713-1826).
O título é Configurações; a aba continua identificada como Ajustes.

| Grupo | Acesso | Destino |
| --- | --- | --- |
| Conta | Perfil | Avatar, dados da sessão, resumo do trabalho e logout |
| Conta | Segurança | Dados da conta e formulários de edição real no Firebase |
| Preferências | Notificações | Template de Notificações |
| Preferências | Aparência | Template de Aparência |
| Suporte | Ajuda e Suporte | Template de ajuda com acesso a Falar com o suporte |
| Suporte | Sobre o KAIROS | Template de Sobre |

O segundo destino de ajuda/suporte é Suporte, correspondente ao futuro chatbot
do frame `713:2397`. Nesta etapa não há atendimento ou envio de mensagens.
As demais telas filhas usam `SettingsTemplateView` com o aviso Em breve; cada destino
tem uma chave em `SettingsDestination` para receber sua implementação específica.

O botão Voltar e o retorno do sistema levam Suporte → Ajuda e Suporte →
Configurações → Início. As demais telas filhas voltam diretamente a Configurações.
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
