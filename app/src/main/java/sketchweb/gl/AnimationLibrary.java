package sketchweb.gl;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central registry of CSS keyframe animations referenced by animation blocks.
 *
 * <p>Both {@link ExportManager} and {@link PageCodeGenerator} call into here
 * so the {@code @keyframes} block they emit is always consistent with the
 * names produced by {@code blocks.json} entries (fadeIn, slideIn, bounce, …).
 *
 * <p>Adding a new animation is two changes: register a keyframe rule via
 * {@link #addKeyframe(String, String)} below, and add a matching entry to
 * {@code blocks.json}. Generated CSS gets the new keyframe automatically.
 */
public final class AnimationLibrary {

    private static final Map<String, String> KEYFRAMES = new LinkedHashMap<>();
    private static final Map<String, String> EASING_PRESETS = new LinkedHashMap<>();

    static {
        // Fade
        addKeyframe("fadeIn",
            "from { opacity: 0; } to { opacity: 1; }");
        addKeyframe("fadeOut",
            "from { opacity: 1; } to { opacity: 0; }");

        // Slide
        addKeyframe("slideIn",
            "from { transform: translateY(20px); opacity: 0; } "
                + "to { transform: translateY(0); opacity: 1; }");
        addKeyframe("slideOut",
            "from { transform: translateY(0); opacity: 1; } "
                + "to { transform: translateY(20px); opacity: 0; }");
        addKeyframe("slideUp",
            "from { transform: translateY(20px); opacity: 0; } "
                + "to { transform: translateY(0); opacity: 1; }");
        addKeyframe("slideDown",
            "from { transform: translateY(-20px); opacity: 0; } "
                + "to { transform: translateY(0); opacity: 1; }");
        addKeyframe("slideLeft",
            "from { transform: translateX(20px); opacity: 0; } "
                + "to { transform: translateX(0); opacity: 1; }");
        addKeyframe("slideRight",
            "from { transform: translateX(-20px); opacity: 0; } "
                + "to { transform: translateX(0); opacity: 1; }");

        // Zoom / scale
        addKeyframe("zoomIn",
            "from { transform: scale(0.85); opacity: 0; } "
                + "to { transform: scale(1); opacity: 1; }");
        addKeyframe("zoomOut",
            "from { transform: scale(1); opacity: 1; } "
                + "to { transform: scale(0.85); opacity: 0; }");

        // Attention
        addKeyframe("bounce",
            "0%, 20%, 50%, 80%, 100% { transform: translateY(0); } "
                + "40% { transform: translateY(-12px); } "
                + "60% { transform: translateY(-6px); }");
        addKeyframe("pulse",
            "0%, 100% { transform: scale(1); } 50% { transform: scale(1.05); }");
        addKeyframe("rotate",
            "from { transform: rotate(0deg); } to { transform: rotate(360deg); }");
        addKeyframe("shake",
            "0%, 100% { transform: translateX(0); } "
                + "20%, 60% { transform: translateX(-6px); } "
                + "40%, 80% { transform: translateX(6px); }");
        addKeyframe("flip",
            "from { transform: perspective(400px) rotateY(0); } "
                + "to { transform: perspective(400px) rotateY(360deg); }");

        // Easing presets used by the transition editor / animation block
        EASING_PRESETS.put("linear",      "cubic-bezier(0, 0, 1, 1)");
        EASING_PRESETS.put("ease",        "cubic-bezier(0.25, 0.1, 0.25, 1)");
        EASING_PRESETS.put("ease-in",     "cubic-bezier(0.42, 0, 1, 1)");
        EASING_PRESETS.put("ease-out",    "cubic-bezier(0, 0, 0.58, 1)");
        EASING_PRESETS.put("ease-in-out", "cubic-bezier(0.42, 0, 0.58, 1)");
        EASING_PRESETS.put("spring",      "cubic-bezier(0.34, 1.56, 0.64, 1)");
        EASING_PRESETS.put("bounce-out",  "cubic-bezier(0.68, -0.55, 0.27, 1.55)");
    }

    private AnimationLibrary() {}

    public static void addKeyframe(String name, String body) {
        if (name != null && !name.isEmpty() && body != null) {
            KEYFRAMES.put(name, body);
        }
    }

    /** Render every registered keyframe block for embedding into a stylesheet. */
    public static String generateKeyframesCss() {
        return generateKeyframesCss("    ");
    }

    public static String generateKeyframesCss(String indent) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : KEYFRAMES.entrySet()) {
            sb.append(indent).append("@keyframes ").append(entry.getKey()).append(" { ")
              .append(entry.getValue()).append(" }\n");
        }
        return sb.toString();
    }

    public static java.util.Set<String> getAllAnimations() {
        return KEYFRAMES.keySet();
    }

    public static String getKeyframeBody(String name) {
        return KEYFRAMES.get(name);
    }

    public static Map<String, String> easingPresets() {
        return EASING_PRESETS;
    }

    public static String resolveEasing(String name) {
        if (name == null) return "ease";
        String preset = EASING_PRESETS.get(name);
        return preset != null ? preset : name;
    }
}
