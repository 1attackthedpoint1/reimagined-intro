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

        // Dynamically detect frame resolution (e.g. 2560x1440 Standard vs 1920x1080 Lite)
        NativeImage firstFrame = loadFrame(false, 1);
        int width = firstFrame != null ? firstFrame.getWidth() : 2560;
        int height = firstFrame != null ? firstFrame.getHeight() : 1440;

        dynamicTexture = new SmoothDynamicTexture("reimagined_frame", width, height, false);
        Minecraft.getInstance().getTextureManager().register(TEXTURE_ID, dynamicTexture);
        initialized = true;
        lastUploadedFrame = -1;

        if (firstFrame != null) {
            predecodedCache.put(cacheKey(false, 1), firstFrame);
        }

        // Start prefetching the next intro frames
        prefetchUpcoming(false, 2, 5);
    }

    public static void loadLastIntroFrame() {
        if (!initialized) init();
        uploadFrame(false, INTRO_FRAME_COUNT);
        lastUploadedFrame = INTRO_FRAME_COUNT;
    }

    public static void warmupLoop() {
        prefetchUpcoming(true, 1, 4);
    }

    public static void update(long elapsedMs) {
        if (!initialized) init();
        reversedActive = false; // forward session takes over

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

            // Free and remove any skipped or obsolete frames from off-heap memory
            pruneStaleFrames(inLoop, targetFrame);

            // Prefetch ahead by 4 frames
            prefetchUpcoming(inLoop, targetFrame + 1, targetFrame + 4);

            // If nearing end of intro, prefetch loop start frames in the background
            if (!inLoop && targetFrame >= INTRO_FRAME_COUNT - 8) {
                prefetchUpcoming(true, 1, 4);
            }
        }
    }

    /**
     * Frames 1..76 of the intro are ONE static image (the full-red logo page)
     * - measured identical across the range. Reversed playback clamps to
     * frame 76 for the whole logo phase: the page costs a single decode.
     */
    public static final int REVERSED_STATIC_FRAME = 76;

    /** Reversed-prefetch lead: frames decoded ahead of playback. */
    private static final int REVERSE_LEAD = 12;

    /** Wall-clock time consumed by skip-aheads, so the timeline stays monotonic. */
    private static long reverseSkipMs;
    /** True while the reversed session is active (startReverse .. forward update/cleanup). */
    private static boolean reversedActive;

    /**
     * Reversed intro playback (enter-world transition): plays intro frames
     * INTRO_FRAME_COUNT -> 1 at the original pacing. Intro frames were freed
     * after startup; they are re-decoded from the jar on demand.
     *
     * Stall-proofing (decode cannot always keep 25 fps of 1440p JPGs while
     * the world loads): a cache miss does NOT block - playback skips ahead to
     * the nearest already-decoded frame (the video fast-forwards instead of
     * freezing), and only a fully cold cache falls back to a sync decode.
     */
    public static synchronized void updateReversed(long elapsedMs) {
        if (!initialized) init();
        isLoopingPhase = false;

        // Skip-ahead shortens the timeline by the skipped frames' duration so
        // the next tick's target lands right behind the skipped position -
        // playback stays monotonic (never steps back up).
        int targetFrame = INTRO_FRAME_COUNT - (int) ((elapsedMs - reverseSkipMs) / FRAME_DURATION_MS);
        if (targetFrame < 1) targetFrame = 1;
        // Logo phase (frames 1..76) is one static image: clamp to it.
        int uploadFrame = targetFrame <= REVERSED_STATIC_FRAME ? REVERSED_STATIC_FRAME : targetFrame;
        final int frame = uploadFrame;

        if (uploadFrame != lastUploadedFrame) {
            // Skip-ahead on cache miss: jump to the nearest decoded frame we
            // have not played yet (index <= target = further along the
            // reversed timeline) instead of stalling on a synchronous decode.
            if (!predecodedCache.containsKey(cacheKey(false, uploadFrame))) {
                int best = -1;
                for (String key : predecodedCache.keySet()) {
                    if (!key.startsWith("i_")) continue;
                    try {
                        int f = Integer.parseInt(key.substring(2));
                        if (f <= uploadFrame && f > best && f != lastUploadedFrame) best = f;
                    } catch (Exception ignored) {
                    }
                }
                if (best > 0) {
                    reverseSkipMs += (uploadFrame - best) * FRAME_DURATION_MS;
                    uploadFrame = best;
                }
            }
            uploadFrame(false, uploadFrame);
            lastUploadedFrame = uploadFrame;

            // Reversed playback walks downward: anything ABOVE the current
            // index is already played and gets freed. Loop cache is untouched.
            predecodedCache.entrySet().removeIf(entry -> {
                String key = entry.getKey();
                if (key.startsWith("l_")) return false;
                try {
                    int f = Integer.parseInt(key.substring(2));
                    if (f > frame) {
                        entry.getValue().close();
                        return true;
                    }
                } catch (Exception ignored) {
                }
                return false;
            });

            // Prefetch the frames we are about to play (index going down);
            // nothing below the static frame is ever needed.
            int pfEnd = Math.max(uploadFrame - 1, REVERSED_STATIC_FRAME);
            int pfStart = Math.max(uploadFrame - REVERSE_LEAD, REVERSED_STATIC_FRAME);
            if (pfEnd >= pfStart) {
                prefetchUpcoming(false, pfStart, pfEnd);
            }
        }
    }

    /** Prepares reversed playback: uploads the LAST intro frame and prefetches behind it. */
    public static synchronized void startReverse() {
        if (!initialized) init();
        isLoopingPhase = false;
        reversedActive = true;
        reverseSkipMs = 0;
        uploadFrame(false, INTRO_FRAME_COUNT);
        lastUploadedFrame = INTRO_FRAME_COUNT;
        prefetchUpcoming(false, INTRO_FRAME_COUNT - REVERSE_LEAD, INTRO_FRAME_COUNT - 1);
    }

    /**
     * True when the reversed playback has actually reached the static logo
     * page (position-based, robust to skip-aheads: the window opens when the
     * picture IS the logo page, not when the clock says so).
     */
    public static boolean reversedAtLogoPage() {
        return reversedActive && lastUploadedFrame > 0 && lastUploadedFrame <= REVERSED_STATIC_FRAME;
    }

    private static void pruneStaleFrames(boolean isLoop, int currentFrame) {
        if (predecodedCache.isEmpty()) return;

        predecodedCache.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            boolean entryIsLoop = key.startsWith("l_");

            // If we've entered the loop phase, immediately free any remaining intro frames
            if (isLoop && !entryIsLoop) {
                entry.getValue().close();
                return true;
            }

            if (isLoop != entryIsLoop) {
                return false;
            }

            try {
                int frame = Integer.parseInt(key.substring(2));
                boolean isStale;
                if (!isLoop) {
                    isStale = frame < currentFrame;
                } else {
                    int distance = (frame - currentFrame + LOOP_FRAME_COUNT) % LOOP_FRAME_COUNT;
                    isStale = distance > 8;
                }

                if (isStale) {
                    entry.getValue().close();
                    return true;
                }
            } catch (Exception ignored) {
            }

            return false;
        });
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
        String base = isLoop ? "loop" : "intro";
        String jpgPath = String.format("/assets/reimagined-intro/textures/%s/frame_%04d.jpg", base, frameIndex);
        String pngPath = String.format("/assets/reimagined-intro/textures/%s/frame_%04d.png", base, frameIndex);

        InputStream stream = ReimaginedIntro.class.getResourceAsStream(jpgPath);
        if (stream == null) {
            stream = ReimaginedIntro.class.getResourceAsStream(pngPath);
        }

        if (stream != null) {
            try (InputStream is = stream) {
                return NativeImageDecoder.decode(is);
            } catch (Throwable e) {
                ReimaginedIntro.LOGGER.error("Failed to decode frame: " + base + " #" + frameIndex, e);
            }
        }
        return null;
    }

    private static String cacheKey(boolean isLoop, int frame) {
        return (isLoop ? "l_" : "i_") + frame;
    }

    /** True when the loop texture exists and is safe to blit (FLUID wipes rely on this). */
    public static boolean isReady() {
        return initialized && dynamicTexture != null;
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
