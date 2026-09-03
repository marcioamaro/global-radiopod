# REGRAS DE OURO: Resiliência de Streaming, Degradação Progressiva e Modo Streaming Puro

## Contexto Crítico
Servidores de rádio frequentemente travam a cada poucos segundos devido à sobrecarga ao servir áudio e metadados ICY inline simultaneamente. A continuidade do áudio é **NÃO NEGOCIÁVEL**; metadados são **DESCARTÁVEIS**.

## 1. Máquina de Estados de Resiliência (`PlaybackMode`)
- `FULL`: Áudio + Metadados (padrão inicial).
- `AUDIO_ONLY`: Modo Streaming Puro — apenas áudio, sem extração de metadata.
- `FALLBACK_URL`: URL alternativa em modo `AUDIO_ONLY`.

## 2. Gatilhos de Degradação
- **Stutter Detection**: > 3 transições para `STATE_BUFFERING` em 15s → transição imediata para `AUDIO_ONLY`.
- **Stall Timer**: `currentPosition` parado por 8s em `STATE_READY` → transição para `AUDIO_ONLY`.
- **Reconexão Manual**: Ação do usuário força `AUDIO_ONLY` imediatamente na URL atual.
- **Falha de Metadata**: 3 falhas consecutivas de metadata desativam extração sem interromper áudio.

## 3. Modo Streaming Puro no ExoPlayer
Ao entrar em `AUDIO_ONLY`:
- Cancelar busca/parse de metadata ICY e jobs externos.
- Criar `MediaSource` usando `ExtractorsFactory` apenas com extratores de áudio puro (`Mp3Extractor`, `AdtsExtractor`, `OggExtractor`) SEM `MetadataExtractor`.

## 4. Android Auto & Google Cast
- **Android Auto**: Parar de chamar `mediaSession.setMetadata()`. O Auto mantém automaticamente a última informação válida na tela. Nunca enviar metadados vazios ou placeholders.
- **Google Cast**: Parar de enviar updates via `RemoteMediaClient.setMediaMetadata()`. O receiver continua tocando e exibe o último título conhecido.
- **UI Mobile**: Mostrar indicador sutil ("Modo Estável" / 🔊).

## 5. Estratégia em Cascata de URLs
1. `primary` em modo `FULL` → falhou/stutter?
2. `primary` em modo `AUDIO_ONLY` → falhou?
3. `backup candidate 1..N` em modo `AUDIO_ONLY` → falhou?
4. `lowQuality` em modo `AUDIO_ONLY` (último recurso).

*Regra*: Nunca reativar extração inline na mesma sessão após entrar em modo degradado, exceto se o usuário trocar explicitamente de estação ou após 5 minutos de estabilidade ininterrupta via polling externo isolado.
