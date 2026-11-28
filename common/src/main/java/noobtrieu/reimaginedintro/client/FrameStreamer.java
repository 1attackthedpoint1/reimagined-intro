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

    /**
     * The reversed transition plays the intro frames DOWNSCALED to 960x540
     * (assets textures/rev/, pre-generated). Full-res 1440p decode+upload
     * could not keep 25 fps while the world loads (reports: stalls, then the
     * whole cave sequence skipped). At 540p a decode is ~6 ms and the upload
     * is 2 MB/frame - the 3-thread pool keeps up under any load, and the
     * bilinear-filtered upscale over a 3.4 s transition is fine.
     */
    public static final Identifier REV_TEXTURE_ID = ReimaginedIntro.id("reversed_frame");

    private static DynamicTexture revTexture;
    private static boolean revInitialized;

    /** Reversed-decode thread pool (parallel small-JPG decode under world-load CPU contention). */
    private static final java.util.concurrent.ExecutorService REVERSE_DECODE_POOL =
            java.util.concurrent.Executors.newFixedThreadPool(3, r -> {
                Thread t = new Thread(r, "ReimaginedIntro-RevDecode");
                t.setDaemon(true);
                return t;
            });

    /** Cap on decoded-but-unplayed rev frames held off-heap (540p = ~2 MB each). */
    private static final int REVERSE_CACHE_CAP = 20;

    /** Max time (ms) a missing reversed frame may be held before playback skips ahead. */
    private static final long REVERSE_MAX_HOLD_MS = 250;

    /** When the current hold started (0 = none). */
    private static long reverseHoldSinceMs;

    /** True while the reversed session is active. */
    private static boolean reversedActive;

    /**
     * The reversed video's OWN clock, advanced per render tick by the real
     * delta CAPPED at 50 ms. Rationale: world loading hitches the RENDER
     * THREAD itself (chunk building eats every core); while the game freezes
     * the wall clock keeps running, and on recovery the old wall-clock
     * playback would burn several frames at once ("stuck, then it jumps").
     * With this clock the video freezes WITH the game and resumes exactly
     * where it froze - the transition may stretch in wall time, but never
     * skips content.
     */
    private static long revVideoTimeMs;
    private static long revLastAdvanceMs;

    /** Per-render-tick max video-time advance (ms). */
    private static final long REVERSE_TICK_CAP_MS = 50;

    // session diagnostics (one INFO summary per transition)
    private static int revFramesPlayed;
    private static int revHoldEvents;
    private static int revSkipEvents;
    private static long revWallStartMs;
    private static boolean revSummaryLogged;

    private static void ensureRevTexture() {
        if (revInitialized) return;
        NativeImage first = loadReversedFrame(INTRO_FRAME_COUNT);
        int width = first != null ? first.getWidth() : 960;
        int height = first != null ? first.getHeight() : 540;
        revTexture = new SmoothDynamicTexture("reimagined_rev_frame", width, height, false);
        Minecraft.getInstance().getTextureManager().register(REV_TEXTURE_ID, revTexture);
        revInitialized = true;
        if (first != null) {
            predecodedCache.put(cacheKeyRev(INTRO_FRAME_COUNT), first);
        }
    }

    private static NativeImage loadReversedFrame(int frameIndex) {
        String jpgPath = String.format("/assets/reimagined-intro/textures/rev/frame_%04d.jpg", frameIndex);
        InputStream stream = ReimaginedIntro.class.getResourceAsStream(jpgPath);
        if (stream != null) {
            try (InputStream is = stream) {
                return NativeImageDecoder.decode(is);
            } catch (Throwable e) {
                ReimaginedIntro.LOGGER.error("Failed to decode rev frame #" + frameIndex, e);
            }
        }
        return null;
    }

    private static String cacheKeyRev(int frame) {
        return "r_" + frame;
    }

    private static int revBufferedCount() {
        int n = 0;
        for (String k : predecodedCache.keySet()) {
            if (k.startsWith("r_")) n++;
        }
        return n;
    }

    /**
     * Kicks off background decoding of the whole reversed tail (161 -> 77)
     * NEAREST-FIRST: playback walks downward, so higher indices are needed
     * sooner. Workers park while the unplayed buffer is at the cap, keeping
     * memory bounded; parked tasks bail out when the session ends.
     */
    public static synchronized void preDecodeReversed() {
        ensureRevTexture();
        for (int f = INTRO_FRAME_COUNT - 1; f > REVERSED_STATIC_FRAME; f--) {
            final int frame = f;
            if (predecodedCache.containsKey(cacheKeyRev(frame))) continue;
            REVERSE_DECODE_POOL.submit(() -> {
                String key = cacheKeyRev(frame);
                if (predecodedCache.containsKey(key)) return;
                while (revBufferedCount() >= REVERSE_CACHE_CAP) {
                    if (!reversedActive) return; // session over (cancelled / reveal finished)
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
                NativeImage img = loadReversedFrame(frame);
                if (img != null) {
                    predecodedCache.put(key, img);
                }
            });
        }
    }

    /**
     * Reversed intro playback (enter-world transition): plays the DOWNSCALED
     * rev frames INTRO_FRAME_COUNT -> 1 at the original pacing.
     *
     * Hard rule: NEVER decode on the render thread. A missing frame holds
     * the current picture up to 250 ms, then playback skips minimally to the
     * nearest decoded frame at/below the target.
     */
    public static synchronized void updateReversed() {
        if (!reversedActive) return;
        long now = net.minecraft.util.Util.getMillis();
        long delta = now - revLastAdvanceMs;
        revLastAdvanceMs = now;
        if (delta > 0) {
            revVideoTimeMs += Math.min(delta, REVERSE_TICK_CAP_MS);
            if (revVideoTimeMs > INTRO_TOTAL_DURATION_MS) revVideoTimeMs = INTRO_TOTAL_DURATION_MS;
        }
        updateReversedAt(revVideoTimeMs);
    }

    /** Video-time position of the reversed playback (0..INTRO_TOTAL_DURATION_MS). */
    public static long reversedElapsedMs() {
        return revVideoTimeMs;
    }

    /** Video-time left in the reversed session (0 when inactive/finished). */
    public static long reversedRemainingMs() {
        return reversedActive ? Math.max(0, INTRO_TOTAL_DURATION_MS - revVideoTimeMs) : 0;
    }

    private static void updateReversedAt(long elapsedMs) {
        ensureRevTexture();
        isLoopingPhase = false;

        int targetFrame = INTRO_FRAME_COUNT - (int) (elapsedMs / FRAME_DURATION_MS);
        if (targetFrame < 1) targetFrame = 1;
        // Logo phase (frames 1..76) is one static image: clamp to it.
        int uploadFrame = targetFrame <= REVERSED_STATIC_FRAME ? REVERSED_STATIC_FRAME : targetFrame;
        if (uploadFrame == lastUploadedFrame) return;

        NativeImage image = predecodedCache.get(cacheKeyRev(uploadFrame));
        if (image == null) {
            // Missing: hold, then skip minimally. Never a sync decode here.
            long now = net.minecraft.util.Util.getMillis();
            if (reverseHoldSinceMs == 0) {
                reverseHoldSinceMs = now;
                revHoldEvents++;
                return; // keep the last picture one more beat
            }
            if (now - reverseHoldSinceMs < REVERSE_MAX_HOLD_MS) return;
            int best = -1;
            for (String k : predecodedCache.keySet()) {
                if (!k.startsWith("r_")) continue;
                try {
                    int f = Integer.parseInt(k.substring(2));
                    if (f <= uploadFrame && f > best && f != lastUploadedFrame) best = f;
                } catch (Exception ignored) {
                }
            }
            if (best <= 0) return; // nothing ready below: keep holding
            if (best < targetFrame) {
                revSkipEvents++;
                ReimaginedIntro.LOGGER.warn("[ReimaginedIntro/REV] skipped ahead {} frames (target={} best={})", targetFrame - best, targetFrame, best);
            }
            uploadFrame = best;
            image = predecodedCache.remove(cacheKeyRev(uploadFrame));
        } else {
            image = predecodedCache.remove(cacheKeyRev(uploadFrame));
        }
        reverseHoldSinceMs = 0;
        revFramesPlayed++;

        if (image != null && revTexture != null) {
            revTexture.setPixels(image);
            revTexture.upload();
        }
        lastUploadedFrame = uploadFrame;

        if (!revSummaryLogged && revVideoTimeMs >= INTRO_TOTAL_DURATION_MS) {
            revSummaryLogged = true;
            ReimaginedIntro.LOGGER.info("[ReimaginedIntro/REV] reversed video finished: framesPlayed={} holds={} skips={} wallTime={}ms",
                revFramesPlayed, revHoldEvents, revSkipEvents, net.minecraft.util.Util.getMillis() - revWallStartMs);
        }

        // Reversed playback walks downward: anything ABOVE the current index
        // is already played and gets freed. Intro/loop caches are untouched.
        final int played = uploadFrame;
        predecodedCache.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            if (!key.startsWith("r_")) return false;
            try {
                int f = Integer.parseInt(key.substring(2));
                if (f > played) {
                    entry.getValue().close();
                    return true;
                }
            } catch (Exception ignored) {
            }
            return false;
        });
    }

    /**
     * Prepares reversed playback: uploads the LAST rev frame (one small 540p
     * decode at the click moment) and queues the tail decode.
     */
    public static synchronized void startReverse() {
        ensureRevTexture();
        isLoopingPhase = false;
        reversedActive = true;
        reverseHoldSinceMs = 0;
        revVideoTimeMs = 0;
        revLastAdvanceMs = net.minecraft.util.Util.getMillis();
        revFramesPlayed = 0;
        revHoldEvents = 0;
        revSkipEvents = 0;
        revWallStartMs = revLastAdvanceMs;
        revSummaryLogged = false;
        NativeImage first = predecodedCache.remove(cacheKeyRev(INTRO_FRAME_COUNT));
        if (first == null) {
            first = loadReversedFrame(INTRO_FRAME_COUNT);
        }
        if (first != null && revTexture != null) {
            revTexture.setPixels(first);
            revTexture.upload();
        }
        lastUploadedFrame = INTRO_FRAME_COUNT;
        preDecodeReversed();
    }

    /**
     * True when the reversed playback has actually reached the static logo
     * page (position-based, robust to skips: the window opens when the
     * picture IS the logo page, not when the clock says so).
     */
    public static boolean reversedAtLogoPage() {
        return reversedActive && lastUploadedFrame > 0 && lastUploadedFrame <= REVERSED_STATIC_FRAME;
    }

    /** The reversed transition's texture id (the downscaled frame stream). */
    public static Identifier getReversedTextureId() {
        ensureRevTexture();
        return REV_TEXTURE_ID;
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
        reversedActive = false;
        revInitialized = false;
        if (revTexture != null) {
            revTexture.close();
            revTexture = null;
        }
        if (dynamicTexture != null) {
            dynamicTexture.close();
            dynamicTexture = null;
        }
        initialized = false;
        lastUploadedFrame = -1;
    }
}
