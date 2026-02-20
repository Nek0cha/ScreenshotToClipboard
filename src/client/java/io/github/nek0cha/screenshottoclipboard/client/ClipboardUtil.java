package io.github.nek0cha.screenshottoclipboard.client;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
/**
 * Java port of ClipboardImageUtils.kt (reference: hima-nokiwami mod).
 *
 * Strategy per OS:
 *   Windows : AWT Toolkit (primary) -> Win32 CF_DIB via JNA (fallback)
 *   macOS   : osascript (primary)   -> AWT fallback
 *   Linux   : wl-copy / xclip       -> AWT fallback
 */
public final class ClipboardUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger("ScreenshotToClipboard");
    private ClipboardUtil() {}
    // ------------------------------------------------------------------
    // Win32 JNA interfaces  (mirrors kernel32Extra / user32Clipboard in .kt)
    // ------------------------------------------------------------------
    private interface Kernel32Extra extends StdCallLibrary {
        Kernel32Extra INSTANCE = Native.load("kernel32", Kernel32Extra.class, W32APIOptions.DEFAULT_OPTIONS);
        Pointer GlobalAlloc(int uFlags, long dwBytes);
        Pointer GlobalLock(Pointer hMem);
        boolean GlobalUnlock(Pointer hMem);
        Pointer GlobalFree(Pointer hMem);
    }
    private interface User32Clipboard extends StdCallLibrary {
        User32Clipboard INSTANCE = Native.load("user32", User32Clipboard.class, W32APIOptions.DEFAULT_OPTIONS);
        boolean OpenClipboard(Pointer hWndNewOwner);
        boolean CloseClipboard();
        boolean EmptyClipboard();
        Pointer SetClipboardData(int uFormat, Pointer hMem);
    }
    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------
    public static boolean copyImageToClipboard(Path imagePath) {
        if (!waitForFileStable(imagePath, 2500)) {
            LOGGER.warn("[ScreenshotToClipboard] Screenshot file not ready: {}", imagePath);
            return false;
        }
        BufferedImage image;
        try {
            image = ImageIO.read(imagePath.toFile());
        } catch (IOException e) {
            LOGGER.warn("[ScreenshotToClipboard] Failed to read screenshot: {}", imagePath, e);
            return false;
        }
        if (image == null) {
            LOGGER.warn("[ScreenshotToClipboard] ImageIO.read returned null: {}", imagePath);
            return false;
        }
        return copyBufferedImageToClipboard(image, imagePath);
    }
    // ------------------------------------------------------------------
    // OS dispatch  (mirrors copyBufferedImageToClipboard in .kt)
    // ------------------------------------------------------------------
    private static boolean copyBufferedImageToClipboard(BufferedImage image, Path pngPath) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            if (copyMac(pngPath)) return true;
            return copyAwt(image);
        }
        if (os.contains("linux")) {
            if (copyLinux(pngPath)) return true;
            return copyAwt(image);
        }
        // Windows (and unknown): AWT primary, Win32 CF_DIB fallback
        if (copyAwt(image)) return true;
        if (os.contains("windows")) return copyWin32Dib(image);
        LOGGER.warn("[ScreenshotToClipboard] Clipboard copy failed (os={}, java.awt.headless={})",
                os, System.getProperty("java.awt.headless"));
        return false;
    }
    // ------------------------------------------------------------------
    // macOS  (mirrors copyBufferedImageToClipboardMac)
    // ------------------------------------------------------------------
    private static boolean copyMac(Path pngPath) {
        try {
            String escaped = pngPath.toAbsolutePath().toString()
                    .replace("\\", "\\\\").replace("\"", "\\\"");
            String script = "set the clipboard to (read (POSIX file \""
                    + escaped + "\") as \u00abclass PNGf\u00bb)";
            return runProcess(List.of("osascript", "-e", script), null, 2000);
        } catch (Throwable t) {
            LOGGER.warn("[ScreenshotToClipboard] macOS osascript failed", t);
            return false;
        }
    }
    // ------------------------------------------------------------------
    // Linux  (mirrors copyBufferedImageToClipboardLinux)
    // ------------------------------------------------------------------
    private static boolean copyLinux(Path pngPath) {
        try {
            String sessionType = nvl(System.getenv("XDG_SESSION_TYPE"), "").toLowerCase().strip();
            String waylandDisp = nvl(System.getenv("WAYLAND_DISPLAY"), "");
            boolean wayland = "wayland".equals(sessionType) || !waylandDisp.isBlank();
            if (wayland) {
                if (runProcess(List.of("wl-copy", "--type", "image/png"), pngPath, 2000)) return true;
            }
            if (runProcess(List.of("xclip", "-selection", "clipboard", "-t", "image/png", "-i"), pngPath, 2000)) {
                return true;
            }
            LOGGER.warn("[ScreenshotToClipboard] Linux clipboard unavailable. "
                    + "Install wl-clipboard (wl-copy) or xclip. "
                    + "(XDG_SESSION_TYPE={}, java.awt.headless={})",
                    sessionType, System.getProperty("java.awt.headless"));
            return false;
        } catch (Throwable t) {
            LOGGER.warn("[ScreenshotToClipboard] Linux clipboard fallback failed", t);
            return false;
        }
    }
    // ------------------------------------------------------------------
    // AWT clipboard  (mirrors Toolkit section)
    // ------------------------------------------------------------------
    private static boolean copyAwt(BufferedImage image) {
        Throwable last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                if (EventQueue.isDispatchThread()) {
                    doAwtCopy(image);
                } else {
                    EventQueue.invokeAndWait(() -> doAwtCopy(image));
                }
                return true;
            } catch (IllegalStateException e) {
                last = e;
                if (attempt < 2) {
                    try { Thread.sleep(50); }
                    catch (InterruptedException ie) { Thread.currentThread().interrupt(); return false; }
                }
            } catch (Throwable t) {
                last = t;
                break;
            }
        }
        LOGGER.warn("[ScreenshotToClipboard] AWT clipboard copy failed", last);
        return false;
    }
    private static void doAwtCopy(BufferedImage image) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new ImageSelection(image), null);
    }
    // ------------------------------------------------------------------
    // Win32 CF_DIB fallback  (mirrors copyBufferedImageToClipboardWin32Dib)
    // ------------------------------------------------------------------
    private static boolean copyWin32Dib(BufferedImage image) {
        try {
            byte[] dib = buildDib24(image);
            final int GMEM_MOVEABLE = 0x0002;
            Pointer hGlobal = Kernel32Extra.INSTANCE.GlobalAlloc(GMEM_MOVEABLE, dib.length);
            if (hGlobal == null) return false;
            Pointer ptr = Kernel32Extra.INSTANCE.GlobalLock(hGlobal);
            if (ptr == null) {
                Kernel32Extra.INSTANCE.GlobalFree(hGlobal);
                return false;
            }
            try {
                ptr.write(0, dib, 0, dib.length);
            } finally {
                Kernel32Extra.INSTANCE.GlobalUnlock(hGlobal);
            }
            boolean opened = false;
            for (int attempt = 0; attempt < 10; attempt++) {
                if (User32Clipboard.INSTANCE.OpenClipboard(null)) { opened = true; break; }
                if (attempt < 9) try { Thread.sleep(25); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
            if (!opened) {
                Kernel32Extra.INSTANCE.GlobalFree(hGlobal);
                LOGGER.warn("[ScreenshotToClipboard] Win32 OpenClipboard failed");
                return false;
            }
            boolean success = false;
            try {
                if (!User32Clipboard.INSTANCE.EmptyClipboard()) {
                    LOGGER.warn("[ScreenshotToClipboard] Win32 EmptyClipboard failed");
                    return false;
                }
                final int CF_DIB = 8;
                Pointer res = User32Clipboard.INSTANCE.SetClipboardData(CF_DIB, hGlobal);
                if (res == null || Pointer.nativeValue(res) == 0L) {
                    LOGGER.warn("[ScreenshotToClipboard] Win32 SetClipboardData(CF_DIB) failed");
                    return false;
                }
                success = true;
                return true;
            } finally {
                User32Clipboard.INSTANCE.CloseClipboard();
                if (!success) Kernel32Extra.INSTANCE.GlobalFree(hGlobal);
            }
        } catch (Throwable t) {
            LOGGER.warn("[ScreenshotToClipboard] Win32 clipboard fallback failed", t);
            return false;
        }
    }
    /**
     * Build a bottom-up 24bpp BGR CF_DIB byte array.
     * Mirrors buildDib24() in ClipboardImageUtils.kt.
     */
    private static byte[] buildDib24(BufferedImage image) {
        int width  = image.getWidth();
        int height = image.getHeight();
        int rowSize   = ((width * 3 + 3) / 4) * 4;
        int pixBytes  = rowSize * height;
        int totalSize = 40 + pixBytes;
        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(40);      // biSize
        buf.putInt(width);   // biWidth
        buf.putInt(height);  // biHeight (positive = bottom-up)
        buf.putShort((short) 1);   // biPlanes
        buf.putShort((short) 24);  // biBitCount
        buf.putInt(0);       // BI_RGB
        buf.putInt(pixBytes);// biSizeImage
        buf.putInt(0); buf.putInt(0); buf.putInt(0); buf.putInt(0);
        int[] argb = new int[width * height];
        image.getRGB(0, 0, width, height, argb, 0, width);
        for (int y = height - 1; y >= 0; y--) {
            int rowStart = buf.position();
            for (int x = 0; x < width; x++) {
                int px = argb[y * width + x];
                buf.put((byte)  (px        & 0xFF));  // B
                buf.put((byte) ((px >>  8) & 0xFF));  // G
                buf.put((byte) ((px >> 16) & 0xFF));  // R
            }
            int pad = rowSize - (buf.position() - rowStart);
            for (int i = 0; i < pad; i++) buf.put((byte) 0);
        }
        return buf.array();
    }
    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private static boolean waitForFileStable(Path path, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        long lastSize = -1L;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (Files.exists(path)) {
                    long size = Files.size(path);
                    if (size > 0 && size == lastSize) return true;
                    lastSize = size;
                }
            } catch (IOException ignored) {}
            try { Thread.sleep(75); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
        }
        return false;
    }
    private static boolean runProcess(List<String> command, Path stdinFile, long timeoutMs) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (stdinFile != null) {
                try (var in = Files.newInputStream(stdinFile); var out = p.getOutputStream()) {
                    in.transferTo(out);
                }
            } else {
                p.getOutputStream().close();
            }
            boolean done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!done) { p.destroyForcibly(); return false; }
            int exit = p.exitValue();
            if (exit != 0) {
                try {
                    String err = new String(p.getInputStream().readNBytes(4096)).strip();
                    if (!err.isBlank()) LOGGER.warn("[ScreenshotToClipboard] Process {} => {}", command, err);
                } catch (Throwable ignored) {}
            }
            return exit == 0;
        } catch (Throwable t) { return false; }
    }
    private static String nvl(String v, String def) { return v != null ? v : def; }
    // ------------------------------------------------------------------
    // AWT Transferable
    // ------------------------------------------------------------------
    private static final class ImageSelection implements Transferable {
        private final BufferedImage image;
        ImageSelection(BufferedImage i) { this.image = i; }
        @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
        @Override public boolean isDataFlavorSupported(DataFlavor f) { return DataFlavor.imageFlavor.equals(f); }
        @Override public Object getTransferData(DataFlavor f) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(f)) throw new UnsupportedFlavorException(f);
            return image;
        }
    }
}