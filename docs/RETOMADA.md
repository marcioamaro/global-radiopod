# Estado de retomada

## Instrução inicial
Leia AGENTS.md e CHECKLIST_PROJETO.md. Confira as tarefas pendentes e continue a
execução autorizada. Confira alterações locais antes de editar. Não reinicie do
zero nem suponha que uma tarefa em andamento terminou.

## Regra de versões
Sempre que uma versão for incrementada, atualizar na mesma alteração o
`versionName`/`versionCode` em `app/build.gradle.kts` e a data com a versão
exibida na tela Sobre (`IpodClassicScreen.kt`). Conferir ambos antes de gerar o
APK ou AAB.

## Estado atual
- Base: MediaPod Android Kotlin/Compose; versão 0.3.22, código 104.
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
Validar em Android Auto físico a retomada da última rádio e a identificação da
estação: conectar ao carro repetidas vezes, confirmar o início após a negociação de
750 ms, inclusive com o telefone bloqueado, e a ausência de “Unknown source” em
Favoritas, Recentes e retomada. Depois, validar visualmente em AMOLED os dois
ciclos de Dock, bateria e a reprodução/autoplay.

## Avaliação local — 24/09/2026
- `python scripts/check_project.py` aprovado: 41.551 rádios, quatro rankings Top 20, 74 recursos de texto em sete idiomas e backup automático desativado.
- A suíte histórica em `reports/checklist-accessibility-tests.log` passou, mas pertence ao código anterior ao renomeio de namespace e à versão 0.3.8. A suíte atual `:app:testDebugUnitTest` gerou 60 XMLs, totalizando 206 testes, sem falhas nem erros.
- Corrigido: `debug` usa `debugConfig`; os botões Favoritar e Opções da biblioteca têm alvo de 48 dp com ícones de 17 dp; APKs/AABs/ZIP legados saíram do índice e permanecem locais, protegidos por `.gitignore`.
- O refactor incompleto de `IpodClassicScreen.kt`, que bloqueava a compilação, foi retirado sem afetar as alterações de Cast. `:app:assembleRelease` concluiu e gerou somente o APK 0.3.9 (91) em `app/build/outputs/apk/release/app-release.apk`.
- Geografia: Rádio Evangelizar Família foi corrigida para Curitiba/PR a partir da página oficial da emissora; restam 523 entradas sem localidade confirmada.
- Playlists: as interações de criar, renomear, escolher, mover e remover agora usam modal LCD com cores do tema selecionado; `MediaLibraryComposeTest` passou (3/3) e o APK release 0.3.9 foi recompilado.
- Biblioteca e Configurações: Minha biblioteca usa moldura LCD, abas e fonte/escala ativas; atualização de catálogo e diagnóstico passaram a usar controles LCD. `MediaLibraryComposeTest` passou (3/3).
- Modal de playlists: a UI deixou de usar `Dialog` do Android e passou a ser hospedada no conteúdo LCD de `IpodClassicScreen`; ela recebe o limite do visor e não cobre a carcaça ou a roda. `MediaLibraryComposeTest` passou (3/3); `:app:compileDebugKotlin` e `:app:assembleRelease` passaram. APK release 0.3.9 (91) atualizado.
- Minha biblioteca removida por solicitação: foram eliminados o item do menu raiz, `PERSONAL_LIBRARY`, a tela `PersonalLibraryScreen` e o recurso `library_title` nos sete idiomas. O repositório de mídia continua para playlists, favoritos, recentes, recuperação de arquivos e backup. Versão 0.3.10 (92) gerada com `:app:assembleRelease`; checkpoint local `40bbb65` criado. Próximo passo: obter aprovação explícita para enviar o conteúdo ao GitHub `origin` e executar `git push origin internaciona`.
- Capas oficiais: a auditoria brasileira gravou 15 imagens únicas declaradas pelas páginas das emissoras e retirou 470 imagens repetidas de agregadores. O script `scripts/official_radio_artwork.py` produz a evidência em `reports/official-brazil-radio-artwork.json`. Easter egg: a primeira exibição agora desativa-o de forma persistente e o gesto de agitar não pode reativá-lo; o item Efeito Visual da Traseira saiu de Configurações. A transmissão econômica apenas alterna um estado de modo de reprodução e não implementa economia de dados ou estabilização; aguardar decisão do usuário antes de alterá-la.
- Transmissão Econômica e Estável removida por solicitação: não restaram interface, preferência persistida, estado de player, enum de modo ou indicação no serviço. `:app:compileDebugKotlin` passou. Próximo passo: validar fisicamente o Cast de MP3 e vídeo local; depois, continuar a auditoria geográfica com fontes verificáveis.
- Correção de compilação em cascata, Autoplay e Marcações LCD (Versão 0.3.12 / 94 - 24/09/2026):
  1. Erro em cascata eliminado em `IpodClassicScreen.kt`: fechamento de chaves corrigido no bloco Box do visor LCD.
  2. Autoplay auditado e corrigido em `RadioMediaService.kt`: conexão de controladores genéricos (ex: Bluetooth ou barra de sistema navegando raiz) não dispara mais reprodução forçada em `onGetLibraryRoot`; conexão ao Android Auto respeita estritamente a preferência do usuário `isAutoPlayOnLaunch` configurada; `autoPlayLastMediaIfIdle` respeita `userInitiatedPause` do `RadioPlayerManager`.
  3. Marcações de Podcast (`PODCAST_BOOKMARKS` e `PODCAST_BOOKMARKS_LIST`): navegação hierárquica em árvore de 2 níveis (Nível 1: Pastas/Programas -> Nível 2: Lista de marcações) 100% operável via Click Wheel (`SelectableLazyColumn`); botão de marcar em largura total na tela de reprodução e janela `BookmarkEditor` totalmente contida no visor LCD com tema retrô ativo, compatível com a suíte de acessibilidade.
  4. Validação completa: `:app:compileDebugKotlin` e `:app:testDebugUnitTest` passaram (206 testes unitários, 0 falhas).

- Dock Mode AMOLED Anti Burn-In (24/09/2026): o relógio e a data compartilham um
  `Box` com offset de bounce próprio, a aproximadamente 105 dp/s e reflexão em
  limites de ±20% da tela. O pixel shift global continua separado. A barra de
  controles inicia no rodapé e alterna com o topo a cada 60 s em 600 ms; o relógio
  anima para a metade oposta. `:app:compileDebugKotlin` passou. A validação visual
  em hardware AMOLED continua pendente e não foi marcada como executada.

- Release 0.3.16 (98) — 24/09/2026: versão incrementada e
  `:app:assembleRelease` concluído com sucesso. O APK release assinado está em
  `app/build/outputs/apk/release/app-release.apk`, confirmado por
  `output-metadata.json`.

- Indicador de bateria (24/09/2026): `BatteryStatusIndicator` registra
  `ACTION_BATTERY_CHANGED` e substitui a bateria decorativa do cabeçalho superior.
  O Dock exibe o mesmo estado com porcentagem; o raio aparece durante carregamento e
  com carga completa conectada. `:app:compileDebugKotlin` passou. Falta validar a
  transição em aparelho físico com carregador.

- Release 0.3.17 (99) — 24/09/2026: `:app:assembleRelease` concluído com
  sucesso. O APK release assinado está em
  `app/build/outputs/apk/release/app-release.apk`, e `output-metadata.json`
  confirma a versão 0.3.17 (99).

- Retomada e Android Auto (25/09/2026): removido o atraso fixo de 400 ms na
  retomada pelo `RadioViewModel`. O `RadioPlayerManager` passou a preparar rádio
  com `mediaId` `radio_<id>` e `RequestMetadata`, iguais ao catálogo do
  `RadioMediaService`; antes, o ID cru podia deixar a fonte desconhecida no Android
  Auto. `:app:testDebugUnitTest` passou. Falta a verificação física com Android Auto.

- Backup sem senha e release 0.3.18 (100) — 25/09/2026: exportações novas são
  criptografadas com AES-GCM usando a chave interna, sem solicitar senha; URLs de
  rádios personalizadas seguem no payload cifrado para permitir a restauração. O
  formato v1 legado restaura automaticamente e o v2 pede a senha original apenas
  quando detectado. Teste cobre cifra sem texto claro de URL e leitura sem senha.
  `:app:testDebugUnitTest` e `:app:assembleRelease` passaram; APK assinado em
  `app/build/outputs/apk/release/app-release.apk`.

- Release 0.3.19 (101) — 25/09/2026: tela Sobre atualizada para 2026.09.25 e
  versão 0.3.19 (101), conforme a regra de manter data e versão sincronizadas
  com cada incremento. `:app:assembleRelease` passou; o APK assinado está em
  `app/build/outputs/apk/release/app-release.apk`, confirmado por
  `output-metadata.json`.

- Android Auto — correção de origem desconhecida (25/09/2026): o `MediaItem`
  ativo agora preserva exatamente o `mediaId` devolvido pela árvore
  (`radio_fav_*`, `radio_rec_*` ou `radio_*`) durante seleção, retomada e
  fallback de stream. O autoplay foi removido da consulta de raiz e agendado uma
  única vez, 750 ms após a aceitação da conexão. `:app:compileDebugKotlin` e
  `AndroidAutoMediaTreeTest` passaram; falta confirmação no veículo.

- Release 0.3.20 (102) — 25/09/2026: correção do Android Auto incorporada,
  versão e tela Sobre sincronizadas em 2026.09.25. `:app:assembleRelease`
  passou em 4m33s com R8, lint e assinatura; APK confirmado por
  `output-metadata.json`.

- Android Auto em segundo plano (25/09/2026): a sessão passou a anunciar
  `Player.COMMAND_SET_MEDIA_ITEM`, requisito do Media3 para o controlador do carro
  selecionar e restaurar rádios enquanto a Activity está bloqueada. A trava não é
  mais aplicada pelo contrato de comandos do app. `:app:compileDebugKotlin`
  passou; validar no veículo.

- Release 0.3.21 (103) — 25/09/2026: correção do player de vídeo incorporada;
  seleção de outro vídeo agora compara o item anterior antes de atualizar o
  estado e reinicia corretamente itens em `ENDED`. `:app:assembleRelease`
  passou em 4m40s com R8, lint e assinatura; APK confirmado por
  `output-metadata.json`.


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

Diagnóstico Alpha FM 101.7 (25/09/2026): APK debug instalado no aparelho ADB
`HMQ8PJHY4LHI7PNZ`. O log confirmou EOF no stream AAC da Alpha e respostas 404/400
nas URLs de fallback geradas automaticamente; evidências em
`reports/alpha-fm-debug-log.txt` e `reports/alpha-fm-debug-after-fix.txt`.
`RadioStation.getAllStreamCandidates()` foi ajustado para não gerar endpoints
StreamTheWorld; `:app:compileDebugKotlin` e `:app:assembleDebug` passaram.
Pendente: desinstalar/reinstalar ou limpar dados para remover URLs antigas
persistidas e repetir o teste físico da primeira execução.

Teste limpo repetido (25/09/2026): após reinstalação, a Alpha ainda encerrou a
URL oficial `RADIO_ALPHAFM_ADP.aac` com EOF, mas não houve mais HTTP 404/400 nem
fallback artificial; o player repetiu somente a URL cadastrada. Evidência em
`reports/alpha-fm-clean-test.txt`. O servidor da emissora continua sendo o
fator externo observado; não há fonte alternativa cadastrada no catálogo.
Na segunda tentativa a transmissão estabilizou no aparelho, confirmando a
recuperação esperada sem os fallbacks artificiais.

Release 0.3.22 (104) preparada em 25/09/2026: versão e tela Sobre atualizadas;
`:app:assembleRelease` passou e `output-metadata.json` confirmou `versionCode
104`/`versionName 0.3.22`; commit `8bbff77` criado e enviado para
`origin/internaciona`.

Os documentos sobrevivem ao fechamento da IDE. A execução requer uma sessão do
agente aberta; não é prometida execução autônoma com a IDE fechada. Ao iniciar uma
nova sessão neste diretório, AGENTS.md direciona a leitura das pendências.
