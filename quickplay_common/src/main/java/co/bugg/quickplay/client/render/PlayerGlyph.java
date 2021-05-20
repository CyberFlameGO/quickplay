package co.bugg.quickplay.client.render;

import co.bugg.quickplay.Quickplay;
import co.bugg.quickplay.QuickplayEventHandler;
import co.bugg.quickplay.Reference;
import co.bugg.quickplay.config.AssetFactory;
import com.google.common.hash.Hashing;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Quickplay Glyph class
 */
public class PlayerGlyph {
    /**
     * The maximum amount of times to try downloading before giving up
     */
    public static final int maxDownloadAttempts = 5;

    /**
     * UUID of the owner
     */
    public final UUID uuid;
    /**
     * URL to the glyph image
     */
    public final URL path;
    /**
     * Height of the glyph
     */
    public Integer height = 20;
    /**
     * Vertical offset from the default position that the glyph should be rendered at
     */
    public Float yOffset = 0.0f;
    /**
     * Whether this glyph should be displayed in-game
     */
    public Boolean displayInGames = false;
    /**
     * Whether this Glyph is currently being downloaded or not
     */
    public boolean downloading = false;
    /**
     * The number of times a download has been attempted on this Glyph.
     */
    public int downloadCount = 0;

    /**
     * Constructor
     * @param uuid UUID of the owner
     * @param resource URL to the glyph image
     */
    public PlayerGlyph(UUID uuid, URL resource) {
        this.uuid = uuid;
        this.path = resource;
    }

    /**
     * Constructor
     * @param uuid UUID of the owner
     * @param resource URL to the glyph image
     * @param height The height of the glyph in pixels
     * @param yOffset The offset of the glyph from the original position in meters
     * @param displayInGames Whether or not this Glyph should be displayed in-game
     */
    public PlayerGlyph(UUID uuid, URL resource, Integer height, Float yOffset, Boolean displayInGames) {
        this.uuid = uuid;
        this.path = resource;
        this.height = height;
        this.yOffset = yOffset;
        this.displayInGames = displayInGames;
    }

    /**
     * Try to download this glyph to the Glyphs resource folder
     */
    public synchronized void download() {
        if(!downloading && downloadCount < maxDownloadAttempts) {
            downloading = true;
            downloadCount++;

            final HttpGet get = new HttpGet(path.toString());

            try (CloseableHttpResponse httpResponse = (CloseableHttpResponse) Quickplay.INSTANCE.requestFactory.httpClient.execute(get)) {

                int responseCode = httpResponse.getStatusLine().getStatusCode();

                // If the response code is a successful one & request header is png
                final String contentType = httpResponse.getEntity().getContentType().getValue();
                if (200 <= responseCode && responseCode < 300 && (contentType.equals("image/png") ||
                        contentType.equals("image/jpg") || contentType.equals("image/jpeg"))) {

                    final File file = new File(AssetFactory.glyphsDirectory +
                            Hashing.sha1().hashString(path.toString(), StandardCharsets.UTF_8).toString() + ".png");
                    // Try to create file if necessary
                    if(!file.exists() && !file.createNewFile()) {
                        throw new IllegalStateException("Glyph file could not be created.");
                    }

                    // Write contents
                    final InputStream in = httpResponse.getEntity().getContent();
                    final FileOutputStream out = new FileOutputStream(file);
                    IOUtils.copy(in, out);
                    out.close();
                    in.close();

                    // Reload the resource
                    QuickplayEventHandler.mainThreadScheduledTasks.add(() -> {
                        Quickplay.INSTANCE.reloadResource(file, new ResourceLocation(Reference.MOD_ID,
                                "glyphs/" + file.getName()));
                        downloading = false;
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
                Quickplay.INSTANCE.sendExceptionRequest(e);
                downloading = false;
            }
        }
    }
}
