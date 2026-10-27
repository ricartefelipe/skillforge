package skillforge.domain.catalog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import skillforge.domain.shared.Failure;
import skillforge.domain.shared.Rejection;

public final class ScormZip {

    public record Entry(String path, byte[] bytes, String mediaType) {
    }

    public record Item(String title, String path) {
    }

    public record Unpacked(String launchPath, List<Item> items, List<Entry> files) {
    }

    private static final int MAX_ENTRIES = 500;

    // A hostile package can compress to little yet expand to gigabytes, so the inflated size is bounded too.
    static final long MAX_TOTAL_BYTES = 256L * 1024 * 1024;

    private ScormZip() {
    }

    public static Unpacked read(byte[] zip) {
        return read(zip, MAX_TOTAL_BYTES);
    }

    static Unpacked read(byte[] zip, long maxTotalBytes) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        long budget = maxTotalBytes;
        try (ZipInputStream stream = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String path = normalize(entry.getName());
                if (files.size() >= MAX_ENTRIES) {
                    throw new Rejection(Failure.TOO_LARGE);
                }
                byte[] bytes = readCapped(stream, budget);
                budget -= bytes.length;
                files.put(path, bytes);
            }
        }
        catch (IOException ex) {
            throw new Rejection(Failure.NOT_PACKAGE);
        }
        String manifestPath = files.keySet().stream().filter(path -> path.endsWith("imsmanifest.xml")).findFirst()
                .orElseThrow(() -> new Rejection(Failure.NOT_PACKAGE));
        Outline outline = outline(files.get(manifestPath), parent(manifestPath));
        if (!files.containsKey(outline.launch())) {
            throw new Rejection(Failure.NOT_PACKAGE);
        }
        List<Entry> stored = new ArrayList<>();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            stored.add(new Entry(file.getKey(), file.getValue(), type(file.getKey())));
        }
        List<Item> items = outline.items().isEmpty()
                ? List.of(new Item("Aula", outline.launch()))
                : outline.items();
        return new Unpacked(outline.launch(), items, List.copyOf(stored));
    }

    private record Outline(String launch, List<Item> items) {
    }

    private static Outline outline(byte[] manifest, String base) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(manifest));
            Map<String, String> resources = new LinkedHashMap<>();
            NodeList resourceNodes = document.getElementsByTagName("resource");
            String first = null;
            for (int index = 0; index < resourceNodes.getLength(); index++) {
                Element resource = (Element) resourceNodes.item(index);
                String href = resource.getAttribute("href");
                if (href.isBlank()) {
                    continue;
                }
                String path = join(base, href);
                resources.put(resource.getAttribute("identifier"), path);
                if (first == null) {
                    first = path;
                }
            }
            if (first == null) {
                throw new Rejection(Failure.NOT_PACKAGE);
            }
            List<Item> items = new ArrayList<>();
            NodeList itemNodes = document.getElementsByTagName("item");
            for (int index = 0; index < itemNodes.getLength(); index++) {
                Element item = (Element) itemNodes.item(index);
                String path = resources.get(item.getAttribute("identifierref"));
                if (path == null) {
                    continue;
                }
                String title = "Item";
                NodeList titles = item.getElementsByTagName("title");
                if (titles.getLength() > 0 && titles.item(0).getTextContent() != null) {
                    String text = titles.item(0).getTextContent().trim();
                    if (!text.isBlank()) {
                        title = text;
                    }
                }
                items.add(new Item(title, path));
            }
            return new Outline(items.isEmpty() ? first : items.get(0).path(), items);
        }
        catch (Exception ex) {
            throw new Rejection(Failure.NOT_PACKAGE);
        }
    }

    // Reads one entry without trusting its declared size, refusing the package once the budget is spent.
    private static byte[] readCapped(InputStream stream, long budget) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long left = budget;
        while (true) {
            int want = (int) Math.min(chunk.length, left + 1);
            int read = stream.read(chunk, 0, want);
            if (read == -1) {
                return out.toByteArray();
            }
            if (read > left) {
                throw new Rejection(Failure.TOO_LARGE);
            }
            out.write(chunk, 0, read);
            left -= read;
        }
    }

    public static String normalize(String raw) {
        String path = raw.replace('\\', '/');
        if (path.startsWith("/") || path.contains("..")) {
            throw new Rejection(Failure.NOT_PACKAGE);
        }
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        if (path.isBlank()) {
            throw new Rejection(Failure.NOT_PACKAGE);
        }
        return path;
    }

    private static String parent(String path) {
        int slash = path.lastIndexOf('/');
        if (slash < 0) {
            return "";
        }
        return path.substring(0, slash + 1);
    }

    private static String join(String base, String href) {
        String relative = href.split("\\?")[0].split("#")[0];
        return normalize(base + relative);
    }

    private static String type(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html; charset=utf-8";
        }
        if (lower.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (lower.endsWith(".xml")) {
            return "application/xml";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

}
