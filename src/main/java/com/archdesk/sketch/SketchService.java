package com.archdesk.sketch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.archdesk.client.ClientDtos;
import com.archdesk.client.ClientDtos.SketchResponse;
import com.archdesk.common.NotFoundException;
import com.archdesk.project.Project;
import com.archdesk.project.ProjectRepository;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
public class SketchService {
    private static final Logger log = LoggerFactory.getLogger(SketchService.class);
    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L;
    private static final Set<String> HEIC_BRANDS = Set.of("heic", "heix", "heim", "heis", "mif1", "msf1", "heif", "hevc", "hevx");

    private final ProjectSketchRepository sketches;
    private final ProjectRepository projects;
    private final S3Client s3Client;
    private final String bucket;

    public SketchService(
            ProjectSketchRepository sketches,
            ProjectRepository projects,
            S3Client s3Client,
            @Value("${app.r2.bucket}") String bucket) {
        this.sketches = sketches;
        this.projects = projects;
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Transactional(readOnly = true)
    public List<SketchResponse> list(Long projectId) {
        ensureProjectExists(projectId);
        return sketches.findByProjectIdOrderByUploadedAtAsc(projectId).stream().map(ClientDtos::sketch).toList();
    }

    @Transactional
    public SketchResponse upload(Long projectId, MultipartFile file, String caption) {
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File exceeds 10 MB limit");
        }

        Project project = ensureProjectExists(projectId);
        byte[] input = readFile(file);
        ImageType type = detectType(input);
        byte[] finalBytes = input;
        String extension = type.extension;
        String mimeType = type.mimeType;

        if (type == ImageType.HEIC) {
            finalBytes = convertHeic(input);
            extension = "jpg";
            mimeType = "image/jpeg";
        }

        String fileName = UUID.randomUUID() + "." + extension;
        String key = sketchKey(projectId, fileName);

        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(mimeType)
                    .contentLength((long) finalBytes.length)
                    .build(), RequestBody.fromBytes(finalBytes));
        } catch (S3Exception ex) {
            log.warn("Unable to upload sketch file to R2: {}", key, ex);
            throw new IllegalArgumentException("Unable to store sketch file");
        }

        ProjectSketch sketch = new ProjectSketch();
        sketch.setProject(project);
        sketch.setFileName(fileName);
        sketch.setOriginalName(originalName(file));
        sketch.setCaption(cleanCaption(caption));
        sketch.setFileSize((long) finalBytes.length);
        sketch.setMimeType(mimeType);
        return ClientDtos.sketch(sketches.save(sketch));
    }

    @Transactional
    public SketchResponse updateCaption(Long projectId, Long sketchId, String caption) {
        ProjectSketch sketch = findOwned(projectId, sketchId);
        sketch.setCaption(cleanCaption(caption));
        return ClientDtos.sketch(sketch);
    }

    @Transactional
    public void delete(Long projectId, Long sketchId) {
        ProjectSketch sketch = findOwned(projectId, sketchId);
        String key = sketchKey(projectId, sketch.getFileName());
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (S3Exception ex) {
            log.warn("Unable to delete sketch file from R2: {}", key, ex);
        }
        sketches.delete(sketch);
    }

    private String sketchKey(Long projectId, String fileName) {
        return "sketches/" + projectId + "/" + fileName;
    }

    private Project ensureProjectExists(Long projectId) {
        return projects.findById(projectId).orElseThrow(() -> new NotFoundException("Project not found"));
    }

    private ProjectSketch findOwned(Long projectId, Long sketchId) {
        ProjectSketch sketch = sketches.findById(sketchId).orElseThrow(() -> new NotFoundException("Sketch not found"));
        if (!sketch.getProject().getId().equals(projectId)) {
            throw new NotFoundException("Sketch not found for project");
        }
        return sketch;
    }

    private byte[] readFile(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to read sketch file");
        }
    }

    private ImageType detectType(byte[] bytes) {
        byte[] header = Arrays.copyOf(bytes, Math.min(bytes.length, 12));
        if (header.length >= 3
                && unsigned(header[0]) == 0xFF
                && unsigned(header[1]) == 0xD8
                && unsigned(header[2]) == 0xFF) {
            return ImageType.JPEG;
        }
        if (header.length >= 8
                && unsigned(header[0]) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {
            return ImageType.PNG;
        }
        if (header.length >= 12
                && header[0] == 0x52
                && header[1] == 0x49
                && header[2] == 0x46
                && header[3] == 0x46
                && header[8] == 0x57
                && header[9] == 0x45
                && header[10] == 0x42
                && header[11] == 0x50) {
            return ImageType.WEBP;
        }
        if (header.length >= 12
                && header[4] == 0x66
                && header[5] == 0x74
                && header[6] == 0x79
                && header[7] == 0x70
                && HEIC_BRANDS.contains(new String(header, 8, 4, StandardCharsets.US_ASCII))) {
            return ImageType.HEIC;
        }
        throw new IllegalArgumentException("Unsupported file type");
    }

    private int unsigned(byte value) {
        return value & 0xFF;
    }

    private byte[] convertHeic(byte[] input) {
        Path tempInput = null;
        Path tempOutput = null;
        try {
            tempInput = Files.createTempFile("archdesk-sketch-", ".heic");
            tempOutput = Files.createTempFile("archdesk-sketch-", ".jpg");
            tempInput.toFile().deleteOnExit();
            tempOutput.toFile().deleteOnExit();
            Files.write(tempInput, input);
            Files.deleteIfExists(tempOutput);
            runConversionCommand(List.of("heif-convert", tempInput.toString(), tempOutput.toString()), "heif-convert");
            return Files.readAllBytes(tempOutput);
        } catch (IOException primaryEx) {
            if (tempInput != null && tempOutput != null) {
                try {
                    Files.deleteIfExists(tempOutput);
                    runConversionCommand(List.of("magick", tempInput.toString(), tempOutput.toString()), "ImageMagick magick");
                    return Files.readAllBytes(tempOutput);
                } catch (IOException magickEx) {
                    try {
                        Files.deleteIfExists(tempOutput);
                        runConversionCommand(List.of("convert", tempInput.toString(), tempOutput.toString()), "ImageMagick convert");
                        return Files.readAllBytes(tempOutput);
                    } catch (IOException convertEx) {
                        log.warn("Unable to convert HEIC image. heif-convert: {}; magick: {}; convert: {}",
                                primaryEx.getMessage(), magickEx.getMessage(), convertEx.getMessage());
                    }
                    throw new IllegalArgumentException("Unable to convert HEIC image");
                }
            }
            log.warn("Unable to prepare HEIC conversion", primaryEx);
            throw new IllegalArgumentException("Unable to convert HEIC image");
        } finally {
            deleteTemp(tempInput);
            deleteTemp(tempOutput);
        }
    }

    private void runConversionCommand(List<String> command, String label) throws IOException {
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                if (!output.isBlank()) {
                    log.warn("{} conversion failed with exit code {}: {}", label, exitCode, output);
                }
                throw new IOException(label + " exited with " + exitCode);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException(label + " conversion interrupted", ex);
        }
    }

    private void deleteTemp(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Unable to delete temp sketch file: {}", path, ex);
        }
    }

    private String cleanCaption(String caption) {
        if (caption == null || caption.isBlank()) return null;
        String cleaned = caption.trim();
        if (cleaned.length() > 100) {
            throw new IllegalArgumentException("Caption must be 100 characters or less");
        }
        return cleaned;
    }

    private String originalName(MultipartFile file) {
        String original = file.getOriginalFilename();
        return original == null || original.isBlank() ? "sketch" : original;
    }

    private enum ImageType {
        JPEG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp"),
        HEIC("heic", "image/heic");

        private final String extension;
        private final String mimeType;

        ImageType(String extension, String mimeType) {
            this.extension = extension;
            this.mimeType = mimeType;
        }
    }
}
