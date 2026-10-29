package skillforge.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import skillforge.domain.shared.Rejection;

class ScormZipTest {

    @Test
    void readsTheManifestOrder() throws Exception {
        ScormZip.Unpacked unpacked = ScormZip.read(upload(zip(false)), new Collected());

        assertEquals("passo.html", unpacked.launchPath());
        assertEquals(2, unpacked.items().size());
        assertEquals("O primeiro passo", unpacked.items().get(0).title());
        assertEquals("A revisão do passo", unpacked.items().get(1).title());
    }

    @Test
    void refusesAPathThatLeavesThePackage() {
        Rejection rejection = assertThrows(
                Rejection.class,
                () -> ScormZip.read(upload(zip(true)), new Collected()));

        assertEquals("not-package", rejection.failure().wire());
    }

    @Test
    void refusesAPackageThatInflatesBeyondTheBudget() throws Exception {
        Rejection rejection = assertThrows(
                Rejection.class,
                () -> ScormZip.read(upload(bomb()), new Collected(), 1024));

        assertEquals("too-large", rejection.failure().wire());
    }

    @Test
    void handsEachEntryOverAsItIsRead() throws Exception {
        Collected collected = new Collected();

        ScormZip.read(upload(zip(false)), collected);

        assertEquals(
                java.util.List.of("imsmanifest.xml", "passo.html", "revisao.html"),
                java.util.List.copyOf(collected.files.keySet()));
        assertEquals("text/html; charset=utf-8", collected.types.get("passo.html"));
        assertEquals("<html></html>", new String(collected.files.get("passo.html"), StandardCharsets.UTF_8));
    }

    private static MediaUpload upload(byte[] bytes) {
        return new MediaUpload() {

            @Override
            public MediaProbe probe() {
                return MediaProbe.of(bytes);
            }

            @Override
            public InputStream open() {
                return new ByteArrayInputStream(bytes);
            }

        };
    }

    private static final class Collected implements ScormZip.EntryWriter {

        private final Map<String, byte[]> files = new LinkedHashMap<>();

        private final Map<String, String> types = new LinkedHashMap<>();

        @Override
        public void write(String path, String mediaType, byte[] bytes) {
            files.put(path, bytes);
            types.put(path, mediaType);
        }

    }

    private static byte[] bomb() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(buffer);
        zip.putNextEntry(new ZipEntry("big.bin"));
        zip.write(new byte[4096]);
        zip.closeEntry();
        zip.close();
        return buffer.toByteArray();
    }

    private static byte[] zip(boolean escape) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(buffer);
        zip.putNextEntry(new ZipEntry("imsmanifest.xml"));
        String href = escape ? "../secret.html" : "passo.html";
        String manifest = """
                <manifest>
                  <resources>
                    <resource identifier="passo" href="%s"/>
                    <resource identifier="revisao" href="revisao.html"/>
                  </resources>
                  <item identifierref="passo"><title>O primeiro passo</title></item>
                  <item identifierref="revisao"><title>A revisão do passo</title></item>
                </manifest>
                """.formatted(href);
        zip.write(manifest.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.putNextEntry(new ZipEntry(escape ? "secret.html" : "passo.html"));
        zip.write("<html></html>".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.putNextEntry(new ZipEntry("revisao.html"));
        zip.write("<html></html>".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.close();
        return buffer.toByteArray();
    }

}
