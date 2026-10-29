package skillforge.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import skillforge.domain.catalog.LessonKind;

class SlicedMediaTest {

    private static final long HUGE = 512L * 1024 * 1024;

    /** Registra cada recorte pedido ao armazenamento, para medir o que foi realmente lido. */
    private static final class Reads implements SlicedMedia.Slice {

        private final List<String> asked = new ArrayList<>();

        private long bytesRead;

        @Override
        public byte[] read(long offset, int length) {
            asked.add(offset + "+" + length);
            bytesRead += length;
            byte[] chunk = new byte[length];
            Arrays.fill(chunk, (byte) 'x');
            return chunk;
        }

    }

    @Test
    void readingTheStartOfAHugeFileTouchesOneChunkOnly() throws IOException {
        Reads reads = new Reads();
        SlicedMedia media = new SlicedMedia(LessonKind.VIDEO, "video/mp4", HUGE, reads);

        try (InputStream stream = media.open(0, HUGE)) {
            assertEquals(100, stream.readNBytes(100).length);
        }

        assertEquals(List.of("0+" + SlicedMedia.CHUNK_BYTES), reads.asked);
        assertTrue(reads.bytesRead < HUGE / 1000, "leu " + reads.bytesRead + " bytes de um arquivo de " + HUGE);
    }

    @Test
    void aRangeAsksTheStorageOnlyForThatRange() throws IOException {
        Reads reads = new Reads();
        SlicedMedia media = new SlicedMedia(LessonKind.VIDEO, "video/mp4", HUGE, reads);

        try (InputStream stream = media.open(1_000_000, 10)) {
            assertEquals(10, stream.readAllBytes().length);
        }

        assertEquals(List.of("1000000+10"), reads.asked);
    }

    @Test
    void theWholeFileIsReadInChunksAndNeverAtOnce() throws IOException {
        long size = 3L * SlicedMedia.CHUNK_BYTES + 7;
        Reads reads = new Reads();
        SlicedMedia media = new SlicedMedia(LessonKind.VIDEO, "video/mp4", size, reads);

        long copied;
        try (InputStream stream = media.open(0, size)) {
            copied = stream.transferTo(java.io.OutputStream.nullOutputStream());
        }

        assertEquals(size, copied);
        assertEquals(4, reads.asked.size());
        assertEquals("0+" + SlicedMedia.CHUNK_BYTES, reads.asked.get(0));
        assertEquals((3L * SlicedMedia.CHUNK_BYTES) + "+7", reads.asked.get(3));
    }

    @Test
    void aRangeIsClippedToTheEndOfTheFile() throws IOException {
        Reads reads = new Reads();
        SlicedMedia media = new SlicedMedia(LessonKind.PDF, "application/pdf", 10, reads);

        try (InputStream stream = media.open(8, 1000)) {
            assertEquals(2, stream.readAllBytes().length);
        }

        assertEquals(List.of("8+2"), reads.asked);
    }

    @Test
    void readingPastTheEndGivesNothing() throws IOException {
        Reads reads = new Reads();
        SlicedMedia media = new SlicedMedia(LessonKind.PDF, "application/pdf", 10, reads);

        try (InputStream stream = media.open(10, 5)) {
            assertEquals(-1, stream.read());
        }

        assertEquals(List.of(), reads.asked);
    }

    @Test
    void storageThatRunsShortStopsTheStreamInsteadOfLooping() throws IOException {
        SlicedMedia media = new SlicedMedia(LessonKind.PDF, "application/pdf", 1000, (offset, length) -> new byte[0]);

        try (InputStream stream = media.open(0, 1000)) {
            assertArrayEquals(new byte[0], stream.readAllBytes());
        }
    }

    @Test
    void aNegativeRangeIsRefused() {
        SlicedMedia media = new SlicedMedia(LessonKind.PDF, "application/pdf", 10, (offset, length) -> new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> media.open(-1, 5));
        assertThrows(IllegalArgumentException.class, () -> media.open(0, -5));
    }

}
