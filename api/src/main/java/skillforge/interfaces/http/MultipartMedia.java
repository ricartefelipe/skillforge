package skillforge.interfaces.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import org.springframework.web.multipart.MultipartFile;

import skillforge.domain.catalog.MediaProbe;
import skillforge.domain.catalog.MediaUpload;

/**
 * Upload multipart visto como fonte de bytes.
 *
 * <p>O contêiner já derrama o arquivo em disco durante a análise; esta classe lê dele apenas o
 * começo, para decidir o tipo, e reabre o conteúdo na hora de gravar.
 */
final class MultipartMedia implements MediaUpload {

    private final MultipartFile file;

    private final MediaProbe probe;

    private MultipartMedia(MultipartFile file, MediaProbe probe) {
        this.file = Objects.requireNonNull(file, "file");
        this.probe = Objects.requireNonNull(probe, "probe");
    }

    /** Devolve {@code null} quando não veio arquivo, que é como a decisão de conteúdo lê a ausência. */
    static MediaUpload of(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try (InputStream stream = file.getInputStream()) {
            return new MultipartMedia(file, new MediaProbe(stream.readNBytes(MediaProbe.HEAD_BYTES), file.getSize()));
        }
    }

    @Override
    public MediaProbe probe() {
        return probe;
    }

    @Override
    public InputStream open() throws IOException {
        return file.getInputStream();
    }

}
