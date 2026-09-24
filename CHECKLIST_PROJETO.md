# Checklist de execução autorizada

Pedido original (23/09/2026): executar os 24 ajustes da análise, marcar conforme
concluídos e manter instrução inicial para verificar pendências e continuar após
fechamento da IDE. Nada é considerado concluído somente por existir código.

Estados: `[ ]` pendente; `[~]` em andamento; `[x]` implementado e verificado;
`[!]` dependência externa documentada. A evidência deve acompanhar a conclusão.

- [!] 01 — Assinatura: remover senhas padrão do build, retirar chave do índice Git sem apagar arquivo local; documentar avaliação de rotação da chave de upload (conta externa).
- [x] 02 — Banco: migração explícita sem apagar favoritos; teste de preservação.
- [x] 03 — Catálogo: integrar as 12 rádios exclusivas dos Tops à busca, país e filtros; testar identidade e reprodução.
- [x] 04 — Backup por senha: formato autenticado novo, interface de senha, leitura dos backups legados e testes de senha incorreta.
- [x] 05 — Restauração: limites, validação prévia, versão, restauração consistente e recuperação de falha parcial.
- [x] 06 — YouTube: restringir navegação e exposição da ponte JavaScript nos três players; testar URLs e ciclo de vida.
- [x] 07 — Rede: certificados de usuário somente no debug; política de HTTP compatível com rádios legadas e HTTPS preferido.
- [~] 08 — Geografia: auditar metadados, corrigir inconsistências confirmadas e registrar casos sem evidência sem inventar localidades.
- [x] 09 — Podcasts: recuperação de feed por identidade verificada, sem associação por título parcial.
- [!] 10 — Dispositivos reais: matriz de áudio, segundo plano, Bluetooth, chamadas, rede, Android Auto e Cast; depende de aparelhos disponíveis.
- [x] 11 — Atualização de catálogo/ranking: importar atualização validada sem reinstalação, atomicidade e fallback; atualização remota requer origem confiável configurada.
- [x] 12 — Catálogo único: eliminar lista Kotlin gerada gigante e usar fonte de dados única com compatibilidade de IDs.
- [x] 13 — Sincronização: persistir versão do catálogo e atualizar banco somente quando mudar.
- [x] 14 — Playlists: identidade estável e recuperação de arquivos movidos, sem associação ambígua.
- [x] 15 — Downloads: fila limitada, pausa/retomada e recuperação após encerramento.
- [x] 16 — Diagnóstico: erros acionáveis e relatório exportável sanitizado.
- [~] 17 — Manutenção: dividir responsabilidades, automatizar regressões e configurar regras de backup Android.
- [~] 18 — Acessibilidade/tradução: alvos de toque, semântica, contraste, fonte ampliada e recursos de texto; validar telas críticas.
- [~] 19 — Continuar: tela de retomada de podcasts, músicas e vídeos.
- [~] 20 — Marcadores: horários e anotações de episódios, edição e backup.
- [~] 21 — Playlists inteligentes: não ouvidos, baixados, favoritos recentes e episódios curtos com critérios configuráveis.
- [~] 22 — OPML: importar/exportar assinaturas com limites, validação e deduplicação.
- [~] 23 — Economia de dados: preferências para bitrate, capas e downloads respeitadas pelos players.
- [~] 24 — Descoberta local opcional: recomendações por interesse/histórico, controle de ativação e exclusão de dados.

## Prioridade adicional solicitada antes dos 24 itens

- [x] P0 — Corrigir todos os rótulos de Top para 20 e gerar versão 0.3.6 (88) antes de continuar a lista. Incluir separação do estado de busca de podcasts, carregamento da base completa e cancelamento de consultas antigas, conforme pedido adicional.

## Critério de entrega

Cada item precisa de implementação utilizável, teste proporcional e registro de
limitações. Não basta criar classes sem integrá-las à interface. Ao final, executar
regressões pertinentes e gerar APK/AAB; não publicar automaticamente.

## Evidências e histórico

- 23/09/2026: checklist e protocolo de retomada criados. Base existente 0.3.5 (87),
  com modificações de sessões anteriores que devem ser preservadas. Nenhum dos
  24 itens foi marcado como concluído nesta criação.

- P0 concluído: versão 0.3.6 (88), assembleRelease e bundleRelease bem-sucedidos. Evidência: `reports/top20-search-release.log`; 2 testes de busca completa e 2 de rankings passaram. Rótulos conferidos nos 7 idiomas. APK/AAB em `app/build/outputs`. Demais 24 itens continuam pendentes.

- Itens 01/02: validateSigningRelease e RadioDatabaseMigrationTest passaram em `reports/checklist-first-tests.log`. Senhas padrão removidas, chave retirada do índice e preservada no disco. Item 01 aguarda avaliação/rotação pelo titular da conta; ver docs/ASSINATURA.md. Item 02 comprovou preservação de favorito e data na migração 1 → 2.

- Itens 03/12: `reports/checklist-catalog-tests.log` passou. 41.551 rádios em JSON, incluindo as 12 exclusivas dos Tops; testes de busca, URLs, Rádio Atual e atualização do catálogo passaram. Fachada Kotlin reduzida a 244 linhas. Item 13 aguarda reforço do teste de ausência de regravação.

- Itens 04/07/09/13: testes aprovados em `reports/checklist-security-tests.log` e `reports/checklist-restore-tests.log`; senha nova/legada, interface, identidade de feed, preferência HTTPS e ausência de regravação do catálogo.
- Item 05: validação de coleções antes de escrever, versão/tamanho e recuperação durável após interrupção aprovados em `reports/checklist-restore-validation.log`; revisar campos internos antes de concluir.
- Item 08: 4 correções confirmadas e 524 localidades sem confirmação registradas em `reports/checklist-geography-audit.json`; filtros de cidades usam o catálogo real.
- Item 10: sem aparelho disponível; matriz em `docs/TESTES_DISPOSITIVOS.md`.
- Item 17: componentes separados e fluxo de CI criado; verificação offline `python scripts/check_project.py` aprovada. CI remoto ainda não executado. Alteração de backup em nuvem recusada pela revisão automática por dados/destino sem autorização específica; não aplicada.

- 24/09/2026 — Itens 05/06: `reports/checklist-lifecycle-tests.log` aprovado; validação de campos, recuperação de interrupção e descarte do WebView verificados. Testes reais seguem no item 10.
- Item 11: importação local integrada em Configurações; teste de troca atômica, pacote inválido e fallback aprovado em `reports/checklist-update-tests.log`. Pacote gerado por `scripts/package_catalog.py`; origem remota não configurada.
- Item 14: recuperação por SHA-256 preserva favoritos, recentes e ordem; cópias ambíguas permanecem sem associação. Identidades entram no backup. `reports/checklist-media-recovery-tests.log` aprovado. Arquivos precisam ter identidade capturada antes de serem movidos.
- Item 15: controles de download integrados aos episódios e reprodução offline conectada; dois downloads simultâneos, fila persistida, pausa, Range/If-Range. Testes HTTP aprovados em `reports/checklist-download-tests.log`; teste de fila após reinício pendente.
- Item 16: diagnóstico exportável sem URLs, títulos ou caminhos implementado; teste pendente.

- Itens 15/16: `reports/checklist-queue-diagnostics-tests.log` aprovado, incluindo fila pausada após recriação e relatório limitado a 50 eventos sem campos privados. Recuperação da fila ocorre ao reabrir o app; execução contínua com processo encerrado não é prometida.
- Item 17: alternativa segura aplicada após bloqueio anterior: backup automático/transferência Android desativados; backup manual por senha preservado. Em 24/09, a variante debug voltou a usar `debugConfig` e seis APK/AAB/ZIP foram removidos do índice, preservados no disco e cobertos por `.gitignore`; falta validação final de CI remoto.
- Itens 19–24: biblioteca pessoal conectada ao menu; marcadores, OPML, filtros e descoberta passaram em `reports/checklist-library-validation.log`. Integração de economia de dados e revisão de acessibilidade/traduções em andamento; ainda não marcar o conjunto concluído.
- 24/09/2026 — validação atualizada: `:app:testDebugUnitTest` gerou 60 relatórios XML com 206 testes, 0 falhas e 0 erros. `:app:assembleRelease` gerou somente `app/build/outputs/apk/release/app-release.apk`, versão 0.3.9 (91). O alvo de toque dos botões Favoritar e Opções da biblioteca passou a 48 dp, coberto por `MediaLibraryComposeTest`.
- Item 08: a auditoria passou a registrar 5 correções confirmadas; Rádio Evangelizar Família foi corrigida para Curitiba/PR com fonte da emissora. Permanecem 523 registros sem fonte confirmatória e sem inferência de localidade.
- 24/09/2026 — playlists: criação, renomeação, seleção, movimentação e remoção passaram a abrir modais LCD com a paleta do tema ativo nas telas MP3 e Vídeo. `MediaLibraryComposeTest` passou (3/3) e `:app:assembleRelease` atualizou somente o APK 0.3.9 (91).
- 24/09/2026 — biblioteca/configurações: Minha biblioteca ganhou moldura LCD, abas e tipografia configurada; os controles de importação de catálogo e diagnóstico em Configurações receberam paleta e fonte LCD. `MediaLibraryComposeTest` passou (3/3).
- 24/09/2026 — modal de playlists: a janela Android foi substituída por um host Compose dentro da área ativa do visor LCD; criar, renomear e manipular playlists ficam limitados ao conteúdo do iPod. `MediaLibraryComposeTest` passou (3/3) e `:app:assembleRelease` gerou somente o APK 0.3.9 (91).
- 24/09/2026 — por solicitação do usuário, Minha biblioteca foi removida recursivamente da navegação: item do menu inicial, destino, cabeçalho, tela e rótulos traduzidos. Dados de mídia, playlists, favoritos e backup foram preservados por serem usados pelos players. `:app:compileDebugKotlin` e `:app:assembleRelease` passaram; APK 0.3.10 (92) gerado.
