# Entrega do checklist — MediaPod 0.3.7 (89)

O estado verificável de cada item está em `CHECKLIST_PROJETO.md`. Uma tarefa só
recebe `[x]` após a verificação correspondente. Os binários desta versão ainda
dependem do término bem-sucedido de `reports/checklist-final-release.log`.

## Onde encontrar as funções

- **Minha biblioteca**, no menu principal: continuar podcasts, músicas e vídeos;
  editar/reproduzir marcadores; assinaturas OPML; playlists inteligentes;
  descoberta local opcional.
- **Marcar este momento**, durante um podcast: salvar posição e anotação.
- **Menu de download**, na linha do episódio: baixar, pausar, retomar ou excluir.
  A reprodução usa o arquivo salvo quando disponível.
- **Configurações**: backup por senha, importação de catálogo/Tops, diagnóstico
  exportável e preferências de consumo de dados.

## Comportamentos importantes

O backup manual por senha inclui a marcação de episódios ouvidos, URLs do
YouTube, favoritos, playlists, posições locais, marcadores, assinaturas e histórico
de episódios. Arquivos de áudio/vídeo não são incorporados. Backups antigos
continuam legíveis. O backup automático/transferência do Android foi desativado;
use a exportação manual para migrar dados entre aparelhos.

A recuperação de arquivos movidos compara SHA-256. É necessário ter capturado a
identidade antes da mudança; cópias idênticas ambíguas não são associadas por
adivinhação. Referências indisponíveis são preservadas.

Downloads têm limite de duas transferências simultâneas. A fila persiste e é
recuperada ao reabrir o app. A retomada parcial depende de suporte do servidor;
uma resposta completa substitui o parcial. Downloads pausados permanecem
pausados após reinício. Não há promessa de execução com o processo encerrado.

Playlists inteligentes usam episódios carregados, recentes e baixados. O filtro
de duração não classifica episódios com duração desconhecida como curtos.
A descoberta fica desativada por padrão; interesses e uso do histórico são
controlados na própria tela e não são enviados para recomendadores externos.

A preferência de bitrate depende das alternativas da fonte. Ela não converte
arquivos de bitrate fixo nem controla o player do YouTube. A atualização de
catálogo é local e exige reabrir o aplicativo; nenhuma origem remota foi ativada.

## Dependências externas

- Item 01: avaliação/rotação da chave de upload pelo titular da conta. A chave
  local foi preservada e a configuração deixou de conter senhas padrão.
- Item 10: testes em aparelhos, Bluetooth, chamadas, Android Auto e Cast.
  A matriz está em `TESTES_DISPOSITIVOS.md`; não houve validação física.

A auditoria geográfica registra 4 correções confirmadas e 524 localidades sem
confirmação. Esses registros não foram preenchidos com localidades presumidas.
O fluxo de CI foi preparado; a execução remota ainda não foi realizada.
