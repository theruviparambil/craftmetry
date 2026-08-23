package io.github.theruviparambil.craftmetry;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

record HudPreferences(boolean visible, Corner corner, Scale scale) {
    HudPreferences {
        Objects.requireNonNull(corner, "corner");
        Objects.requireNonNull(scale, "scale");
    }

    static HudPreferences defaults() {
        return new HudPreferences(true, Corner.TOP_LEFT, Scale.NORMAL);
    }

    HudPreferences toggleVisibility() {
        return new HudPreferences(!visible, corner, scale);
    }

    HudPreferences cycleCorner() {
        return new HudPreferences(visible, corner.next(), scale);
    }

    HudPreferences cycleScale() {
        return new HudPreferences(visible, corner, scale.next());
    }

    enum Corner {
        TOP_LEFT("top_left", false, false),
        TOP_RIGHT("top_right", true, false),
        BOTTOM_RIGHT("bottom_right", true, true),
        BOTTOM_LEFT("bottom_left", false, true);

        private final String configValue;
        private final boolean right;
        private final boolean bottom;

        Corner(String configValue, boolean right, boolean bottom) {
            this.configValue = configValue;
            this.right = right;
            this.bottom = bottom;
        }

        String configValue() {
            return configValue;
        }

        boolean isRight() {
            return right;
        }

        boolean isBottom() {
            return bottom;
        }

        Corner next() {
            Corner[] corners = values();
            return corners[(ordinal() + 1) % corners.length];
        }

        static Corner fromConfig(String value) {
            for (Corner corner : values()) {
                if (corner.configValue.equals(value)) {
                    return corner;
                }
            }
            return null;
        }
    }

    enum Scale {
        SMALL("small", 0.75F),
        NORMAL("normal", 1.0F),
        LARGE("large", 1.25F);

        private final String configValue;
        private final float factor;

        Scale(String configValue, float factor) {
            this.configValue = configValue;
            this.factor = factor;
        }

        String configValue() {
            return configValue;
        }

        float factor() {
            return factor;
        }

        Scale next() {
            Scale[] scales = values();
            return scales[(ordinal() + 1) % scales.length];
        }

        static Scale fromConfig(String value) {
            for (Scale scale : values()) {
                if (scale.configValue.equals(value)) {
                    return scale;
                }
            }
            return null;
        }
    }
}

final class HudPreferencesStore {
    private static final Logger LOGGER = LoggerFactory.getLogger("Craftmetry");
    private static final int CONFIG_VERSION = 1;
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .setStrictness(Strictness.STRICT)
            .create();

    private final Path path;
    private boolean backupBeforeNextSave;
    private HudPreferences pending;

    private HudPreferencesStore(Path path) {
        this.path = path;
    }

    static HudPreferencesStore createDefault() {
        try {
            Path path = FabricLoader.getInstance().getConfigDir().resolve("craftmetry.json");
            return new HudPreferencesStore(path);
        } catch (RuntimeException exception) {
            LOGGER.warn("Craftmetry preferences are disabled because the config directory is unavailable", exception);
            return new HudPreferencesStore(null);
        }
    }

    HudPreferences load() {
        HudPreferences defaults = HudPreferences.defaults();
        if (path == null || Files.notExists(path)) {
            return defaults;
        }

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement root = GSON.fromJson(reader, JsonElement.class);
            if (root == null || !root.isJsonObject()) {
                throw new InvalidPreferencesException("the root must be a JSON object");
            }

            JsonObject data = root.getAsJsonObject();
            int version = requiredInteger(data, "version");
            if (version != CONFIG_VERSION) {
                throw new InvalidPreferencesException("unsupported config version " + version);
            }

            boolean visible = optionalBoolean(data, "visible", defaults.visible());
            String cornerValue = optionalString(data, "corner", defaults.corner().configValue());
            String scaleValue = optionalString(data, "scale", defaults.scale().configValue());
            HudPreferences.Corner corner = HudPreferences.Corner.fromConfig(cornerValue);
            HudPreferences.Scale scale = HudPreferences.Scale.fromConfig(scaleValue);
            if (corner == null) {
                throw new InvalidPreferencesException("invalid HUD corner");
            }
            if (scale == null) {
                throw new InvalidPreferencesException("invalid HUD scale");
            }

            return new HudPreferences(visible, corner, scale);
        } catch (IOException | JsonParseException | IllegalStateException | InvalidPreferencesException exception) {
            backupBeforeNextSave = true;
            LOGGER.warn("Could not read Craftmetry preferences at {}; using defaults", path, exception);
            return defaults;
        }
    }

    void save(HudPreferences preferences) {
        pending = preferences;
        flush();
    }

    void flush() {
        if (path == null || pending == null) {
            return;
        }

        JsonObject data = new JsonObject();
        data.addProperty("version", CONFIG_VERSION);
        data.addProperty("visible", pending.visible());
        data.addProperty("corner", pending.corner().configValue());
        data.addProperty("scale", pending.scale().configValue());

        Path temporary = null;
        try {
            Files.createDirectories(path.getParent());
            preserveInvalidPreferences();
            temporary = Files.createTempFile(path.getParent(), "craftmetry-", ".tmp");
            Files.writeString(
                    temporary,
                    GSON.toJson(data) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    WRITE,
                    TRUNCATE_EXISTING);

            try (FileChannel channel = FileChannel.open(temporary, WRITE)) {
                channel.force(true);
            }

            moveReplacing(temporary, path);
            temporary = null;
            pending = null;
        } catch (IOException exception) {
            LOGGER.warn("Could not save Craftmetry preferences at {}", path, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException exception) {
                    LOGGER.debug("Could not remove temporary Craftmetry preferences at {}", temporary, exception);
                }
            }
        }
    }

    private void preserveInvalidPreferences() throws IOException {
        if (!backupBeforeNextSave) {
            return;
        }
        if (Files.notExists(path)) {
            backupBeforeNextSave = false;
            return;
        }

        String backupName = path.getFileName() + ".broken-" + Instant.now().toEpochMilli();
        Path backup = path.resolveSibling(backupName);
        Files.copy(path, backup);
        backupBeforeNextSave = false;
        LOGGER.warn("Preserved unreadable Craftmetry preferences at {}", backup);
    }

    private static void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException exception) {
            Files.move(source, target, REPLACE_EXISTING);
        }
    }

    private static int requiredInteger(JsonObject data, String name) throws InvalidPreferencesException {
        JsonPrimitive value = requiredPrimitive(data, name);
        if (!value.isNumber()) {
            throw new InvalidPreferencesException(name + " must be a number");
        }
        try {
            return value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new InvalidPreferencesException(name + " must be an integer", exception);
        }
    }

    private static boolean optionalBoolean(JsonObject data, String name, boolean fallback)
            throws InvalidPreferencesException {
        if (!data.has(name)) {
            return fallback;
        }
        JsonPrimitive value = requiredPrimitive(data, name);
        if (!value.isBoolean()) {
            throw new InvalidPreferencesException(name + " must be a boolean");
        }
        return value.getAsBoolean();
    }

    private static String optionalString(JsonObject data, String name, String fallback)
            throws InvalidPreferencesException {
        if (!data.has(name)) {
            return fallback;
        }
        JsonPrimitive value = requiredPrimitive(data, name);
        if (!value.isString()) {
            throw new InvalidPreferencesException(name + " must be a string");
        }
        return value.getAsString();
    }

    private static JsonPrimitive requiredPrimitive(JsonObject data, String name)
            throws InvalidPreferencesException {
        JsonElement value = data.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new InvalidPreferencesException(name + " must be a primitive value");
        }
        return value.getAsJsonPrimitive();
    }

    private static final class InvalidPreferencesException extends Exception {
        private InvalidPreferencesException(String message) {
            super(message);
        }

        private InvalidPreferencesException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
