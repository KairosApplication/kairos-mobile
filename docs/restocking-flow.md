# Fluxo de reposição

O resultado da foto usa a mesma tela de Resultado da análise dos alertas. A foto
produz observações e candidatos do catálogo; os alertas continuam vindo do banco.
Nenhuma análise altera saldos automaticamente.

Selecionar pelo menos um produto libera Iniciar reposição. Os produtos escolhidos
seguem juntos para Reposição em andamento, sem uma tela intermediária de preparo.
O funcionário repõe fisicamente e informa estoque de origem, quantidade retirada
e avarias antes de finalizar. A foto de conferência é opcional.

A finalização usa POST /api/v1/mobile/restockings/batch. O servidor valida cada
movimentação em uma única transação e resolve os alertas correspondentes apenas
em comandos novos. Uma repetição não duplica saldos nem resolve alertas posteriores.
Cada produto mantém seu UUID de comando durante tentativas após falha de conexão.
O andamento e os comandos pendentes ficam salvos por conta neste aparelho.

Scanner e câmera restauram a preferência de tema do app. A superfície da câmera
continua escura para fotografar; os formulários respeitam o modo claro/escuro.
As transições usam entrada e saída suaves e respeitam animações desativadas no Android.
