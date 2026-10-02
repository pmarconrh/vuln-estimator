package com.redhat.mockup.vulnassess.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.jboss.logging.Logger;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import com.redhat.mockup.vulnassess.domain.RequestFile;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Handles persisting uploaded ZIP files to local disk, and cleaning them up
 * when a draft request is cancelled.
 */
@ApplicationScoped
public class FileStorageService {

    private static final Logger LOG = Logger.getLogger(FileStorageService.class);

    @ConfigProperty(name = "app.upload.base-dir", defaultValue = "./data/uploads")
    String baseDir;

    /**
     * Copies each uploaded {@link FileUpload} into {@code ./data/uploads/{referenceId}/}
     * preserving the original file name, and returns the absolute stored paths
     * in the same order as {@code uploads}.
     */
    public List<Path> store(String referenceId, List<FileUpload> uploads) {
        Path targetDir = requestDir(referenceId);
        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create upload directory " + targetDir, e);
        }

        return uploads.stream()
                .map(upload -> copyOne(targetDir, upload))
                .toList();
    }

    private Path copyOne(Path targetDir, FileUpload upload) {
        String safeName = sanitize(upload.fileName());
        Path destination = targetDir.resolve(safeName);
        try {
            Files.copy(upload.uploadedFile(), destination, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to store uploaded file " + safeName, e);
        }
        return destination;
    }

    /** Deletes the whole storage directory for the given request (used on cancel). */
    public void deleteAll(String referenceId) {
        Path targetDir = requestDir(referenceId);
        if (!Files.exists(targetDir)) {
            return;
        }
        try (var walk = Files.walk(targetDir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    LOG.warnf(e, "Failed to delete %s", path);
                }
            });
        } catch (IOException e) {
            LOG.warnf(e, "Failed to walk directory %s for deletion", targetDir);
        }
    }

    private Path requestDir(String referenceId) {
        return Path.of(baseDir, referenceId).normalize();
    }

    /**
     * Returns the single source archive to upload to the external scanner
     * for this request's files.
     *
     * <p>The real scanner's {@code POST /api/scan/{id}} only accepts <em>one</em>
     * source archive per scan. When the request has exactly one uploaded file,
     * it is used as-is (no re-processing, zero risk of corrupting it). When it
     * has several, their contents are extracted and merged into one combined
     * archive, each original upload's files placed under a subfolder named
     * after it, so the scanner still sees one coherent, directly-scannable
     * project tree rather than a zip of zips.
     */
    public Path prepareScanArchive(String referenceId, List<RequestFile> files) {
        if (files.size() == 1) {
            return Path.of(files.get(0).storedPath);
        }

        Path combined = requestDir(referenceId).resolve("_combined-for-scan.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(combined))) {
            for (RequestFile file : files) {
                String prefix = stripExtension(file.originalFileName) + "/";
                try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(Path.of(file.storedPath)))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (entry.isDirectory()) {
                            continue;
                        }
                        zos.putNextEntry(new ZipEntry(prefix + entry.getName()));
                        zis.transferTo(zos);
                        zos.closeEntry();
                        zis.closeEntry();
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to build combined scan archive for " + referenceId, e);
        }
        return combined;
    }

    private String stripExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "upload";
        }
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String sanitize(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "upload.zip";
        }
        // Strip any path components a browser might have sent, keep just the file name.
        String name = fileName.replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name;
    }
}
