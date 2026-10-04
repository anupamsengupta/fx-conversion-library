package com.power.fx.api.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * The bitemporal, four-eyes-approved version envelope embedded in every
 * reference-data value object. Re-declared here (not inherited from a
 * shared library) because {@code fx-api} must not depend on
 * {@code uom-api} (module boundary, S13.3 MC-4); identical in shape to
 * UOM FS S5.1 / UOM tech spec S4.3.
 *
 * @see "Tech spec S4.3"
 */
public record VersionEnvelope(
        Scope scope,
        String tenantId,
        String naturalKey,
        String versionId,
        LocalDate validFrom,
        LocalDate validTo,
        Instant recordedAt,
        VersionStatus status,
        String authoredBy,
        String approvedBy,
        Instant approvedAt,
        String sourceSystem,
        String correctionOf,
        String reasonCode,
        String catalogueRelease) {

    public VersionEnvelope {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(naturalKey, "naturalKey must not be null");
        Objects.requireNonNull(versionId, "versionId must not be null");
        Objects.requireNonNull(validFrom, "validFrom must not be null");
        Objects.requireNonNull(recordedAt, "recordedAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(authoredBy, "authoredBy must not be null");
        Objects.requireNonNull(approvedBy, "approvedBy must not be null");
        Objects.requireNonNull(approvedAt, "approvedAt must not be null");

        if (approvedBy.equals(authoredBy)) {
            throw new IllegalArgumentException(
                    "approvedBy must differ from authoredBy (four-eyes rule)");
        }
        if (!approvedAt.equals(recordedAt)) {
            throw new IllegalArgumentException("approvedAt must equal recordedAt");
        }
        if (correctionOf != null && reasonCode == null) {
            throw new IllegalArgumentException("correctionOf requires a reasonCode");
        }
        if (scope == Scope.GLOBAL && tenantId != null) {
            throw new IllegalArgumentException("GLOBAL scope must not carry a tenantId");
        }
        if (scope == Scope.TENANT && tenantId == null) {
            throw new IllegalArgumentException("TENANT scope requires a tenantId");
        }
        if (validTo != null && !validFrom.isBefore(validTo)) {
            throw new IllegalArgumentException("validFrom must be before validTo");
        }
    }
}
