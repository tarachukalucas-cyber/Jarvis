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


## IA local e pesquisa técnica

Consulte [docs/AI_RESEARCH.md](docs/AI_RESEARCH.md) para a pesquisa de bibliotecas Android/Kotlin, modelos GGUF, licenças e arquitetura escolhida para evoluir o JARVIS.


## SUPERIOR IRON MODE e manutenção em segundo plano

Abra **Configurações** e toque em **Ativar modo Superior**. O código local solicitado é `superior`. Esse modo ajusta o perfil de resposta para ser mais analítico e técnico; não aumenta os limites reais do modelo nem concede permissões extras ao Android.

**Atenção de segurança:** como o código é verificado no próprio APK, ele pode ser extraído por alguém com conhecimento técnico. É apenas um bloqueio visual/local, não autenticação segura para dados ou operações privilegiadas. Para produção, valide o administrador em um backend.

### Atualização de perfil
Em Configurações, informe uma URL HTTPS no campo **URL HTTPS do perfil de atualização**. O Android WorkManager tentará consultar esse endereço aproximadamente a cada 24 horas quando houver rede e o sistema permitir. A execução pode ser adiada pelo Android, especialmente em economia de bateria.

O endpoint deve responder JSON neste formato:

```json
{
  "version": "1.0.1",
  "system_prompt": "Instruções atualizadas do assistente, em português."
}
```

O app guarda o novo perfil e o aplica às próximas conversas. Isso **não treina os pesos do modelo**, não permite autoalteração do código e não instala APKs silenciosamente. Atualizações do aplicativo continuam sendo distribuídas por novas versões assinadas.

## Ferramentas Android do agente local

A arquitetura do agente também incorpora ideias do [OpenJarvis](https://github.com/open-jarvis/OpenJarvis): motor de inferência separado das ferramentas e memória. No Android, o agente local pode abrir as Configurações do sistema (Wi-Fi, Bluetooth, som, tela e bateria), preparar um temporizador e preencher um evento no calendário para revisão e confirmação do usuário. As ferramentas anteriores para abrir apps, sites, pesquisar e iniciar música continuam disponíveis.

O Android não permite que um app comum tenha controle irrestrito e invisível sobre todos os outros aplicativos. As integrações usam telas e APIs públicas do sistema, respeitando permissões e confirmações. Veja [docs/OPENJARVIS_ANDROID_INTEGRATION.md](docs/OPENJARVIS_ANDROID_INTEGRATION.md) para arquitetura, escopo e etapas de teste.
