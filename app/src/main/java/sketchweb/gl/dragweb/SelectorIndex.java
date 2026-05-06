package sketchweb.gl.dragweb;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks selectors discovered in placed blocks so that <code>%m.id</code>,
 * <code>%m.class</code>, <code>%m.tag</code>, <code>%m.section</code>, and
 * <code>%m.file</code> dropdowns can suggest values that are actually in use.
 *
 * <p>This is a passive observer: it never modifies the project; it scans
 * generated fragments and rendered project state to populate its sets.
 */
public class SelectorIndex {

    private static final Pattern ID_ATTR = Pattern.compile("\\bid\\s*=\\s*'([^']+)'|\\bid\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern CLASS_ATTR = Pattern.compile("\\bclass\\s*=\\s*'([^']+)'|\\bclass\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern TAG_OPEN = Pattern.compile("<([a-zA-Z][a-zA-Z0-9]*)");
    private static final Pattern SECTION_ID = Pattern.compile("<section[^>]*\\bid\\s*=\\s*'([^']+)'|<section[^>]*\\bid\\s*=\\s*\"([^\"]+)\"");

    private final Set<String> ids = new LinkedHashSet<>();
    private final Set<String> classes = new LinkedHashSet<>();
    private final Set<String> tags = new LinkedHashSet<>();
    private final Set<String> sections = new LinkedHashSet<>();
    private final Set<String> files = new LinkedHashSet<>();

    public void clear() {
        ids.clear();
        classes.clear();
        tags.clear();
        sections.clear();
        files.clear();
    }

    public void scanHtml(String html) {
        if (html == null || html.isEmpty()) return;

        Matcher idM = ID_ATTR.matcher(html);
        while (idM.find()) addAll(ids, group(idM));

        Matcher classM = CLASS_ATTR.matcher(html);
        while (classM.find()) {
            String value = group(classM);
            for (String cls : value.split("\\s+")) if (!cls.isEmpty()) classes.add(cls);
        }

        Matcher tagM = TAG_OPEN.matcher(html);
        while (tagM.find()) tags.add(tagM.group(1).toLowerCase());

        Matcher secM = SECTION_ID.matcher(html);
        while (secM.find()) addAll(sections, group(secM));
    }

    public void registerFile(String filename) {
        if (filename != null && !filename.isEmpty()) files.add(filename);
    }

    public Set<String> ids() { return ids; }
    public Set<String> classes() { return classes; }
    public Set<String> tags() { return tags; }
    public Set<String> sections() { return sections; }
    public Set<String> files() { return files; }

    public Set<String> forToken(String token) {
        if (token == null) return new LinkedHashSet<>();
        switch (token) {
            case "%m.id":      return ids;
            case "%m.class":   return classes;
            case "%m.tag":     return tags;
            case "%m.section": return sections;
            case "%m.file":    return files;
            default:           return new LinkedHashSet<>();
        }
    }

    private static void addAll(Set<String> dst, String value) {
        if (value == null) return;
        dst.add(value.trim());
    }

    private static String group(Matcher m) {
        String g = m.group(1);
        return g != null ? g : m.group(2);
    }
}
