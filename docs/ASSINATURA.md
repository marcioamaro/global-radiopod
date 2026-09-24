# Assinatura de distribuição

As senhas são lidas de MEDIAPOD_KEYSTORE_PASSWORD e MEDIAPOD_KEY_PASSWORD ou das
propriedades locais mediapod.keystore.password e mediapod.key.password.
local.properties e os arquivos *.jks/*.keystore são ignorados pelo Git. Nunca
imprimir esses valores em logs. A chave original foi preservada no disco.

A remoção do índice não elimina versões anteriores do histórico. Como a chave e
a senha já estiveram versionadas, deve-se avaliar a exposição dos clones/remotos
e solicitar redefinição da chave de upload no Play Console, se aplicável. Isso
depende do titular da conta e não foi executado. Não substituir uma chave de
assinatura de aplicativos já instalados sem verificar o mecanismo de atualização.

Validação local: executar validateSigningRelease e gerar um release. Uma cópia
nova do repositório precisa receber a chave e as credenciais por canal privado.
