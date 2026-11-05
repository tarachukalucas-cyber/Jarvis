# Integração inspirada no OpenJarvis

Este documento descreve como o JARVIS Android aproveita ideias arquiteturais do projeto OpenJarvis sem tentar executar o framework Python/desktop inteiro dentro do APK.

Referências:
- OpenJarvis: https://github.com/open-jarvis/OpenJarvis
- Visão de arquitetura: https://open-jarvis.github.io/OpenJarvis/architecture/overview/
- Guia rápido: https://open-jarvis.github.io/OpenJarvis/getting-started/quickstart/

## O que foi aproveitado

O OpenJarvis separa a solução em inteligência/modelos, motor de inferência, agentes, ferramentas e memória/aprendizado. O JARVIS Android mantém Kotlin nativo e aplica essa separação nos pontos compatíveis:

| Camada | Implementação Android |
| --- | --- |
| Inteligência | Qwen3-1.7B GGUF baixado sob demanda |
| Engine | llmedge/llama.cpp local; endpoint HTTPS opcional |
| Agent | agente de ferramentas do llmedge |
| Tools | ferramentas Android explicitamente permitidas |
| Memory | histórico local e janela de conversa existentes |
| Learning/observabilidade | perfil de sistema atualizável e manutenção periódica; não altera pesos do modelo |

## Ferramentas Android adicionadas

A classe `AndroidActionTools` registra ações para o agente local:

- `open_android_settings`: abre telas de configurações gerais, Wi-Fi, Bluetooth, som, tela ou bateria.
- `start_timer`: abre o relógio com um temporizador preenchido.
- `create_calendar_event`: abre o calendário com título e horários preenchidos para revisão.

As ações abrem telas visíveis do Android. O usuário confirma e conclui alterações no aplicativo de sistema correspondente. Isso evita prometer controle irrestrito ou realizar mudanças silenciosas em configurações protegidas.

As ferramentas anteriores continuam disponíveis: abrir aplicativo instalado, abrir URL, pesquisar na web e abrir o app de música.

## Limites importantes do Android

Um aplicativo comum não pode controlar irrestritamente todos os outros aplicativos. A disponibilidade depende da versão do Android, permissões, visibilidade de pacotes e APIs públicas. Wi-Fi, Bluetooth, notificações, chamadas, mensagens, pagamentos e dados de outros apps têm proteções próprias. Não são usados comandos shell arbitrários nem automação invisível de toques.

A voz permanece baseada nas APIs de reconhecimento de fala e Text-to-Speech do Android; o microfone só é iniciado após interação explícita com o botão de voz. Ativação contínua por palavra-chave exigiria um serviço próprio, consentimento e tratamento adicional de bateria/privacidade.

## Próximas etapas de validação

1. Compilar o APK em CI com esta branch.
2. Testar instalação e permissões em Android 11 ou superior.
3. Baixar o modelo local e testar chat/tool-calling em um aparelho real.
4. Confirmar que as três ferramentas novas abrem as telas corretas e exigem revisão do usuário.
5. Medir RAM, temperatura e tempo de resposta antes de aumentar o tamanho do modelo.

O OpenJarvis original é um framework local-first com SDK Python, backends de inferência e interface web/desktop. Ele não é uma biblioteca Android pronta para ser importada diretamente no app Kotlin; por isso, esta integração reutiliza conceitos e mantém o runtime Android nativo.
