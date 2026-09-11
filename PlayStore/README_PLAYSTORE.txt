========================================================================
 PASTA DE PUBLICAÇÃO GOOGLE PLAY STORE - MEDIAPOD + RADIO / PODCAST
========================================================================

Data de Geração: 2026.09.11
Nome do Aplicativo: MediaPod + Radio / Podcast
Nome do Pacote (Application ID): com.marcioamaro.mediapod
Versão (versionName): 0.2
Código de Versão (versionCode): 2
Faixa de Lançamento Recomendada: Teste Fechado (Closed Testing)

------------------------------------------------------------------------
 ARQUIVOS DA VERSÃO 0.2 (TESTE FECHADO)
------------------------------------------------------------------------

1. MediaPod-RadioPodcast-v0.2.aab (13.25 MB)
   - Arquivo oficial Android App Bundle (.aab) da versão 0.2.
   - OBRIGATÓRIO para upload no Google Play Console na faixa de Teste Fechado (Closed Testing) ou Produção.
   - Assinado com a chave de upload oficial e otimizado com ProGuard/R8.

2. MediaPod-RadioPodcast-v0.2.apk (7.27 MB)
   - APK universal assinado em modo Release da versão 0.2.
   - Útil para instalar diretamente no seu aparelho físico para testes rápidos.

3. mapping.txt (84.7 MB)
   - Arquivo de mapeamento e desofuscação de símbolos ProGuard/R8 da versão 0.2 (versionCode 2).
   - Deve ser enviado na seção "Arquivos do pacote de apps" do Play Console para diagnóstico de eventuais ANRs ou erros.

4. NOTAS_DA_VERSAO_v0.2.txt
   - Texto pronto para copiar e colar no campo "Notas da versão" no Play Console (em português e inglês).

5. icon-512x512.png (223 KB)
   - Logotipo oficial em alta resolução (512x512 pixels com canal alfa).

6. mediapod-upload-key.jks & CHAVE_DE_ASSINATURA.txt
   - Chave privada de assinatura de produção (Keystore RSA 2048-bit com validade de 10.000 dias).
   - O .aab e o .apk já estão 100% assinados com essa chave!

7. Pasta "prints_promocionais" (Screenshots 9:16):
   - 01_menu_retro_lcd.jpg: Tela inicial do iPod com menu real (Rádio, Podcasts, Músicas, Vídeos, etc.), tela LCD verde clássica e Click Wheel com logo.
   - 02_radios_ao_vivo_rds.jpg: Tela de reprodução de rádio com RDS, frequência, metadados e visualizador de espectro sonoro retrô.
   - 03_podcasts_favoritos.jpg: Tela de reprodução de podcasts com controles de salto (-15s/+30s), velocidade 1.0x e barra de progresso.
   - 04_android_auto.jpg: Mockup da central multimídia com Android Auto integrado tocando o MediaPod no painel do carro.

------------------------------------------------------------------------
 PASSO A PASSO PARA SUBIR NO GOOGLE PLAY CONSOLE (TESTE FECHADO)
------------------------------------------------------------------------
1. Acesse o Google Play Console: https://play.google.com/console
2. Selecione seu app: "MediaPod + Radio / Podcast" (com.marcioamaro.mediapod).
3. No menu lateral esquerdo, vá em "Teste" > "Teste fechado".
4. Clique na faixa de teste fechado criada (ou "Gerenciar faixa" / "Criar nova versão").
5. No topo direito, clique em "Criar nova versão".
6. Na seção "Pacotes de apps", faça o upload do arquivo:
   D:\global-radiopod - Copia\PlayStore\MediaPod-RadioPodcast-v0.2.aab
7. O Play Console reconhecerá automaticamente:
   - Código da versão: 2
   - Nome da versão: 0.2
8. No campo "Notas da versão", copie e cole o conteúdo de NOTAS_DA_VERSAO_v0.2.txt.
9. Clique em "Salvar" e em seguida "Revisar versão".
10. Clique em "Iniciar lançamento no teste fechado".

------------------------------------------------------------------------
 CHECKPOINT GIT
------------------------------------------------------------------------
Commit: 83a83a0
Tags: v0.2-PlayStore | v0.2
========================================================================
