package skillforge.domain.catalog;

import java.io.InputStream;

/**
 * Mídia já armazenada, aberta por faixa de bytes.
 *
 * <p>A faixa é buscada em pedaços conforme o stream é lido, de modo que o pico de memória de um
 * download acompanhe o pedaço, e não o tamanho do arquivo.
 */
public interface MediaStream {

    LessonKind kind();

    String mediaType();

    long size();

    /** Abre os {@code length} bytes a partir de {@code offset}, contado de zero. */
    InputStream open(long offset, long length);

}
