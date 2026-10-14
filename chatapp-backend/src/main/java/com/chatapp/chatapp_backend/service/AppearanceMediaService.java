package com.chatapp.chatapp_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class AppearanceMediaService {
    @Value("${file.upload-dir}") private String uploadDir;
    public static final long MAX_BYTES = 4L * 1024 * 1024;

    public String save(MultipartFile file, boolean small) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_BYTES) throw new IllegalArgumentException("Escolha PNG, JPG ou GIF de até 4 MB.");
        byte[] bytes = file.getBytes();
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("Imagem maior que 4 MB.");
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Imagem inválida. Use PNG, JPG ou GIF.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName().toLowerCase();
                if (!java.util.Set.of("png", "jpeg", "jpg", "gif").contains(format)) throw new IllegalArgumentException("Use PNG, JPG ou GIF.");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 4096 || height > 4096 || (long) width * height > 16_000_000) throw new IllegalArgumentException("Imagem excede o limite de 4096 px ou 16 megapixels.");
                boolean gif = format.equals("gif");
                if (gif) {
                    if (bytes.length > 2 * 1024 * 1024) throw new IllegalArgumentException("GIF deve ter até 2 MB.");
                    int frames = reader.getNumImages(true);
                    int maxWidth = small ? 512 : 960, maxHeight = small ? 512 : 540;
                    var streamMetadata = reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0");
                    for (var node = streamMetadata.getFirstChild(); node != null; node = node.getNextSibling()) {
                        if (node.getNodeName().equals("LogicalScreenDescriptor")) {
                            int canvasWidth = Integer.parseInt(node.getAttributes().getNamedItem("logicalScreenWidth").getNodeValue());
                            int canvasHeight = Integer.parseInt(node.getAttributes().getNamedItem("logicalScreenHeight").getNodeValue());
                            if (canvasWidth > maxWidth || canvasHeight > maxHeight || (long) canvasWidth * canvasHeight * frames > 16_000_000) throw new IllegalArgumentException("Canvas do GIF excede o limite de dimensões ou memória.");
                        }
                    }
                    if (frames > 60 || width > maxWidth || height > maxHeight || (long) width * height * frames > 16_000_000) throw new IllegalArgumentException("GIF muito pesado: até 60 quadros, 512 px no avatar ou 960×540 na capa, e 16 milhões de pixels somados.");
                    long pixels = 0;
                    for (int i = 0; i < frames; i++) {
                        int w = reader.getWidth(i), h = reader.getHeight(i);
                        pixels += (long) w * h;
                        if (w > maxWidth || h > maxHeight || pixels > 16_000_000) throw new IllegalArgumentException("Quadros do GIF excedem o limite.");
                    }
                }
                BufferedImage image = reader.read(0);
                boolean jpeg = !gif && !image.getColorModel().hasAlpha();
                int bound = small ? 256 : 1600;
                double scale = Math.min(1, (double) bound / Math.max(width, height));
                BufferedImage output = new BufferedImage(Math.max(1, (int)(width * scale)), Math.max(1, (int)(height * scale)), jpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
                var graphics = output.createGraphics();
                try { graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR); graphics.drawImage(image, 0, 0, output.getWidth(), output.getHeight(), null); }
                finally { graphics.dispose(); }
                Path directory = Paths.get(uploadDir).resolve("appearance"); Files.createDirectories(directory);
                String id = UUID.randomUUID().toString();
                String extension = jpeg ? ".jpg" : ".png";
                Path still = directory.resolve(id + extension), animated = directory.resolve(id + ".gif");
                try {
                    ImageIO.write(output, jpeg ? "jpeg" : "png", still.toFile());
                    if (gif) Files.write(animated, bytes, StandardOpenOption.CREATE_NEW);
                } catch (IOException failure) { Files.deleteIfExists(still); Files.deleteIfExists(animated); throw failure; }
                return "/uploads/appearance/" + id + (gif ? ".gif" : extension);
            } finally { reader.dispose(); }
        }
    }
    public void delete(String url) {
        if (url == null) return;
        String filename = url.substring(url.lastIndexOf('/') + 1);
        if (!filename.matches("[0-9a-f-]{36}\\.(png|gif|jpg)")) return;
        Path directory = Paths.get(uploadDir).resolve("appearance");
        try { Files.deleteIfExists(directory.resolve(filename)); Files.deleteIfExists(directory.resolve(filename.replace(".gif", ".png"))); }
        catch (IOException ignored) { /* Database no longer references the file; orphan cleanup can retry. */ }
    }
}
