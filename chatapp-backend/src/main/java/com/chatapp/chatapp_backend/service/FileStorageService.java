package com.chatapp.chatapp_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipInputStream;

@Service
public class FileStorageService {
    @Value("${file.upload-dir}") private String uploadDir;
    @Value("${file.base-url}") private String baseUrl;
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.ofEntries(
        Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
        Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"), Map.entry("txt", "text/plain"),
        Map.entry("pdf", "application/pdf"), Map.entry("doc", "application/msword"),
        Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        Map.entry("mp4", "video/mp4"), Map.entry("webm", "video/webm"), Map.entry("3gp", "video/3gpp"));

    public String saveFile(MultipartFile file) throws IOException {
        if (file.isEmpty() || file.getSize() > MAX_SIZE) throw new IllegalArgumentException("Arquivo vazio ou maior que 10 MB.");
        String name = file.getOriginalFilename();
        if (name == null || name.length() > 200 || !name.matches("[^\\\\/:\\x00-\\x1F]+\\.[A-Za-z0-9]+")) throw new IllegalArgumentException("Nome de arquivo invalido");
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        String mime = TYPES.get(extension);
        if (mime == null || !mime.equals(file.getContentType())) throw new IllegalArgumentException("Tipo ou extensao de arquivo nao permitido");
        byte[] bytes = file.getBytes();
        if (bytes.length > MAX_SIZE) throw new IllegalArgumentException("Arquivo maior que 10 MB.");
        validate(bytes, extension);
        if (Set.of("png", "jpg", "jpeg").contains(extension)) {
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) throw new IllegalArgumentException("Imagem invalida");
            var output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, extension.equals("png") ? "png" : "jpeg", output)) throw new IllegalArgumentException("Imagem invalida");
            bytes = output.toByteArray();
            if (bytes.length > MAX_SIZE) throw new IllegalArgumentException("Imagem recodificada maior que 10 MB.");
        }
        String filename = UUID.randomUUID() + "." + extension;
        Path directory = directory(); Files.createDirectories(directory);
        Path target = directory.resolve(filename);
        try { Files.write(target, bytes, StandardOpenOption.CREATE_NEW); }
        catch (IOException problem) { Files.deleteIfExists(target); throw problem; }
        return baseUrl.replaceAll("/+$", "") + "/uploads/" + filename;
    }
    private void validate(byte[] bytes, String extension) throws IOException {
        if (Set.of("png", "jpg", "jpeg", "gif").contains(extension)) {
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw new IllegalArgumentException("Imagem invalida");
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!(format.equals(extension) || format.equals("jpeg") && extension.equals("jpg"))) throw new IllegalArgumentException("Conteudo difere da extensao");
                    int frames = extension.equals("gif") ? reader.getNumImages(true) : 1;
                    if (frames < 1 || frames > 120) throw new IllegalArgumentException("Imagem excede 120 quadros");
                    long pixels = 0;
                    for (int i = 0; i < frames; i++) {
                        int w = reader.getWidth(i), h = reader.getHeight(i); pixels += (long) w * h;
                        if (w < 1 || h < 1 || w > 4096 || h > 4096 || pixels > 16_000_000) throw new IllegalArgumentException("Imagem excede o limite de pixels");
                    }
                    if (reader.read(0) == null) throw new IllegalArgumentException("Imagem invalida");
                } finally { reader.dispose(); }
            }
        } else if (extension.equals("txt")) {
            try { StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)); }
            catch (CharacterCodingException problem) { throw new IllegalArgumentException("Use texto UTF-8"); }
            for (byte b : bytes) if (b >= 0 && b < 32 && b != 9 && b != 10 && b != 13) throw new IllegalArgumentException("Conteudo nao textual");
        } else if (extension.equals("docx")) {
            boolean document = false, types = false; long expanded = 0; int entries = 0;
            try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                java.util.zip.ZipEntry entry; byte[] buffer = new byte[8192];
                while ((entry = zip.getNextEntry()) != null) {
                    if (++entries > 2000 || entry.getName().contains("vbaProject")) throw new IllegalArgumentException("Documento nao permitido");
                    document |= entry.getName().equals("word/document.xml"); types |= entry.getName().equals("[Content_Types].xml");
                    int count; while ((count = zip.read(buffer)) > 0) { expanded += count; if (expanded > 20 * 1024 * 1024) throw new IllegalArgumentException("Documento expandido muito grande"); }
                }
            }
            if (!document || !types) throw new IllegalArgumentException("DOCX invalido");
        } else {
            boolean valid = switch (extension) {
                case "pdf" -> prefix(bytes, new byte[]{37, 80, 68, 70, 45});
                case "doc" -> prefix(bytes, new byte[]{(byte)0xd0,(byte)0xcf,0x11,(byte)0xe0,(byte)0xa1,(byte)0xb1,0x1a,(byte)0xe1});
                case "mp4", "3gp" -> bytes.length >= 12 && ascii(bytes, 4, "ftyp") && (!extension.equals("3gp") || ascii(bytes, 8, "3g"));
                case "webm" -> prefix(bytes, new byte[]{0x1a,0x45,(byte)0xdf,(byte)0xa3});
                case "webp" -> bytes.length >= 16 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP") && ascii(bytes, 12, "VP8");
                default -> false;
            };
            if (!valid) throw new IllegalArgumentException("Conteudo difere do tipo de arquivo");
        }
    }
    private static boolean prefix(byte[] bytes, byte[] magic) { return bytes.length >= magic.length && Arrays.equals(Arrays.copyOf(bytes, magic.length), magic); }
    private static boolean ascii(byte[] bytes, int offset, String value) { return bytes.length >= offset + value.length() && new String(bytes, offset, value.length(), StandardCharsets.US_ASCII).equals(value); }
    private Path directory() { return Paths.get(uploadDir).toAbsolutePath().normalize(); }
    public Path file(String filename) {
        if (!filename.matches("[0-9a-f-]{36}\\.(png|jpg|jpeg|gif|webp|txt|pdf|doc|docx|mp4|webm|3gp)")) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
        Path target = directory().resolve(filename).normalize();
        if (!target.getParent().equals(directory())) throw new IllegalArgumentException("Caminho invalido");
        return target;
    }
    public String filename(String url) {
        var uri = java.net.URI.create(url); String value = uri.getPath();
        if (value == null || !value.matches("/uploads/[^/]+")) throw new IllegalArgumentException("Arquivo invalido");
        String filename = value.substring("/uploads/".length()); file(filename); return filename;
    }
    public String contentType(String filename) { return TYPES.get(filename.substring(filename.lastIndexOf('.') + 1)); }
    public void deleteFile(String fileUrl) {
        try { Files.deleteIfExists(file(filename(fileUrl))); }
        catch (IOException problem) { throw new IllegalStateException("Nao foi possivel remover o arquivo"); }
    }
}
