# Atualização local

Execute `python scripts/package_catalog.py` para validar e gerar
`reports/mediapod-catalog.zip`. Em Configurações, use **Importar atualização de
catálogo e Tops**, selecione o pacote e aguarde a confirmação. Feche e reabra o
aplicativo para ativar as duas fontes juntas.

O pacote contém apenas `radio_catalog.json` e `media_rankings.json`. A importação
rejeita arquivos inesperados, duplicados, caminhos de diretórios, conteúdo acima
de 64 MB, IDs repetidos, rankings incompletos e rádios dos Tops ausentes da base.
O ponteiro da versão ativa só é substituído após validação completa. Falha de
importação mantém a versão anterior. Se a versão persistida estiver corrompida
na abertura, o aplicativo usa a base original incluída na instalação.

Não existe atualização remota automática: nenhuma origem remota foi configurada.
A validação estrutural não comprova a autoria de um pacote; importe somente
arquivos preparados para o projeto. Favoritos e dados pessoais não são enviados.
