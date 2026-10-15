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

    public record Unpacked(String launchPath, List<Entry> files) {
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
        String manifest = files.keySet().stream().filter(path -> path.endsWith("imsmanifest.xml")).findFirst()
                .orElseThrow(() -> new Rejection(Failure.NOT_PACKAGE));
        String href = href(files.get(manifest));
        String launch = join(parent(manifest), href);
        if (!files.containsKey(launch)) {
            throw new Rejection(Failure.NOT_PACKAGE);
        }
        List<Entry> stored = new ArrayList<>();
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            stored.add(new Entry(file.getKey(), file.getValue(), type(file.getKey())));
        }
        return new Unpacked(launch, List.copyOf(stored));
    }

    private static String href(byte[] manifest) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(manifest));
            NodeList resources = document.getElementsByTagName("resource");
            for (int index = 0; index < resources.getLength(); index++) {
                String href = ((Element) resources.item(index)).getAttribute("href");
                if (!href.isBlank()) {
                    return href;
                }
            }
        }
        catch (Exception ex) {
            throw new Rejection(Failure.NOT_PACKAGE);
        }
        throw new Rejection(Failure.NOT_PACKAGE);
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
