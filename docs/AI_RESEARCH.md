# Pesquisa técnica: cérebro de IA do JARVIS

Data da pesquisa: 2026-11-04

## Decisão técnica

O JARVIS atual é um app Android nativo em Kotlin. Para adicionar inferência local sem reescrever todo o app em Flutter ou React Native, a opção mais compatível encontrada foi **llmedge**:

- Projeto: https://github.com/Aatricks/llmedge
- Licença declarada pelo repositório: Apache-2.0
- Biblioteca Android/Kotlin que integra llama.cpp por JNI.
- A documentação descreve download de modelos GGUF do Hugging Face, chat com sessões, geração em streaming e agentes com chamadas de ferramentas.
- Dependência publicada indicada pelo próprio projeto: `io.github.aatricks:llmedge:0.3.9`.
- O README atual do projeto informa minSdk 30; isso precisa ser respeitado ao integrar.

## Modelo inicial escolhido

**Qwen3-1.7B-GGUF**, da organização oficial Qwen:

- Modelo: https://huggingface.co/Qwen/Qwen3-1.7B-GGUF
- Código e documentação: https://github.com/QwenLM/Qwen3
- Licença indicada no cartão do modelo: Apache-2.0.
- É um modelo pequeno para execução local comparado a modelos de nuvem. A qualidade não deve ser anunciada como equivalente ao ChatGPT; a velocidade e a qualidade dependem da RAM, processador, quantização e temperatura do aparelho.
- O modelo é um download separado e grande; não deve ser embutido no APK. O aplicativo deverá mostrar tamanho, espaço livre, progresso e opção de cancelar.

O arquivo selecionado para a primeira integração é `Qwen3-1.7B-Q4_K_M.gguf` no repositório `ggml-org/Qwen3-1.7B-GGUF`, com tamanho publicado de aproximadamente 1,28 GB. O app já aponta para esse repositório e arquivo.

## Projetos analisados

### Aatricks/llmedge — selecionado para integração
https://github.com/Aatricks/llmedge

Biblioteca nativa Kotlin/Android com llama.cpp, gestão de modelos GGUF, sessões de chat, streaming e tool calling. É a melhor correspondência técnica para o app atual. Está em desenvolvimento ativo e o próprio projeto avisa que algumas áreas ainda são experimentais; a integração precisa de compilação e teste em aparelho real.

### RisorseArtificiali/vesta — referência de arquitetura
https://github.com/RisorseArtificiali/vesta

MIT. Mostra um assistente offline com modelos GGUF, memória, histórico e ações Android. É construído com Expo/React Native e llama.rn; por isso, copiar o aplicativo inteiro para o JARVIS Kotlin seria uma reescrita e não uma integração direta. Usar apenas como referência arquitetural, preservando avisos de licença se algum código for reutilizado.

### smartworldarafath/Local-AI — não copiar para o JARVIS
https://github.com/smartworldarafath/Local-AI

Tem interface Material 3 Expressive, provedores de IA, memória/RAG e ferramentas. O repositório declara AGPL-3.0 e possui escopo muito maior, inclusive sandbox Linux. Não será copiado para o JARVIS sem uma decisão explícita de licenciamento e cumprimento das obrigações AGPL.

### dgirard/droidclaw — referência de agente
https://github.com/dgirard/droidclaw

Assistente Android baseado em Flutter com ciclo de agente, tool calling e múltiplos provedores. É útil como referência de arquitetura de agente, mas não é uma dependência direta para o app Kotlin.

## Implementação iniciada no repositório JARVIS

- Dependência adicionada: `io.github.aatricks:llmedge:0.3.9` (Apache-2.0), biblioteca Kotlin/Android baseada em llama.cpp.
- Nova classe `LocalAiEngine.kt` cria uma sessão de chat local, baixa o GGUF sob demanda e registra ferramentas limitadas para abrir aplicativos instalados, URLs, pesquisas Google e app de música.
- A interface ganhou a ação “Baixar IA local (~1,3 GB)” nas configurações.
- O minSdk do JARVIS foi elevado para 30 por compatibilidade com a biblioteca.
- A compilação do APK e o teste em aparelho real ainda são necessários; a integração não deve ser considerada validada até passar nesses testes.

## Arquitetura pretendida

1. **Motor local:** llmedge + llama.cpp.
2. **Modelo inicial:** Qwen3-1.7B GGUF quantizado, baixado sob demanda e guardado no armazenamento privado do app.
3. **Chat:** sessão multi-turn com limite de contexto, histórico local e saída em streaming.
4. **Ferramentas Android:** ferramentas explícitas para abrir apps instalados, abrir URLs, pesquisar na web e iniciar o app de música. A IA só poderá chamar ferramentas que o app implementar.
5. **Segurança:** confirmar ações sensíveis; nunca executar comandos arbitrários de shell, enviar mensagens, fazer chamadas ou alterar dados sem uma etapa clara de confirmação.
6. **Voz:** manter o reconhecimento e a fala Android existentes no primeiro estágio; avaliar Whisper/Bark apenas após medir memória e desempenho.
7. **IA remota opcional:** manter provedor remoto como opção separada para respostas mais fortes. Segredos de API nunca serão embutidos no APK.

## Critérios antes de entregar outro APK

- Compilar no GitHub Actions sem erros.
- Testar instalação e inicialização.
- Confirmar download/resume/cancelamento do modelo.
- Confirmar chat de várias mensagens e streaming.
- Confirmar chamadas de ferramentas com resultado verdadeiro.
- Testar memória e consumo de RAM em aparelho Android real.
- Exibir claramente quando o modelo não estiver baixado ou o dispositivo não tiver recursos suficientes.

## Limites

Um modelo local de 1.7B é um começo funcional e privado, mas não é comparável a um modelo de nuvem de grande porte em raciocínio complexo. Para uma experiência realmente próxima do ChatGPT, o JARVIS deverá oferecer também uma conexão opcional a um modelo remoto competente, sem prometer capacidade que não foi testada.
