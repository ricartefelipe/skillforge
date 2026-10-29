#!/usr/bin/env bash
# Reescreve, uma linha por transação, as mídias gravadas antes de `SET STORAGE EXTERNAL`.
#
# Valores antigos ficaram comprimidos no TOAST (pglz). A leitura por faixa continua correta, mas
# cada fatia descomprime o prefixo desde o início: um vídeo de 256 MiB comprimido levou 222 s para
# ser servido inteiro, contra 3 s quando o valor está externo e sem compressão. `SET STORAGE`
# não reescreve nada, de propósito: a inicialização da aplicação não toca dados.
#
# Este procedimento é a transformação explícita: idempotente (só escolhe linhas ainda comprimidas),
# limitado por lote, uma transação por linha, e só confirma se o md5 antes e depois coincidir e o
# valor novo tiver saído da compressão. Interrompido no meio, retoma de onde parou. A linha antiga
# permanece no heap até o VACUUM, então o espaço livre precisa comportar a maior mídia do lote.
#
# Uso: rewrite-media-storage.sh <dsn> [limite por tabela, padrão 10]
#   ex.: rewrite-media-storage.sh postgresql://skillforge:senha@localhost:5432/skillforge 20
set -euo pipefail

dsn="${1:?dsn do Postgres}"
limit="${2:-10}"
psql_cmd=(psql "$dsn" -v ON_ERROR_STOP=1 -qtA)

rewrite_rows() {
    local table="$1" key_cols="$2"
    local ids
    ids="$("${psql_cmd[@]}" -c "SELECT ${key_cols} FROM ${table} WHERE media IS NOT NULL AND pg_column_size(media) < octet_length(media) LIMIT ${limit}")"
    [ -n "$ids" ] || { echo "${table}: nenhuma mídia comprimida"; return 0; }
    while IFS='|' read -r -a key; do
        local where="" i=0
        for col in ${key_cols//,/ }; do
            [ -n "$where" ] && where="$where AND "
            where="$where${col} = '${key[$i]}'"
            i=$((i + 1))
        done
        "${psql_cmd[@]}" <<SQL
DO \$\$
DECLARE before_md5 text; after_md5 text; stored bigint; total bigint;
BEGIN
    SELECT md5(media) INTO before_md5 FROM ${table} WHERE ${where};
    UPDATE ${table} SET media = substring(media from 1) WHERE ${where};
    SELECT md5(media), pg_column_size(media), octet_length(media) INTO after_md5, stored, total FROM ${table} WHERE ${where};
    IF after_md5 <> before_md5 THEN
        RAISE EXCEPTION 'conteúdo divergente após reescrita em %: md5 % -> %', '${table}', before_md5, after_md5;
    END IF;
    IF stored < total THEN
        RAISE EXCEPTION 'valor continua comprimido em %: % de % bytes', '${table}', stored, total;
    END IF;
    RAISE NOTICE '%: % bytes reescritos, md5 % conferido', '${table}', total, after_md5;
END
\$\$;
SQL
    done <<<"$ids"
}

rewrite_rows lesson id
rewrite_rows lesson_asset id
rewrite_rows scorm_file lesson_id,path
echo "concluído; linhas restantes comprimidas:"
"${psql_cmd[@]}" -c "SELECT 'lesson', count(*) FROM lesson WHERE media IS NOT NULL AND pg_column_size(media) < octet_length(media)
UNION ALL SELECT 'lesson_asset', count(*) FROM lesson_asset WHERE pg_column_size(media) < octet_length(media)
UNION ALL SELECT 'scorm_file', count(*) FROM scorm_file WHERE pg_column_size(media) < octet_length(media)"
