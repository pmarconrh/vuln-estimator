package com.redhat.mockup.vulnassess.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Produces a mockup cost estimate for a future code-vulnerability assessment.
 *
 * <p>This is intentionally a placeholder: the real assessment tooling that
 * will eventually price this work does not exist yet, so we generate a
 * plausible-looking, semi-randomized figure based on the number and size of
 * the uploaded files, purely for demonstration purposes.
 */
@ApplicationScoped
public class CostEstimationService {

    private static final BigDecimal BASE_COST = BigDecimal.valueOf(250);
    private static final BigDecimal PER_FILE_COST = BigDecimal.valueOf(75);
    private static final BigDecimal PER_MB_COST = BigDecimal.valueOf(0.8);

    /** Computes a randomized, size/file-count-influenced estimate for a set of uploads. */
    public BigDecimal estimate(List<FileUpload> uploads) {
        long totalBytes = uploads.stream().mapToLong(FileUpload::size).sum();
        double totalMb = totalBytes / (1024.0 * 1024.0);

        BigDecimal sizeComponent = PER_MB_COST.multiply(BigDecimal.valueOf(totalMb));
        BigDecimal fileCountComponent = PER_FILE_COST.multiply(BigDecimal.valueOf(uploads.size()));

        // +/- 20% random jitter so repeated demos don't look identical.
        double jitterFactor = 0.8 + ThreadLocalRandom.current().nextDouble(0.0, 0.4);

        BigDecimal subtotal = BASE_COST.add(sizeComponent).add(fileCountComponent);
        BigDecimal total = subtotal.multiply(BigDecimal.valueOf(jitterFactor));

        return total.setScale(2, RoundingMode.HALF_UP);
    }
}
