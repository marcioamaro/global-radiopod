# Estado de retomada

## Instrução inicial
Leia AGENTS.md e CHECKLIST_PROJETO.md. Confira as tarefas pendentes e continue a
execução autorizada. Confira alterações locais antes de editar. Não reinicie do
zero nem suponha que uma tarefa em andamento terminou.

## Estado atual
- Base: MediaPod Android Kotlin/Compose; versão 0.3.10, código 92.
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
Validar fisicamente o Cast de MP3 e vídeo local em TV/Nest Mini com o aparelho conectado. Em seguida, continuar a auditoria das 523 localidades de rádio restantes, alterando somente registros com evidência da própria emissora ou fonte pública confiável.

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
