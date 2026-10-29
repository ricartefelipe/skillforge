package skillforge.domain.catalog;

import java.io.IOException;
import java.io.InputStream;

/**
 * Arquivo recebido em um upload, lido sob demanda.
 *
 * <p>A fonte entrega o começo do arquivo para a decisão de tipo e abre o conteúdo apenas quando ele
 * vai ser gravado, de modo que o arquivo inteiro nunca precisa existir em memória.
 */
public interface MediaUpload {

    MediaProbe probe();

    /** Abre o conteúdo do começo. Pode ser chamado mais de uma vez. */
    InputStream open() throws IOException;

}
