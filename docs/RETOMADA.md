# Estado de retomada

## Instrução inicial
Leia AGENTS.md e CHECKLIST_PROJETO.md. Confira as tarefas pendentes e continue a
execução autorizada. Confira alterações locais antes de editar. Não reinicie do
zero nem suponha que uma tarefa em andamento terminou.

## Estado atual
- Base: MediaPod Android Kotlin/Compose; versão 0.3.6, código 88.
- Projeto: `D:\global-radiopod - Copia`; shell PowerShell.
- Alterações anteriores extensas e não commitadas: preservar.
- Pedido prioritário concluído: rótulos Top 20, busca independente dos Tops e build 0.3.6 (88).
- Itens 02, 03 e 12 concluídos; assinatura local ajustada, rotação externa pendente (01).
- Backup por senha e testes da interface aprovados; recuperação transacional implementada.
- Próximo passo: concluir revisão de tradução/acessibilidade, testar economia de dados e executar regressões da biblioteca; então gerar versão 0.3.7 (89).
- Biblioteca pessoal integrada ao menu: Continuar, Marcadores, Assinaturas OPML, Playlists inteligentes e Descobrir. Testes de domínio e backup aprovados em reports/checklist-library-validation.log.
- Preferências de dados e tema LCD dos novos controles ainda aguardam compilação/testes após as últimas edições.
- UI traduzida parcialmente por scripts/localize_checklist_ui.py; completar mensagens antes de concluir 18.
- Backup em nuvem inicialmente recusado por revisão automática. Alternativa segura aplicada com aprovação automática: allowBackup=false e exclusões em cloud/device-transfer; manter backup manual por senha.
- Nenhuma publicação ou instalação em dispositivo autorizada implicitamente.

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
