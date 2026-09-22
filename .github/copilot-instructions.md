# Review do Kairos Mobile

Responda em português e priorize problemas concretos introduzidos pela PR. Explique o impacto e indique o trecho relevante. Evite sugestões puramente cosméticas.

- Este é um aplicativo Android nativo em Kotlin, construído com Gradle Wrapper e Android Gradle Plugin. A CI usa JDK 21, enquanto o bytecode do app tem compatibilidade Java 11.
- Verifique com `bash ./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug` (Windows: `./gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug`).
- Preserve a separação existente entre `model`, `view` e `viewmodel`. Não mova regras de negócio ou acesso a dados para Activities.
- Confira ciclo de vida, mudanças de configuração, estado da UI, nulabilidade e operações bloqueantes na thread principal.
- Em mudanças de autenticação e Firestore, revise sessão parcial, falhas de rede, idempotência e separação entre usuários.
- Não exponha senhas, hashes, CPF, tokens ou credenciais em UI, respostas, logs ou telemetria. `google-services.json` pode conter identificadores públicos do Firebase, mas nunca inclua contas de serviço, chaves privadas, keystores ou segredos administrativos.
- Revise permissões Android, componentes exportados, intents e armazenamento de dados sensíveis. Mudanças no Firestore também devem respeitar `firestore.rules` e o isolamento por usuário.
- Mudanças comportamentais precisam de testes de regressão, incluindo entradas inválidas, falhas assíncronas e estados de erro. Use testes instrumentados apenas quando o comportamento depender do framework Android ou da UI.
- Revise workflows quanto a permissões mínimas. Não use pull_request_target para executar código não confiável.
- A revisão automática complementa os mantenedores. Não sugira desativar a proteção da main para resolver falhas de CI.
