package com.archdesk.sketch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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

@Service
public class SketchService {
    private static final Logger log = LoggerFactory.getLogger(SketchService.class);
    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L;

    private final ProjectSketchRepository sketches;
    private final ProjectRepository projects;
    private final Path uploadRoot;

    public SketchService(
            ProjectSketchRepository sketches,
            ProjectRepository projects,
            @Value("${app.upload.dir:/app/uploads}") String uploadDir) {
        this.sketches = sketches;
        this.projects = projects;
        this.uploadRoot = Path.of(uploadDir);
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
        Path directory = uploadRoot.resolve("sketches").resolve(projectId.toString());
        Path target = directory.resolve(fileName);

        try {
            Files.createDirectories(directory);
            Files.write(target, finalBytes);
        } catch (IOException ex) {
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
        Path target = uploadRoot.resolve("sketches").resolve(projectId.toString()).resolve(sketch.getFileName());
        try {
            boolean deleted = Files.deleteIfExists(target);
            if (!deleted) {
                log.warn("Sketch file already missing: {}", target);
            }
        } catch (IOException ex) {
            log.warn("Unable to delete sketch file: {}", target, ex);
        }
        sketches.delete(sketch);
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
        if (header.length >= 8
                && header[4] == 0x66
                && header[5] == 0x74
                && header[6] == 0x79
                && header[7] == 0x70) {
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
            runImageMagick("magick", tempInput, tempOutput);
            return Files.readAllBytes(tempOutput);
        } catch (IOException ex) {
            if (tempInput != null && tempOutput != null) {
                try {
                    runImageMagick("convert", tempInput, tempOutput);
                    return Files.readAllBytes(tempOutput);
                } catch (IOException fallbackEx) {
                    throw new IllegalArgumentException("Unable to convert HEIC image");
                }
            }
            throw new IllegalArgumentException("Unable to convert HEIC image");
        } finally {
            deleteTemp(tempInput);
            deleteTemp(tempOutput);
        }
    }

    private void runImageMagick(String command, Path input, Path output) throws IOException {
        Process process = new ProcessBuilder(command, input.toString(), output.toString()).start();
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("ImageMagick exited with " + exitCode);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("ImageMagick conversion interrupted", ex);
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
