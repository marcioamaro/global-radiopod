"""Resource catalog for the checklist features (all supported interface languages)."""
from pathlib import Path
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parents[1]
# key | Portuguese | English | Spanish | French | German | Italian | Japanese
ROWS = '''library_title|Minha biblioteca|My library|Mi biblioteca|Ma bibliothèque|Meine Bibliothek|La mia raccolta|マイライブラリ
library_continue|Continuar|Continue|Continuar|Continuer|Fortsetzen|Continua|続ける
library_bookmarks|Marcadores|Bookmarks|Marcadores|Signets|Lesezeichen|Segnalibri|ブックマーク
library_subscriptions|Assinaturas|Subscriptions|Suscripciones|Abonnements|Abonnements|Abbonamenti|購読
library_smart|Playlists inteligentes|Smart playlists|Listas inteligentes|Listes intelligentes|Intelligente Playlists|Playlist intelligenti|スマートプレイリスト
library_discover|Descobrir|Discover|Descubrir|Découvrir|Entdecken|Scopri|見つける
feature_save|Salvar|Save|Guardar|Enregistrer|Speichern|Salva|保存
feature_cancel|Cancelar|Cancel|Cancelar|Annuler|Abbrechen|Annulla|キャンセル
feature_edit|Editar|Edit|Editar|Modifier|Bearbeiten|Modifica|編集
feature_delete|Excluir|Delete|Eliminar|Supprimer|Löschen|Elimina|削除
bookmark_title|Marcador do episódio|Episode bookmark|Marcador del episodio|Signet de l’épisode|Episoden-Lesezeichen|Segnalibro episodio|エピソードのブックマーク
bookmark_seconds|Tempo em segundos|Time in seconds|Tiempo en segundos|Temps en secondes|Zeit in Sekunden|Tempo in secondi|秒数
bookmark_note|Anotação|Note|Nota|Note|Notiz|Nota|メモ
bookmark_add|Marcar este momento|Bookmark this moment|Marcar este momento|Marquer ce moment|Diesen Moment merken|Segna questo momento|この位置を保存
opml_import|Importar OPML|Import OPML|Importar OPML|Importer OPML|OPML importieren|Importa OPML|OPMLを読み込む
opml_export|Exportar OPML|Export OPML|Exportar OPML|Exporter OPML|OPML exportieren|Esporta OPML|OPMLを書き出す
subscription_remove|Remover assinatura|Unsubscribe|Cancelar suscripción|Se désabonner|Abonnement beenden|Annulla abbonamento|購読解除
smart_unplayed|Não ouvidos|Unplayed|Sin escuchar|Non écoutés|Ungehört|Non ascoltati|未再生
smart_downloaded|Baixados|Downloaded|Descargados|Téléchargés|Heruntergeladen|Scaricati|ダウンロード済み
smart_favorites|Favoritos recentes|Recent favorites|Favoritos recientes|Favoris récents|Letzte Favoriten|Preferiti recenti|最近のお気に入り
smart_short|Episódios curtos|Short episodes|Episodios cortos|Épisodes courts|Kurze Episoden|Episodi brevi|短いエピソード
smart_play|Reproduzir playlist|Play playlist|Reproducir lista|Lire la liste|Playlist abspielen|Riproduci playlist|プレイリストを再生
discovery_enable|Ativar descoberta local|Enable local discovery|Activar descubrimiento local|Activer la découverte locale|Lokale Empfehlungen aktivieren|Attiva scoperta locale|ローカルのおすすめを有効化
discovery_history|Usar categorias do histórico|Use history categories|Usar categorías del historial|Utiliser les catégories de l’historique|Kategorien aus Verlauf verwenden|Usa categorie della cronologia|履歴のカテゴリを使用
discovery_interests|Interesses separados por vírgula|Interests separated by commas|Intereses separados por comas|Centres d’intérêt séparés par des virgules|Interessen, durch Kommas getrennt|Interessi separati da virgole|興味のある項目をカンマ区切りで入力
discovery_clear|Apagar preferências de descoberta|Clear discovery preferences|Borrar preferencias de descubrimiento|Effacer les préférences de découverte|Empfehlungseinstellungen löschen|Cancella preferenze di scoperta|おすすめ設定を削除
data_title|Economia de dados|Data usage|Uso de datos|Utilisation des données|Datennutzung|Utilizzo dati|データ使用量
data_artwork|Carregar capas da internet|Load online artwork|Cargar portadas de internet|Charger les pochettes en ligne|Cover aus dem Internet laden|Carica copertine online|オンラインの画像を読み込む
data_wifi|Downloads somente por Wi-Fi|Download over Wi-Fi only|Descargar solo por Wi-Fi|Télécharger uniquement en Wi-Fi|Nur über WLAN herunterladen|Scarica solo tramite Wi-Fi|Wi-Fi接続時のみダウンロード
data_auto|Qualidade automática|Automatic quality|Calidad automática|Qualité automatique|Automatische Qualität|Qualità automatica|自動音質
data_64|Preferir áudio de 64 kbps|Prefer 64 kbps audio|Preferir audio de 64 kbps|Préférer l’audio à 64 kbps|64-kbps-Audio bevorzugen|Preferisci audio a 64 kbps|64 kbpsの音声を優先
data_128|Preferir áudio de 128 kbps|Prefer 128 kbps audio|Preferir audio de 128 kbps|Préférer l’audio à 128 kbps|128-kbps-Audio bevorzugen|Preferisci audio a 128 kbps|128 kbpsの音声を優先
download_start|Baixar|Download|Descargar|Télécharger|Herunterladen|Scarica|ダウンロード
download_resume|Retomar|Resume|Reanudar|Reprendre|Fortsetzen|Riprendi|再開
download_pause|Pausar|Pause|Pausar|Mettre en pause|Pausieren|Pausa|一時停止
download_delete|Excluir download|Delete download|Eliminar descarga|Supprimer le téléchargement|Download löschen|Elimina download|ダウンロードを削除
download_cancel|Cancelar e remover parcial|Cancel and remove partial file|Cancelar y eliminar archivo parcial|Annuler et supprimer le fichier partiel|Abbrechen und Teildatei löschen|Annulla e rimuovi file parziale|中止して未完了ファイルを削除
download_queued|Na fila|Queued|En cola|En attente|In Warteschlange|In coda|待機中
download_paused|Download pausado|Download paused|Descarga pausada|Téléchargement en pause|Download pausiert|Download in pausa|ダウンロード一時停止中
download_offline|Disponível offline|Available offline|Disponible sin conexión|Disponible hors ligne|Offline verfügbar|Disponibile offline|オフラインで利用可能
download_episode|Baixar episódio|Download episode|Descargar episodio|Télécharger l’épisode|Episode herunterladen|Scarica episodio|エピソードをダウンロード
diagnostic_export|Exportar diagnóstico|Export diagnostics|Exportar diagnóstico|Exporter le diagnostic|Diagnose exportieren|Esporta diagnostica|診断情報を書き出す
diagnostic_clear|Limpar diagnóstico|Clear diagnostics|Borrar diagnóstico|Effacer le diagnostic|Diagnose löschen|Cancella diagnostica|診断情報を削除
backup_protect|Proteger backup|Protect backup|Proteger copia|Protéger la sauvegarde|Sicherung schützen|Proteggi backup|バックアップを保護
backup_restore|Restaurar backup|Restore backup|Restaurar copia|Restaurer la sauvegarde|Sicherung wiederherstellen|Ripristina backup|バックアップを復元
backup_password|Senha|Password|Contraseña|Mot de passe|Passwort|Password|パスワード
backup_confirm|Confirmar senha|Confirm password|Confirmar contraseña|Confirmer le mot de passe|Passwort bestätigen|Conferma password|パスワードの確認
catalog_import|Importar atualização de catálogo e Tops|Import catalog and Top updates|Importar catálogo y Tops|Importer le catalogue et les Tops|Katalog und Tops importieren|Importa catalogo e classifiche|カタログとランキングの更新を読み込む
catalog_validating|Validando catálogo…|Validating catalog…|Validando catálogo…|Validation du catalogue…|Katalog wird geprüft…|Verifica catalogo…|カタログを検証中…'''

ROWS += '''
backup_saved|Backup protegido por senha salvo.|Password-protected backup saved.|Copia protegida por contraseña guardada.|Sauvegarde protégée par mot de passe enregistrée.|Passwortgeschützte Sicherung gespeichert.|Backup protetto da password salvato.|パスワードで保護されたバックアップを保存しました。
backup_save_error|Não foi possível salvar o backup.|Could not save the backup.|No se pudo guardar la copia.|Impossible d’enregistrer la sauvegarde.|Sicherung konnte nicht gespeichert werden.|Impossibile salvare il backup.|バックアップを保存できませんでした。
backup_password_help|Use pelo menos 8 caracteres. Guarde a senha: ela será necessária para restaurar.|Use at least 8 characters. Keep the password: it is required to restore.|Usa al menos 8 caracteres. Guarda la contraseña para restaurar.|Utilisez au moins 8 caractères. Conservez le mot de passe pour restaurer.|Mindestens 8 Zeichen verwenden. Passwort für die Wiederherstellung aufbewahren.|Usa almeno 8 caratteri. Conserva la password per il ripristino.|8文字以上を使用してください。復元に必要なパスワードを保管してください。
backup_legacy_help|Informe a senha. Para backups antigos sem senha pessoal, deixe em branco.|Enter the password. Leave blank for older backups without a personal password.|Introduce la contraseña. Déjala vacía para copias antiguas sin contraseña personal.|Saisissez le mot de passe. Laissez vide pour les anciennes sauvegardes sans mot de passe personnel.|Passwort eingeben. Bei alten Sicherungen ohne persönliches Passwort leer lassen.|Inserisci la password. Lascia vuoto per i vecchi backup senza password personale.|パスワードを入力してください。個人用パスワードのない旧形式は空欄にしてください。
backup_restored|Backup restaurado.|Backup restored.|Copia restaurada.|Sauvegarde restaurée.|Sicherung wiederhergestellt.|Backup ripristinato.|バックアップを復元しました。
backup_restore_error|Não foi possível restaurar. Confira a senha e o arquivo.|Could not restore. Check the password and file.|No se pudo restaurar. Revisa la contraseña y el archivo.|Restauration impossible. Vérifiez le mot de passe et le fichier.|Wiederherstellung fehlgeschlagen. Passwort und Datei prüfen.|Ripristino non riuscito. Verifica password e file.|復元できませんでした。パスワードとファイルを確認してください。
opml_invalid|OPML inválido ou maior que 2 MB. Confira o arquivo e tente novamente.|Invalid OPML or larger than 2 MB. Check the file and retry.|OPML inválido o mayor de 2 MB. Revisa el archivo e inténtalo de nuevo.|OPML invalide ou supérieur à 2 Mo. Vérifiez le fichier et réessayez.|OPML ungültig oder größer als 2 MB. Datei prüfen und erneut versuchen.|OPML non valido o superiore a 2 MB. Controlla il file e riprova.|OPMLが無効か2 MBを超えています。ファイルを確認してください。
opml_exported|Assinaturas exportadas.|Subscriptions exported.|Suscripciones exportadas.|Abonnements exportés.|Abonnements exportiert.|Abbonamenti esportati.|購読情報を書き出しました。
file_save_error|Não foi possível salvar. Escolha outro local.|Could not save. Choose another location.|No se pudo guardar. Elige otra ubicación.|Enregistrement impossible. Choisissez un autre emplacement.|Speichern fehlgeschlagen. Anderen Speicherort wählen.|Salvataggio non riuscito. Scegli un’altra posizione.|保存できませんでした。別の保存先を選んでください。
data_help|A preferência de áudio vale para fontes com qualidades alternativas. Não altera arquivos de bitrate fixo nem o YouTube.|Audio preference applies where quality alternatives exist. Fixed-bitrate files and YouTube are unchanged.|La preferencia se aplica cuando hay calidades alternativas. No cambia archivos de bitrate fijo ni YouTube.|La préférence audio s’applique aux sources proposant plusieurs qualités. Les fichiers à débit fixe et YouTube restent inchangés.|Audiopräferenz gilt bei verfügbaren Qualitätsstufen. Dateien mit fester Bitrate und YouTube bleiben unverändert.|La preferenza audio vale con qualità alternative. Non modifica file a bitrate fisso o YouTube.|音質を選べる配信に適用されます。固定ビットレートのファイルとYouTubeには適用されません。
diagnostic_saved|Relatório salvo. Contém apenas versão, Android, códigos de falha e horários.|Report saved. Only app version, Android, error codes and times are included.|Informe guardado. Solo incluye versión, Android, códigos de error y horarios.|Rapport enregistré : version, Android, codes d’erreur et horaires uniquement.|Bericht gespeichert: nur Version, Android, Fehlercodes und Zeitpunkte.|Rapporto salvato: solo versione, Android, codici di errore e orari.|診断情報を保存しました。バージョン、Android、エラーコード、時刻のみを含みます。
diagnostic_save_error|Não foi possível salvar. Escolha outro local com espaço disponível.|Could not save. Choose a location with available space.|No se pudo guardar. Elige una ubicación con espacio disponible.|Enregistrement impossible. Choisissez un emplacement avec de l’espace libre.|Speichern fehlgeschlagen. Speicherort mit freiem Platz wählen.|Salvataggio non riuscito. Scegli una posizione con spazio libero.|保存できませんでした。空き容量のある保存先を選んでください。
diagnostic_cleared|Registros de diagnóstico apagados.|Diagnostic records cleared.|Registros de diagnóstico borrados.|Données de diagnostic effacées.|Diagnosedaten gelöscht.|Dati diagnostici cancellati.|診断記録を削除しました。
catalog_imported|Atualização validada. Feche e reabra o aplicativo para usar o novo catálogo e os Tops.|Update validated. Close and reopen the app to use the new catalog and Tops.|Actualización validada. Cierra y abre la aplicación para usar el nuevo catálogo y los Tops.|Mise à jour validée. Fermez et rouvrez l’application pour utiliser le catalogue et les Tops.|Aktualisierung geprüft. App schließen und erneut öffnen, um Katalog und Tops zu verwenden.|Aggiornamento verificato. Chiudi e riapri l’app per usare catalogo e classifiche.|更新を検証しました。新しいカタログとランキングを使うにはアプリを開き直してください。
catalog_invalid|Pacote inválido ou ilegível. O catálogo atual foi preservado.|Invalid or unreadable package. The current catalog was preserved.|Paquete inválido o ilegible. Se conservó el catálogo actual.|Paquet invalide ou illisible. Le catalogue actuel a été conservé.|Paket ungültig oder unlesbar. Aktueller Katalog wurde beibehalten.|Pacchetto non valido o illeggibile. Il catalogo attuale è stato mantenuto.|パッケージを読み込めません。現在のカタログは保持されています。
library_loading|Carregando biblioteca…|Loading library…|Cargando biblioteca…|Chargement de la bibliothèque…|Bibliothek wird geladen…|Caricamento raccolta…|ライブラリを読み込み中…
library_resume_help|Selecione para continuar do ponto salvo.|Select an item to continue from the saved position.|Selecciona para continuar desde el punto guardado.|Sélectionnez un élément pour reprendre au point enregistré.|Auswählen, um an der gespeicherten Position fortzusetzen.|Seleziona per riprendere dalla posizione salvata.|保存された位置から再開する項目を選んでください。
bookmark_empty|Use Marcar este momento durante um podcast.|Use Bookmark this moment while playing a podcast.|Usa Marcar este momento durante un podcast.|Utilisez Marquer ce moment pendant un podcast.|Während eines Podcasts Diesen Moment merken verwenden.|Usa Segna questo momento durante un podcast.|ポッドキャスト再生中に「この位置を保存」を使用してください。
smart_scope|Episódios carregados, recentes e baixados|Loaded, recent and downloaded episodes|Episodios cargados, recientes y descargados|Épisodes chargés, récents et téléchargés|Geladene, letzte und heruntergeladene Episoden|Episodi caricati, recenti e scaricati|読み込み済み、最近、ダウンロード済みのエピソード
discovery_privacy|Sugestões calculadas neste aparelho, sem envio do histórico.|Suggestions are calculated on this device without sending history.|Las sugerencias se calculan en este dispositivo sin enviar el historial.|Les suggestions sont calculées sur cet appareil sans transmettre l’historique.|Vorschläge werden auf diesem Gerät ohne Übermittlung des Verlaufs berechnet.|Suggerimenti calcolati sul dispositivo senza inviare la cronologia.|履歴を送信せず、この端末でおすすめを計算します。
opml_added|%1$d assinaturas adicionadas.|%1$d subscriptions added.|%1$d suscripciones añadidas.|%1$d abonnements ajoutés.|%1$d Abonnements hinzugefügt.|%1$d abbonamenti aggiunti.|%1$d件の購読を追加しました。
download_progress|Baixando %1$d%%|Downloading %1$d%%|Descargando %1$d%%|Téléchargement %1$d%%|Download %1$d%%|Download %1$d%%|ダウンロード中 %1$d%%
download_description|Download: %1$s|Download: %1$s|Descarga: %1$s|Téléchargement : %1$s|Download: %1$s|Download: %1$s|ダウンロード：%1$s
smart_duration|Duração máxima: %1$d minutos|Maximum duration: %1$d minutes|Duración máxima: %1$d minutos|Durée maximale : %1$d minutes|Maximale Dauer: %1$d Minuten|Durata massima: %1$d minuti|最大時間：%1$d分
smart_limit|Limite: %1$d episódios|Limit: %1$d episodes|Límite: %1$d episodios|Limite : %1$d épisodes|Limit: %1$d Episoden|Limite: %1$d episodi|上限：%1$dエピソード'''
rows = [line.split('|') for line in ROWS.splitlines()]
assert all(len(row) == 8 for row in rows)
for locale, index in [('values', 2), ('values-pt', 1), ('values-es', 3), ('values-fr', 4), ('values-de', 5), ('values-it', 6), ('values-ja', 7)]:
    xml = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']
    for row in rows:
        value = escape(row[index]).replace("'", "\\'")
        xml.append(f'    <string name="{row[0]}">{value}</string>')
    xml.append('</resources>')
    (ROOT / 'app/src/main/res' / locale / 'feature_strings.xml').write_text('\n'.join(xml)+'\n', encoding='utf-8')

targets = ['ui/components/' + name + '.kt' for name in ('BookmarkControls', 'OpmlControls', 'DataUsageControl', 'DiagnosticsControl', 'PodcastDownloadControl', 'CatalogUpdateControl', 'SecureBackupActions')]
targets += ['ui/screens/PersonalLibraryScreen.kt']
for target in targets:
    file = ROOT / 'app/src/main/java/com/example' / target
    source = file.read_text(encoding='utf-8')
    for key, portuguese, *_ in rows:
        source = source.replace('"'+portuguese+'"', f'context.getString(com.example.R.string.{key})')
    file.write_text(source, encoding='utf-8')
