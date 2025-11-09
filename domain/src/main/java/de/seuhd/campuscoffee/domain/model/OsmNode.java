package de.seuhd.campuscoffee.domain.model;

import lombok.Builder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Represents an OpenStreetMap node with relevant Point of Sale information.
 * This is the domain model for OSM data before it is converted to a POS object.
 *
 * @param nodeId The OpenStreetMap node ID.
 * @param name The name of the location (from name, name:de, or name:en tag, preferring German).
 * @param description Optional description from OSM tag.
 * @param amenity The amenity type (e.g., "cafe", "bakery").
 * @param street Street name from addr:street tag.
 * @param houseNumber House number from addr:housenumber tag.
 * @param postalCode Postal code from addr:postcode tag.
 * @param city City name from addr:city tag.
 * @param latitude Latitude coordinate from node attribute.
 * @param longitude Longitude coordinate from node attribute.
 */
@Builder
public record OsmNode(
        @NonNull Long nodeId,
        @Nullable String name,
        @Nullable String description,
        @Nullable String amenity,
        @Nullable String street,
        @Nullable String houseNumber,
        @Nullable Integer postalCode,
        @Nullable String city,
        @Nullable Double latitude,
        @Nullable Double longitude
) {
}
