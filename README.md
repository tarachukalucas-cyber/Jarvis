# JARVIS para Android

Assistente Android em Kotlin com interface de chat, entrada por texto e voz, respostas faladas, comandos locais para iniciar aplicativos, abrir sites, pesquisa web e app de música.

## Build
O GitHub Actions compila o APK de depuração a cada push na branch main. O artefato se chama `JARVIS-debug-apk`.

## Conexão com IA
O app aceita um endpoint HTTPS de servidor compatível com OpenAI Chat Completions. Nas configurações do app, informe a URL base do seu backend; ele deve expor `/v1/chat/completions` (ou informe a URL completa dessa rota), receber `model` e `messages`, e responder com `choices[0].message.content`.

**Importante:** a chave do provedor de IA deve ficar como segredo no servidor/backend, nunca embutida no APK. O repositório não inclui servidor hospedado, chave de API ou créditos de IA; portanto, o chat inteligente só responderá depois que um backend válido for configurado.

## Comandos locais
- "abra [nome do app]" tenta localizar e iniciar um app instalado.
- "pesquise [termo]" abre a pesquisa web.
- "abra exemplo.com" abre um site.
- "abrir música" inicia o app de música padrão ou abre YouTube Music no navegador.

O Android pode impedir a abertura de apps não instalados ou ações sem suporte. O microfone só é usado após a pessoa tocar no botão de voz.
