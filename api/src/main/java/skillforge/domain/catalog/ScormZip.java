package skillforge.domain.catalog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
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

    private ScormZip() {
    }

    public static Unpacked read(byte[] zip) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream stream = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry entry;
            while ((entry = stream.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String path = normalize(entry.getName());
                if (files.size() > 500) {
                    throw new Rejection(Failure.TOO_LARGE);
                }
                files.put(path, stream.readAllBytes());
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
