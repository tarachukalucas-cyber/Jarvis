# JARVIS para Android

Aplicativo Android nativo em Kotlin, com interface própria, conversa por texto, reconhecimento de voz em português usando o serviço de fala disponível no Android, resposta falada por TextToSpeech e abertura de aplicativos instalados.

## Gerar APK
O GitHub Actions compila automaticamente o APK de depuração a cada push na branch `main`. Também é possível executar manualmente em **Actions → Build JARVIS APK → Run workflow**.

Quando a execução terminar com sucesso, baixe o arquivo `JARVIS-debug-apk` na seção **Artifacts** da execução.

## Estado atual
Esta é uma primeira versão funcional de comandos locais, não uma IA geral. A conexão com um modelo de IA, memória persistente e ferramentas adicionais serão etapas posteriores.

## Requisitos
- Android 8.0 (API 26) ou superior.
- Permissão de microfone para comandos por voz.
- Serviço de reconhecimento de fala disponível no dispositivo.

## Privacidade e segurança
O aplicativo não mantém escuta contínua. O microfone é ativado somente quando a pessoa toca no botão de voz. Não há chave de API embutida no APK.
