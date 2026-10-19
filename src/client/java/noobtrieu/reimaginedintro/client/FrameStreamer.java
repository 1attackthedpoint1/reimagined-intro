package noobtrieu.reimaginedintro.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import noobtrieu.reimaginedintro.ReimaginedIntro;

import java.io.InputStream;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FrameStreamer {
    public static final Identifier TEXTURE_ID = ReimaginedIntro.id("active_frame");
    public static final int INTRO_FRAME_COUNT = 161;
    public static final int LOOP_FRAME_COUNT = 238;
    public static final long FRAME_DURATION_MS = 40; // 25 FPS
    public static final long INTRO_TOTAL_DURATION_MS = INTRO_FRAME_COUNT * FRAME_DURATION_MS; // 6440 ms

    private static DynamicTexture dynamicTexture;
    private static int lastUploadedFrame = -1;
    private static boolean isLoopingPhase = false;
    private static boolean initialized = false;

    // Cache of decoded upcoming NativeImages
    private static final ConcurrentHashMap<String, NativeImage> predecodedCache = new ConcurrentHashMap<>();
    private static final ExecutorService prefetchExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ReimaginedIntro-Prefetcher");
        t.setDaemon(true);
        return t;
    });

    public static synchronized void init() {
        if (initialized) return;

        // Initialize with 1920x1080 dynamic texture
        dynamicTexture = new DynamicTexture("reimagined_frame", 1920, 1080, false);
        Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, dynamicTexture);
        initialized = true;
        lastUploadedFrame = -1;

        // Start prefetching the first few intro frames
        prefetchUpcoming(true, 1, 5);
    }

    public static void update(long elapsedMs) {
        if (!initialized) init();

        boolean inLoop = elapsedMs >= INTRO_TOTAL_DURATION_MS;
        int targetFrame;

        if (!inLoop) {
            isLoopingPhase = false;
            targetFrame = (int) (elapsedMs / FRAME_DURATION_MS) + 1;
            if (targetFrame > INTRO_FRAME_COUNT) targetFrame = INTRO_FRAME_COUNT;
        } else {
            isLoopingPhase = true;
            long loopElapsed = elapsedMs - INTRO_TOTAL_DURATION_MS;
            targetFrame = (int) ((loopElapsed / FRAME_DURATION_MS) % LOOP_FRAME_COUNT) + 1;
        }

        if (targetFrame != lastUploadedFrame) {
            uploadFrame(inLoop, targetFrame);
            lastUploadedFrame = targetFrame;

            // Prefetch ahead by 4 frames
            prefetchUpcoming(inLoop, targetFrame + 1, targetFrame + 4);
        }
    }

    private static void uploadFrame(boolean isLoop, int frameIndex) {
        String key = cacheKey(isLoop, frameIndex);
        NativeImage image = predecodedCache.remove(key);

        if (image == null) {
            // Direct load if prefetch wasn't ready in time
            image = loadFrame(isLoop, frameIndex);
        }

        if (image != null && dynamicTexture != null) {
            dynamicTexture.setPixels(image);
            dynamicTexture.upload();
        }
    }

    private static void prefetchUpcoming(boolean isLoop, int startFrame, int endFrame) {
        prefetchExecutor.submit(() -> {
            int max = isLoop ? LOOP_FRAME_COUNT : INTRO_FRAME_COUNT;
            for (int f = startFrame; f <= endFrame; f++) {
                int frame = isLoop ? (((f - 1) % LOOP_FRAME_COUNT) + 1) : Math.min(f, max);
                String key = cacheKey(isLoop, frame);
                if (!predecodedCache.containsKey(key)) {
                    NativeImage img = loadFrame(isLoop, frame);
                    if (img != null) {
                        predecodedCache.put(key, img);
                    }
                }
            }
        });
    }

    private static NativeImage loadFrame(boolean isLoop, int frameIndex) {
        String path = String.format("/assets/reimagined-intro/textures/%s/frame_%04d.png",
                isLoop ? "loop" : "intro", frameIndex);
        try (InputStream stream = ReimaginedIntro.class.getResourceAsStream(path)) {
            if (stream != null) {
                return NativeImage.read(stream);
            }
        } catch (Throwable e) {
            ReimaginedIntro.LOGGER.error("Failed to load frame: " + path, e);
        }
        return null;
    }

    private static String cacheKey(boolean isLoop, int frame) {
        return (isLoop ? "l_" : "i_") + frame;
    }

    public static Identifier getTextureId() {
        return TEXTURE_ID;
    }

    public static boolean isLooping() {
        return isLoopingPhase;
    }

    public static void cleanup() {
        predecodedCache.values().forEach(NativeImage::close);
        predecodedCache.clear();
        if (dynamicTexture != null) {
            dynamicTexture.close();
            dynamicTexture = null;
        }
        initialized = false;
        lastUploadedFrame = -1;
    }
}
