package de.seuhd.campuscoffee.domain.impl;

import de.seuhd.campuscoffee.domain.exceptions.DuplicatePosNameException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeMissingFieldsException;
import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.CampusType;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.model.Pos;
import de.seuhd.campuscoffee.domain.exceptions.PosNotFoundException;
import de.seuhd.campuscoffee.domain.model.PosType;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import de.seuhd.campuscoffee.domain.ports.PosDataService;
import de.seuhd.campuscoffee.domain.ports.PosService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Implementation of the POS service that handles business logic related to POS entities.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PosServiceImpl implements PosService {
    private final PosDataService posDataService;
    private final OsmDataService osmDataService;

    @Override
    public void clear() {
        log.warn("Clearing all POS data");
        posDataService.clear();
    }

    @Override
    public @NonNull List<Pos> getAll() {
        log.debug("Retrieving all POS");
        return posDataService.getAll();
    }

    @Override
    public @NonNull Pos getById(@NonNull Long id) throws PosNotFoundException {
        log.debug("Retrieving POS with ID: {}", id);
        return posDataService.getById(id);
    }

    @Override
    public @NonNull Pos upsert(@NonNull Pos pos) throws PosNotFoundException {
        if (pos.id() == null) {
            // Create new POS
            log.info("Creating new POS: {}", pos.name());
            return performUpsert(pos);
        } else {
            // Update existing POS
            log.info("Updating POS with ID: {}", pos.id());
            // POS ID must be set
            Objects.requireNonNull(pos.id());
            // POS must exist in the database before the update
            posDataService.getById(pos.id());
            return performUpsert(pos);
        }
    }

    @Override
    public @NonNull Pos importFromOsmNode(@NonNull Long nodeId) throws OsmNodeNotFoundException {
        log.info("Importing POS from OpenStreetMap node {}...", nodeId);

        // Fetch the OSM node data using the port
        OsmNode osmNode = osmDataService.fetchNode(nodeId);

        // Convert OSM node to POS domain object and upsert it
        // TODO: Implement the actual conversion (the response is currently hard-coded).
        Pos savedPos = upsert(convertOsmNodeToPos(osmNode));
        log.info("Successfully imported POS '{}' from OSM node {}", savedPos.name(), nodeId);

        return savedPos;
    }

    /**
     * Converts an OSM node to a POS domain object.
     * Maps OSM tags and attributes to POS fields, validates required fields,
     * and maps amenity to PosType and postal code to CampusType.
     */
    private @NonNull Pos convertOsmNodeToPos(@NonNull OsmNode osmNode) {
        // Validate required fields
        validateRequiredFields(osmNode);
        
        // Map amenity to PosType
        PosType posType = mapAmenityToPosType(osmNode.amenity());
        
        // Map postal code to CampusType
        CampusType campusType = mapPostalCodeToCampusType(osmNode.postalCode());
        
        // Build POS object
        return Pos.builder()
                .name(osmNode.name())
                .description(osmNode.description() != null ? osmNode.description() : "")
                .type(posType)
                .campus(campusType)
                .street(osmNode.street())
                .houseNumber(osmNode.houseNumber())
                .postalCode(osmNode.postalCode())
                .city(osmNode.city())
                .build();
    }

    /**
     * Validates that all required fields are present in the OSM node.
     * Throws OsmNodeMissingFieldsException if any required field is missing.
     */
    private void validateRequiredFields(@NonNull OsmNode osmNode) {
        if (osmNode.name() == null || osmNode.name().isEmpty()) {
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }
        if (osmNode.amenity() == null || osmNode.amenity().isEmpty()) {
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }
        if (osmNode.street() == null || osmNode.street().isEmpty()) {
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }
        if (osmNode.houseNumber() == null || osmNode.houseNumber().isEmpty()) {
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }
        if (osmNode.postalCode() == null) {
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }
        if (osmNode.city() == null || osmNode.city().isEmpty()) {
            throw new OsmNodeMissingFieldsException(osmNode.nodeId());
        }
    }

    /**
     * Maps OSM amenity tag to PosType enum.
     * 
     * @param amenity the amenity value from OSM (e.g., "cafe", "bakery")
     * @return the corresponding PosType
     * @throws OsmNodeMissingFieldsException if amenity is null or cannot be mapped
     */
    private @NonNull PosType mapAmenityToPosType(@NonNull String amenity) {
        if (amenity == null || amenity.isEmpty()) {
            throw new IllegalArgumentException("Amenity cannot be null or empty");
        }
        
        return switch (amenity.toLowerCase()) {
            case "cafe" -> PosType.CAFE;
            case "vending_machine" -> PosType.VENDING_MACHINE;
            case "bakery" -> PosType.BAKERY;
            case "cafeteria" -> PosType.CAFETERIA;
            default -> {
                log.warn("Unknown amenity type '{}', defaulting to CAFE", amenity);
                yield PosType.CAFE;
            }
        };
    }

    /**
     * Maps postal code to CampusType enum.
     * 
     * @param postalCode the postal code from OSM
     * @return the corresponding CampusType
     * @throws OsmNodeMissingFieldsException if postal code is null or cannot be mapped
     */
    private @NonNull CampusType mapPostalCodeToCampusType(@NonNull Integer postalCode) {
        if (postalCode == null) {
            throw new IllegalArgumentException("Postal code cannot be null");
        }
        
        return switch (postalCode) {
            case 69117 -> CampusType.ALTSTADT;
            case 69115 -> CampusType.BERGHEIM;
            case 69120 -> CampusType.INF;
            default -> {
                log.warn("Unknown postal code '{}', defaulting to ALTSTADT", postalCode);
                yield CampusType.ALTSTADT;
            }
        };
    }

    /**
     * Performs the actual upsert operation with consistent error handling and logging.
     * Database constraint enforces name uniqueness - data layer will throw DuplicatePosNameException if violated.
     * JPA lifecycle callbacks (@PrePersist/@PreUpdate) set timestamps automatically.
     *
     * @param pos the POS to upsert
     * @return the persisted POS with updated ID and timestamps
     * @throws DuplicatePosNameException if a POS with the same name already exists
     */
    private @NonNull Pos performUpsert(@NonNull Pos pos) throws DuplicatePosNameException {
        try {
            Pos upsertedPos = posDataService.upsert(pos);
            log.info("Successfully upserted POS with ID: {}", upsertedPos.id());
            return upsertedPos;
        } catch (DuplicatePosNameException e) {
            log.error("Error upserting POS '{}': {}", pos.name(), e.getMessage());
            throw e;
        }
    }
}
