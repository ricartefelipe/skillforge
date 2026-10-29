package skillforge.infrastructure.persistence;

import java.io.InputStream;
import java.util.Objects;

import skillforge.domain.catalog.LessonKind;
import skillforge.domain.catalog.MediaStream;

/**
 * Mídia guardada em coluna {@code bytea}, entregue em pedaços.
 *
 * <p>Cada pedaço é buscado por uma consulta que recorta a faixa no banco, de modo que o arquivo
 * inteiro nunca é trazido para a memória da aplicação nem quando o cliente baixa tudo.
 */
final class SlicedMedia implements MediaStream {

    /** Tamanho do pedaço lido por consulta, e portanto o pico de memória de um download. */
    static final int CHUNK_BYTES = 256 * 1024;

    /** Recorta {@code length} bytes a partir de {@code offset}, contado de zero. */
    @FunctionalInterface
    interface Slice {

        byte[] read(long offset, int length);

    }

    private final LessonKind kind;

    private final String mediaType;

    private final long size;

    private final Slice slice;

    SlicedMedia(LessonKind kind, String mediaType, long size, Slice slice) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.mediaType = Objects.requireNonNull(mediaType, "mediaType");
        this.slice = Objects.requireNonNull(slice, "slice");
        if (size < 0) {
            throw new IllegalArgumentException("size");
        }
        this.size = size;
    }

    @Override
    public LessonKind kind() {
        return kind;
    }

    @Override
    public String mediaType() {
        return mediaType;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public InputStream open(long offset, long length) {
        if (offset < 0 || length < 0) {
            throw new IllegalArgumentException("faixa negativa");
        }
        return new SliceStream(slice, offset, Math.min(length, Math.max(0, size - offset)));
    }

    /** Lê a faixa pedida em consultas de {@link #CHUNK_BYTES}, uma por vez. */
    private static final class SliceStream extends InputStream {

        private final Slice slice;

        private long at;

        private long left;

        private byte[] chunk = new byte[0];

        private int next;

        private SliceStream(Slice slice, long offset, long length) {
            this.slice = slice;
            this.at = offset;
            this.left = length;
        }

        @Override
        public int read() {
            if (!fill()) {
                return -1;
            }
            return chunk[next++] & 0xff;
        }

        @Override
        public int read(byte[] out, int off, int len) {
            Objects.checkFromIndexSize(off, len, out.length);
            if (len == 0) {
                return 0;
            }
            if (!fill()) {
                return -1;
            }
            int count = Math.min(len, chunk.length - next);
            System.arraycopy(chunk, next, out, off, count);
            next += count;
            return count;
        }

        @Override
        public int available() {
            return (int) Math.min(Integer.MAX_VALUE, (long) chunk.length - next + left);
        }

        private boolean fill() {
            while (next >= chunk.length) {
                if (left <= 0) {
                    return false;
                }
                byte[] read = slice.read(at, (int) Math.min(CHUNK_BYTES, left));
                if (read == null || read.length == 0) {
                    left = 0;
                    return false;
                }
                chunk = read;
                next = 0;
                at += read.length;
                left -= read.length;
            }
            return true;
        }

    }

}
