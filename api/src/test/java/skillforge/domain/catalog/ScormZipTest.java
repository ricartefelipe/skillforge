package skillforge.domain.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import skillforge.domain.shared.Rejection;

class ScormZipTest {

    @Test
    void readsTheManifestOrder() throws Exception {
        ScormZip.Unpacked unpacked = ScormZip.read(zip(false));

        assertEquals("passo.html", unpacked.launchPath());
        assertEquals(2, unpacked.items().size());
        assertEquals("O primeiro passo", unpacked.items().get(0).title());
        assertEquals("A revisão do passo", unpacked.items().get(1).title());
    }

    @Test
    void refusesAPathThatLeavesThePackage() {
        Rejection rejection = assertThrows(Rejection.class, () -> ScormZip.read(zip(true)));

        assertEquals("not-package", rejection.failure().wire());
    }

    @Test
    void refusesAPackageThatInflatesBeyondTheBudget() throws Exception {
        Rejection rejection = assertThrows(Rejection.class, () -> ScormZip.read(bomb(), 1024));

        assertEquals("too-large", rejection.failure().wire());
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
