package com.jxparallel.ui.text;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The fonts text can ask for by family name: fonts an application registers (from a file, a stream or the
 * classpath) and the fonts installed on the system, found by scanning the system font folders and reading
 * each file's own name table (no font is loaded to be listed). A request ({@link JXFont}) resolves to the
 * nearest face of its family: the italic flag matches first, then the weight closest to the one asked for,
 * as far as the family has such faces. Bold and italic are never synthesized: a renderer cannot embolden
 * or slant a face the same way as the measurement, so the nearest real face is used (a family without
 * an italic face draws upright).
 *
 * <p>Registered fonts win over system fonts of the same family name, and are the first candidates when a
 * glyph is missing ({@link JXTextEngine} falls back to a face that has it). Names compare ignoring case.
 * Thread safe; the system scan runs once, on first use, and is cached.
 */
public final class JXFonts {
    /** One face of a font file. */
    public static final class Face {
        private final String family;
        private final String legacyFamily;
        private final String style;
        private final int weight;
        private final boolean italic;
        private final String file;
        private final int index;
        private final boolean registered;

        Face(String family, String legacyFamily, String style, int weight, boolean italic, String file, int index,
             boolean registered) {
            this.family = family;
            this.legacyFamily = legacyFamily;
            this.style = style;
            this.weight = weight;
            this.italic = italic;
            this.file = file;
            this.index = index;
            this.registered = registered;
        }

        /** The family name this face is found under. */
        public String family() {
            return family;
        }

        /** The style name the file gives ("Bold Italic", "Semibold"...), possibly empty. */
        public String style() {
            return style;
        }

        /** Numeric weight the file declares (OS/2 usWeightClass), 400 when it declares none. */
        public int weight() {
            return weight;
        }

        public boolean italic() {
            return italic;
        }

        /** Path of the font file. */
        public String file() {
            return file;
        }

        /** Index of the face in a collection (.ttc), 0 for a single font. */
        public int index() {
            return index;
        }

        /** True for a face an application registered, false for a system font. */
        public boolean registered() {
            return registered;
        }

        boolean inFamily(String lower) {
            return family.toLowerCase(Locale.ROOT).equals(lower) || legacyFamily.toLowerCase(Locale.ROOT).equals(lower);
        }

        @Override
        public String toString() {
            return family + " " + weight + (italic ? " italic" : "") + " (" + file + (index > 0 ? "#" + index : "") + ")";
        }
    }

    private static final List<Face> REGISTERED = new CopyOnWriteArrayList<Face>();
    /** The UI font files, so the UI font's own family resolves even when it is not in a system folder. */
    private static final List<Face> UI = new CopyOnWriteArrayList<Face>();
    private static volatile List<Face> system;
    private static volatile String uiFamily;
    private static volatile boolean uiFamilyKnown;

    private JXFonts() {
    }

    // ---- registering ---------------------------------------------------------------------------------

    /**
     * Registers every face of a font file under {@code family} (the name text asks for), with the weight and
     * italic the file declares. A collection (.ttc) registers all its faces. Returns how many faces.
     *
     * @throws IllegalArgumentException if the file is not a TrueType or OpenType font
     */
    public static int register(String family, File file) {
        return register(family, file, 0, false, false);
    }

    /** Like {@link #register(String, File)} but with the given weight and italic instead of the file's own. */
    public static int register(String family, File file, int weight, boolean italic) {
        return register(family, file, weight, italic, true);
    }

    /**
     * Registers a font read from a stream (the stream is read to its end, not closed). The bytes are kept in
     * a temporary file, because the renderers load fonts by path.
     */
    public static int register(String family, InputStream in) throws IOException {
        return register(family, copy(in), 0, false, false);
    }

    /** Registers a font from a classpath resource, e.g. {@code "/fonts/Inter-Regular.ttf"}. */
    public static int registerResource(String family, String resource) throws IOException {
        String name = resource.startsWith("/") ? resource.substring(1) : resource;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        InputStream in = loader == null ? null : loader.getResourceAsStream(name);
        if (in == null) {
            in = JXFonts.class.getClassLoader() == null ? null : JXFonts.class.getClassLoader().getResourceAsStream(name);
        }
        if (in == null) {
            throw new IOException("font resource not found: " + resource);
        }
        try {
            return register(family, in);
        } finally {
            in.close();
        }
    }

    private static int register(String family, File file, int weight, boolean italic, boolean override) {
        if (family == null || family.trim().isEmpty()) {
            throw new IllegalArgumentException("A font family name is required");
        }
        List<JXFontFile.Info> infos = JXFontFile.read(file);
        if (infos.isEmpty()) {
            throw new IllegalArgumentException("Not a TrueType or OpenType font: " + file);
        }
        String name = family.trim();
        String path = file.getAbsolutePath();
        for (JXFontFile.Info info : infos) {
            Face face = new Face(name, name, info.style, override ? Math.max(1, Math.min(1000, weight)) : info.weight,
                    override ? italic : info.italic, path, info.index, true);
            for (Face old : REGISTERED) {
                if (old.file.equals(path) && old.index == face.index && old.family.equalsIgnoreCase(name)) {
                    REGISTERED.remove(old);
                }
            }
            REGISTERED.add(face);
        }
        JXTextEngine.fontsChanged();
        return infos.size();
    }

    private static File copy(InputStream in) throws IOException {
        File temp = File.createTempFile("jxfont", ".ttf");
        temp.deleteOnExit();
        OutputStream out = new java.io.FileOutputStream(temp);
        try {
            byte[] buffer = new byte[16384];
            for (int n = in.read(buffer); n >= 0; n = in.read(buffer)) {
                out.write(buffer, 0, n);
            }
        } finally {
            out.close();
        }
        return temp;
    }

    /** Forgets every registered font (the system fonts stay). Mostly for tests and for reloading an application. */
    public static void clearRegistered() {
        REGISTERED.clear();
        JXTextEngine.fontsChanged();
    }

    /** Scans the system font folders again on the next use, e.g. after a font was installed. */
    public static void refresh() {
        system = null;
        JXTextEngine.fontsChanged();
    }

    // ---- looking up ----------------------------------------------------------------------------------

    /** All family names known, registered and system, sorted. */
    public static List<String> families() {
        TreeSet<String> names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        for (Face face : REGISTERED) {
            names.add(face.family);
        }
        for (Face face : systemFaces()) {
            names.add(face.family);
        }
        return Collections.unmodifiableList(new ArrayList<String>(names));
    }

    /** The faces of a family (registered ones if the application registered that name, else the system's). */
    public static List<Face> faces(String family) {
        return facesOf(family == null ? "" : family.trim().toLowerCase(Locale.ROOT));
    }

    /** True if the family is registered or installed. */
    public static boolean hasFamily(String family) {
        return !faces(family).isEmpty();
    }

    /**
     * The face for a request: its family's nearest face by italic, then weight. For the UI font ({@code null}
     * family) the face comes from the UI font's own family, so a weight of 600 on the UI font picks "Semibold"
     * where the system has one. {@code null} if the family is unknown or has no face (the caller then uses the
     * UI font).
     */
    public static Face resolve(JXFont font) {
        String name = font.family();
        if (name == null) {
            name = uiFamily();
            if (name == null) {
                return null;
            }
        }
        String lower = name.toLowerCase(Locale.ROOT);
        List<Face> candidates = facesOf(lower);
        if (candidates.isEmpty()) {
            String[] alias = ALIASES.get(lower);
            if (alias != null) {
                for (String a : alias) {
                    candidates = facesOf(a.toLowerCase(Locale.ROOT));
                    if (!candidates.isEmpty()) {
                        break;
                    }
                }
            }
        }
        return nearest(candidates, font.weight(), font.italic());
    }

    private static final Map<String, String[]> ALIASES = new HashMap<String, String[]>();

    static {
        ALIASES.put("sans-serif", new String[] {"Segoe UI", "Helvetica Neue", "DejaVu Sans", "Liberation Sans", "Arial"});
        ALIASES.put("serif", new String[] {"Times New Roman", "Georgia", "DejaVu Serif", "Liberation Serif", "Noto Serif"});
        ALIASES.put("monospace", new String[] {"Consolas", "Menlo", "DejaVu Sans Mono", "Liberation Mono", "Courier New"});
        ALIASES.put("system-ui", ALIASES.get("sans-serif"));
    }

    private static List<Face> facesOf(String lower) {
        List<Face> out = new ArrayList<Face>();
        for (Face face : REGISTERED) {
            if (face.inFamily(lower)) {
                out.add(face);
            }
        }
        if (!out.isEmpty()) {
            return out; // an application's font replaces the system's of that name
        }
        for (Face face : UI) {
            if (face.inFamily(lower)) {
                out.add(face);
            }
        }
        for (Face face : systemFaces()) {
            if (face.inFamily(lower) && !contains(out, face)) {
                out.add(face);
            }
        }
        return out;
    }

    private static boolean contains(List<Face> faces, Face face) {
        for (Face f : faces) {
            if (f.file.equals(face.file) && f.index == face.index) {
                return true;
            }
        }
        return false;
    }

    /** The face of {@code candidates} closest to the request: italic first, then weight (CSS font matching, simplified). */
    static Face nearest(List<Face> candidates, int weight, boolean italic) {
        Face best = null;
        int bestScore = Integer.MAX_VALUE;
        for (Face face : candidates) {
            int score = (face.italic == italic ? 0 : 100000) + Math.abs(face.weight - weight) * 2;
            if (weight < 400 && face.weight > weight || weight > 500 && face.weight < weight) {
                score++; // away from the asked weight on the wrong side: lighter below 400, heavier above 500
            }
            if (score < bestScore) {
                bestScore = score;
                best = face;
            }
        }
        return best;
    }

    /** The face of {@code face}'s family nearest to this weight and italic (the face itself if it has no siblings). */
    static Face sibling(Face face, int weight, boolean italic) {
        Face best = nearest(facesOf(face.family.toLowerCase(Locale.ROOT)), weight, italic);
        return best == null ? face : best;
    }

    // ---- the UI font ---------------------------------------------------------------------------------

    /** Family name of the UI font (from its file's name table), or {@code null} without a UI font. */
    public static String uiFamily() {
        if (!uiFamilyKnown) {
            synchronized (JXFonts.class) {
                if (!uiFamilyKnown) {
                    String regular = JXTextEngine.findFontFile();
                    String bold = JXTextEngine.findBoldFontFile();
                    String name = null;
                    for (String path : new String[] {regular, bold}) {
                        if (path == null) {
                            continue;
                        }
                        for (JXFontFile.Info info : JXFontFile.read(new File(path))) {
                            if (name == null) {
                                name = info.family;
                            }
                            if (info.family.equalsIgnoreCase(name)) {
                                UI.add(new Face(info.family, info.legacyFamily, info.style, info.weight, info.italic, path,
                                        info.index, false));
                            }
                        }
                    }
                    uiFamily = name;
                    uiFamilyKnown = true;
                }
            }
        }
        return uiFamily;
    }

    // ---- system fonts --------------------------------------------------------------------------------

    private static List<Face> systemFaces() {
        List<Face> faces = system;
        if (faces == null) {
            synchronized (JXFonts.class) {
                faces = system;
                if (faces == null) {
                    faces = scan();
                    system = faces;
                }
            }
        }
        return faces;
    }

    private static List<Face> scan() {
        List<File> dirs = new ArrayList<File>();
        String windir = System.getenv("WINDIR");
        dirs.add(new File(windir == null ? "C:\\Windows" : windir, "Fonts"));
        String local = System.getenv("LOCALAPPDATA");
        if (local != null) {
            dirs.add(new File(local, "Microsoft\\Windows\\Fonts"));
        }
        String home = System.getProperty("user.home");
        for (String dir : new String[] {"/usr/share/fonts", "/usr/local/share/fonts", "/System/Library/Fonts", "/Library/Fonts"}) {
            dirs.add(new File(dir));
        }
        if (home != null) {
            dirs.add(new File(home, ".fonts"));
            dirs.add(new File(home, ".local/share/fonts"));
            dirs.add(new File(home, "Library/Fonts"));
        }
        String extra = System.getProperty("jx.font.dirs");
        if (extra != null) {
            for (String dir : extra.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                if (!dir.isEmpty()) {
                    dirs.add(new File(dir));
                }
            }
        }
        List<Face> out = new ArrayList<Face>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (File dir : dirs) {
            walk(dir, 0, out, seen);
        }
        return Collections.unmodifiableList(out);
    }

    private static void walk(File dir, int depth, List<Face> out, java.util.Set<String> seen) {
        File[] entries = dir.listFiles();
        if (entries == null || depth > 5) {
            return;
        }
        Arrays.sort(entries);
        for (File entry : entries) {
            if (entry.isDirectory()) {
                walk(entry, depth + 1, out, seen);
                continue;
            }
            String lower = entry.getName().toLowerCase(Locale.ROOT);
            if (!(lower.endsWith(".ttf") || lower.endsWith(".otf") || lower.endsWith(".ttc") || lower.endsWith(".otc"))) {
                continue;
            }
            String path = entry.getAbsolutePath();
            if (!seen.add(path.toLowerCase(Locale.ROOT))) {
                continue;
            }
            for (JXFontFile.Info info : JXFontFile.read(entry)) {
                out.add(new Face(info.family, info.legacyFamily, info.style, info.weight, info.italic, path, info.index, false));
            }
        }
    }

    // ---- glyph fallback ------------------------------------------------------------------------------

    private static final String[] EMOJI_FIRST = {"Segoe UI Emoji", "Apple Color Emoji", "Noto Color Emoji", "Noto Emoji",
            "Twemoji Mozilla", "Segoe UI Symbol", "Symbola", "Noto Sans Symbols2"};
    private static final String[] CJK_FIRST = {"Microsoft YaHei", "Microsoft JhengHei", "Yu Gothic UI", "Yu Gothic", "Malgun Gothic",
            "Meiryo", "MS Gothic", "SimSun", "PingFang SC", "Hiragino Sans", "Apple SD Gothic Neo", "Noto Sans CJK SC",
            "Noto Sans CJK JP", "Noto Sans CJK KR", "Noto Sans CJK TC", "Source Han Sans", "WenQuanYi Micro Hei",
            "WenQuanYi Zen Hei", "Droid Sans Fallback"};
    private static final String[] GENERAL = {"Segoe UI", "Segoe UI Symbol", "Segoe UI Historic", "Nirmala UI", "Leelawadee UI",
            "Gadugi", "Ebrima", "Myanmar Text", "Mongolian Baiti", "Microsoft Himalaya", "Microsoft New Tai Lue",
            "Microsoft Tai Le", "Microsoft Yi Baiti", "Javanese Text", "Sylfaen", "Arial", "Times New Roman", "Tahoma",
            "Calibri", "Cambria Math", "Arial Unicode MS", "Geeza Pro", "Arial Hebrew", "Lucida Grande", "Apple Symbols",
            "Noto Sans", "Noto Sans Arabic", "Noto Naskh Arabic", "Noto Sans Hebrew", "Noto Sans Symbols", "Noto Sans Math",
            "DejaVu Sans", "Liberation Sans", "FreeSans", "Unifont"};

    /** Fallback ranking classes: which families to try first. */
    private static final int OTHER = 0;
    private static final int EMOJI = 1;
    private static final int CJK = 2;
    private static final List<Face>[] FALLBACKS = newLists();
    private static volatile int fallbackGeneration = -1;
    private static volatile int generation;

    @SuppressWarnings("unchecked")
    private static List<Face>[] newLists() {
        return (List<Face>[]) new List[3];
    }

    static int classOf(int cp) {
        if (cp >= 0x1F000 && cp <= 0x1FAFF || cp >= 0x2600 && cp <= 0x27BF || cp >= 0x2300 && cp <= 0x23FF
                || cp >= 0x2B00 && cp <= 0x2BFF || cp >= 0x1F1E6 && cp <= 0x1F1FF) {
            return EMOJI;
        }
        if (cp >= 0x2E80 && cp <= 0x9FFF || cp >= 0xAC00 && cp <= 0xD7AF || cp >= 0xF900 && cp <= 0xFAFF
                || cp >= 0xFF00 && cp <= 0xFFEF || cp >= 0x20000 && cp <= 0x3FFFF || cp >= 0x1100 && cp <= 0x11FF) {
            return CJK;
        }
        return OTHER;
    }

    /**
     * The faces to try, in order, for a code point the chosen font lacks: registered fonts, then well-known
     * system families for that kind of character, then one face of every other family.
     */
    static List<Face> fallbacks(int cp) {
        int kind = classOf(cp);
        List<Face> list = FALLBACKS[kind];
        if (list == null || fallbackGeneration != generation) {
            synchronized (JXFonts.class) {
                if (fallbackGeneration != generation) {
                    FALLBACKS[0] = FALLBACKS[1] = FALLBACKS[2] = null;
                    fallbackGeneration = generation;
                }
                list = FALLBACKS[kind];
                if (list == null) {
                    list = buildFallbacks(kind);
                    FALLBACKS[kind] = list;
                }
            }
        }
        return list;
    }

    static void invalidateFallbacks() {
        synchronized (JXFonts.class) {
            generation++;
        }
    }

    private static List<Face> buildFallbacks(int kind) {
        LinkedHashMap<String, Face> picked = new LinkedHashMap<String, Face>();
        for (Face face : REGISTERED) {
            picked.put(face.file + "#" + face.index, face);
        }
        String[][] order = kind == EMOJI ? new String[][] {EMOJI_FIRST, GENERAL, CJK_FIRST}
                : kind == CJK ? new String[][] {CJK_FIRST, GENERAL, EMOJI_FIRST} : new String[][] {GENERAL, CJK_FIRST, EMOJI_FIRST};
        for (String[] names : order) {
            for (String name : names) {
                Face face = nearest(facesOf(name.toLowerCase(Locale.ROOT)), 400, false);
                if (face != null) {
                    picked.put(face.file + "#" + face.index, face);
                }
            }
        }
        Map<String, Boolean> families = new HashMap<String, Boolean>();
        for (Face face : picked.values()) {
            families.put(face.family.toLowerCase(Locale.ROOT), Boolean.TRUE);
        }
        for (Face face : systemFaces()) {
            String key = face.family.toLowerCase(Locale.ROOT);
            if (!families.containsKey(key) && face.weight == 400 && !face.italic) {
                families.put(key, Boolean.TRUE);
                picked.put(face.file + "#" + face.index, face);
            }
        }
        return Collections.unmodifiableList(new ArrayList<Face>(picked.values()));
    }
}
