# Estado de retomada

## Instrução inicial
Leia AGENTS.md e CHECKLIST_PROJETO.md. Confira as tarefas pendentes e continue a
execução autorizada. Confira alterações locais antes de editar. Não reinicie do
zero nem suponha que uma tarefa em andamento terminou.

## Estado atual
- Base: MediaPod Android Kotlin/Compose; versão 0.3.8, código 90.
- Projeto: `D:\\global-radiopod - Copia`; shell PowerShell.
- Alterações anteriores extensas e não commitadas: preservar.
- Pedido prioritário concluído: rótulos Top 20, busca independente dos Tops e build 0.3.6 (88).
- Itens 02, 03 e 12 concluídos; assinatura local ajustada, rotação externa pendente (01).
- Backup por senha e testes da interface aprovados; recuperação transacional implementada.
- **DIRETIVA DE NAVEGAÇÃO FIXADA (commit a21fb66):** navegação anterior/próximo de rádios feita SEMPRE dentro da lista de origem (Favoritos, Recentes, Busca, Categoria, Ranking). Loop circular ativo em todos os extremos. Cobre UI, Android Auto e Cast.
- **REPRODUÇÃO GOOGLE CAST E RENDERIZAÇÃO CORRIGIDAS (Versão 0.3.8 / 90 - 2026.09.24):**
  1. Identificado e eliminado duplo disparo instantâneo em `playRadioStation`/`playStreamUrl` que gerava cancelamento mútuo imediato pelo Google Cast SDK (`statusCode=2103 MEDIA_LOAD_CANCELLED`).
  2. Implementado Debounce de 600ms em `AudioRouteManager.transferPlaybackToCast` evitando requisições concorrentes.
  3. Implementado `CastStreamProxy` local (servidor HTTP interno no dispositivo) que serve as streams para o Chromecast com cabeçalhos `Access-Control-Allow-Origin: *` (CORS), eliminação de cabeçalhos ICY conflitantes e User-Agent compatível, permitindo tocar qualquer rádio online (HTTP ou HTTPS) no Nest Mini / Chromecast sem bloqueios de segurança do receiver web.
  4. Versão incrementada para 0.3.8 (90) e data atualizada para 2026.09.24 na tela Sobre e em `build.gradle.kts`.
  5. Corrigida renderização de tags e gêneros nos Tops (`PublishedRankings.kt` e `RadioStation.kt`), eliminando resíduos de arrays JSON (`[""]`, `["adult...`). Teste automatizado incluído e validado em `RadioRankingRepositoryTest`.
  6. Isolamento da fila de reprodução na navegação de telas em `RadioViewModel.kt`: a fila ativa só é modificada quando o usuário clica para reproduzir, mantendo anterior/próximo e loop íntegros mesmo ao navegar livremente pelas outras telas.
  7. Suporte a Áudio Local (MP3/M4A/FLAC) e Vídeos Locais (MP4/MKV) no Google Cast implementado:
     - `CastStreamProxy`: endpoint `/local_media` com suporte a requisições de intervalo HTTP (`Range: bytes=start-end` / `206 Partial Content`) e CORS, permitindo ao Chromecast indexar metadados de vídeo e realizar Seek rápido na LAN.
     - `AudioRouteManager`: roteamento automático das mídias `LOCAL_AUDIO` e `LOCAL_VIDEO` via `transferPlaybackToCast` e `transferPlaybackToLocal`, com metadados completos e tipo de stream bufferizado.
     - `LocalVideoPlayerManager` e `RadioPlayerManager`: delegação transparente de comandos de transporte (`play`, `pause`, `seek`, `next`, `prev`) diretamente para o Cast quando conectado.
     - `IpodVideoScreen`: interface dedicada "Transmitindo para [Dispositivo]" em modo retrato e tela cheia landscape durante o Cast, poupando bateria e CPU do celular.
  8. Validação automatizada: 205 testes unitários passaram (0 falhas) e APK debug compilado (`app/build/outputs/apk/debug/app-debug.apk`).

## Próximo passo concreto
Acompanhar a validação do usuário com o aparelho conectado testando o Cast de MP3 e vídeos locais na TV/Nest Mini, e em seguida prosseguir com os itens pendentes da checklist (Item 08 - Geografia e Item 18 - Acessibilidade/Tradução).


## Verificação
Compilação prioritária iniciada: `reports/top20-search-release.log`.
Os dois testes de `PodcastFullCatalogSearchTest` passaram (0 falhas).
BUILD SUCCESSFUL confirmado; APK/AAB versão 0.3.6 (88) gerados; P0 concluído.
Esses binários ainda NÃO incluem as alterações posteriores do checklist.
Busca usa `podcastSearchResults` e carregamento próprios, sem compartilhar
`podcastShows` com Tops/categorias. Campo vazio mostra toda a base; busca local
inclui personalizados, favoritos e assinaturas; requisições anteriores canceladas.
JAVA_HOME para Gradle: `C:\Program Files\Android\Android Studio\jbr`.
Use os testes específicos do lote; registre logs em reports quando executar.
Não inicie dois processos Gradle simultâneos. Antes de repetir um build,
verifique se o anterior terminou. Processos da sessão anterior podem não existir.

## Continuidade
Os documentos sobrevivem ao fechamento da IDE. A execução requer uma sessão do
agente aberta; não é prometida execução autônoma com a IDE fechada. Ao iniciar uma
nova sessão neste diretório, AGENTS.md direciona a leitura das pendências.
