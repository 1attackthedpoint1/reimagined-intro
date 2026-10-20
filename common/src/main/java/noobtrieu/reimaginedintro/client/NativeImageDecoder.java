package noobtrieu.reimaginedintro.client;

import com.mojang.blaze3d.platform.NativeImage;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

public class NativeImageDecoder {
    public static NativeImage decode(InputStream stream) throws IOException {
        byte[] bytes;
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = stream.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            bytes = baos.toByteArray();
        }

        ByteBuffer directBuffer = MemoryUtil.memAlloc(bytes.length);
        directBuffer.put(bytes);
        directBuffer.flip();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);

            // Force 4 channels (RGBA)
            ByteBuffer pixels = STBImage.stbi_load_from_memory(directBuffer, w, h, comp, 4);
            if (pixels == null) {
                throw new IOException("STBImage failed to decode image: " + STBImage.stbi_failure_reason());
            }

            return new NativeImage(NativeImage.Format.RGBA, w.get(0), h.get(0), false, MemoryUtil.memAddress(pixels));
        } finally {
            MemoryUtil.memFree(directBuffer);
        }
    }
}
