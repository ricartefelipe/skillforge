package skillforge.domain.catalog;

import java.util.Arrays;
import java.util.Objects;

/**
 * Começo de um arquivo enviado, com o tamanho total declarado.
 *
 * <p>O tipo de um arquivo é decidido pelos primeiros bytes e pelo tamanho, nunca pelo conteúdo
 * inteiro: carregar 512 MB em memória para ler quatro bytes de assinatura seria desperdício.
 */
public record MediaProbe(byte[] head, long size) {

    /** Bytes do começo que bastam para todas as assinaturas reconhecidas (a maior olha 200). */
    public static final int HEAD_BYTES = 512;

    public MediaProbe {
        Objects.requireNonNull(head, "head");
        if (size < 0) {
            throw new IllegalArgumentException("size");
        }
        if (head.length > size) {
            throw new IllegalArgumentException("head maior que o arquivo");
        }
        head = head.clone();
    }

    public static MediaProbe of(byte[] content) {
        Objects.requireNonNull(content, "content");
        return new MediaProbe(Arrays.copyOf(content, Math.min(content.length, HEAD_BYTES)), content.length);
    }

    @Override
    public byte[] head() {
        return head.clone();
    }

    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof MediaProbe probe)) {
            return false;
        }
        return size == probe.size && Arrays.equals(head, probe.head);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(head), size);
    }

}
