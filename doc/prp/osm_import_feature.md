# Project Requirement Proposal (PRP)
<!-- Adapted from https://github.com/Wirasm/PRPs-agentic-eng/tree/development/PRPs -->

You are a senior software engineer.
Use the information below to implement a new feature or improvement in this software project.

## Goal

**Feature Goal**: Complete the implementation of importing Point of Sale (POS) entities from OpenStreetMap (OSM) nodes. The feature should work for any valid OSM node, not just hardcoded examples.

**Deliverable**: 
1. Extended `OsmNode` domain model with all relevant OSM tag fields
2. Real HTTP client implementation in `OsmDataServiceImpl` that fetches data from the OSM API
3. Generic `convertOsmNodeToPos` method that maps OSM tags to POS fields for any OSM node

**Success Definition**: 
- The endpoint `POST /api/pos/import/osm/{nodeId}` successfully imports any valid OSM cafe/coffee shop node
- OSM data is fetched from the real OpenStreetMap API (https://www.openstreetmap.org/api/0.6/node/{id})
- OSM tags are properly mapped to POS domain model fields
- Missing required fields are handled with appropriate exceptions

## User Persona (if applicable)

**Target User**: API consumers (developers, administrators) who want to add new coffee shops to the Campus Coffee application

**Use Case**: An API user wants to import a new coffee shop location from OpenStreetMap by providing an OSM node ID

**User Journey**: 
1. User identifies an OSM node ID for a coffee shop (e.g., 5589879349 for "Rada Coffee & Rösterei")
2. User sends POST request to `/api/pos/import/osm/{nodeId}`
3. System fetches OSM data, converts it to POS format, and saves it
4. User receives the created/updated POS entity

**Pain Points Addressed**: 
- Currently, the implementation only works for one hardcoded OSM node
- OSM data is not actually fetched from the API (stub implementation)
- Manual data entry is time-consuming and error-prone

## Why

- **Business value**: Enables rapid addition of new coffee shop locations from OpenStreetMap's rich dataset
- **Integration**: Builds on existing POS management infrastructure and follows the established ports-and-adapters architecture
- **Problems solved**: Eliminates manual data entry, ensures data consistency with OpenStreetMap, and makes the feature work for any OSM node

## What

### Current State
- Endpoint `/api/pos/import/osm/{nodeId}` exists but only works for node ID 5589879349
- `OsmDataServiceImpl.fetchNode()` returns hardcoded data instead of calling the OSM API
- `OsmNode` record only contains `nodeId` field
- `convertOsmNodeToPos()` has hardcoded mapping for one specific node

### Required Changes

#### 1. Extend OsmNode Domain Model
The `OsmNode` record in `domain/src/main/java/de/seuhd/campuscoffee/domain/model/OsmNode.java` needs to include:
- `nodeId` (already exists)
- `name` - from OSM tag `name` or `name:de` or `name:en` (prefer German, fallback to English)
- `description` - from OSM tag `description` (optional)
- `amenity` - from OSM tag `amenity` (e.g., "cafe")
- `street` - from OSM tag `addr:street`
- `houseNumber` - from OSM tag `addr:housenumber`
- `postalCode` - from OSM tag `addr:postcode` (as Integer)
- `city` - from OSM tag `addr:city`
- `latitude` - from OSM node attribute `lat`
- `longitude` - from OSM node attribute `lon`

#### 2. Implement Real OSM API Client
Replace the stub in `data/src/main/java/de/seuhd/campuscoffee/data/impl/OsmDataServiceImpl.java`:
- Use Spring's `RestTemplate` or `WebClient` to fetch XML from `https://www.openstreetmap.org/api/0.6/node/{nodeId}`
- Parse the OSM XML response
- Extract node attributes (id, lat, lon) and tags (key-value pairs)
- Map to `OsmNode` domain model
- Handle HTTP errors (404 → `OsmNodeNotFoundException`)

#### 3. Implement Generic OSM to POS Mapping
Update `convertOsmNodeToPos()` in `domain/src/main/java/de/seuhd/campuscoffee/domain/impl/PosServiceImpl.java`:
- Map OSM `amenity` tag to `PosType` enum:
  - "cafe" → `PosType.CAFE`
  - "vending_machine" → `PosType.VENDING_MACHINE`
  - "bakery" → `PosType.BAKERY`
  - "cafeteria" → `PosType.CAFETERIA`
- Map OSM address to `CampusType` enum based on postal code or city:
  - Postal code 69117 → `CampusType.ALTSTADT`
  - Postal code 69115 → `CampusType.BERGHEIM`
  - Postal code 69120 → `CampusType.INF`
  - (Extend logic as needed for other campus locations)
- Extract name, description, street, houseNumber, postalCode, city from OSM tags
- Validate required fields and throw `OsmNodeMissingFieldsException` if missing

### OSM XML Format Reference

The OSM API returns XML in this format:
```xml
<osm version="0.6">
  <node id="5589879349" lat="49.4122362" lon="8.7077883">
    <tag k="name" v="Rada"/>
    <tag k="name:de" v="Rada Coffee &amp; Rösterei"/>
    <tag k="amenity" v="cafe"/>
    <tag k="addr:street" v="Untere Straße"/>
    <tag k="addr:housenumber" v="21"/>
    <tag k="addr:postcode" v="69117"/>
    <tag k="addr:city" v="Heidelberg"/>
    <tag k="description" v="Caffé und Rösterei"/>
    <!-- more tags -->
  </node>
</osm>
```

Key points:
- Node attributes: `id`, `lat`, `lon`, `visible`, `version`, `timestamp`
- Tags are key-value pairs: `<tag k="key" v="value"/>`
- Some values may be HTML-encoded (e.g., `&amp;` for `&`)
- Address fields use `addr:*` prefix
- Name can have language variants: `name`, `name:de`, `name:en`

### Success Criteria

- [ ] `OsmNode` record includes all relevant fields from OSM XML
- [ ] `OsmDataServiceImpl.fetchNode()` makes real HTTP request to OSM API
- [ ] OSM XML is properly parsed and mapped to `OsmNode`
- [ ] `convertOsmNodeToPos()` works for any valid OSM cafe node, not just hardcoded ones
- [ ] OSM `amenity` tag is correctly mapped to `PosType` enum
- [ ] Postal code is correctly mapped to `CampusType` enum
- [ ] Missing required fields throw `OsmNodeMissingFieldsException`
- [ ] Non-existent OSM nodes throw `OsmNodeNotFoundException`
- [ ] The endpoint `/api/pos/import/osm/5589879349` successfully imports "Rada Coffee & Rösterei"
- [ ] The endpoint works for other valid OSM cafe nodes

## Documentation & References

MUST READ - Include the following information in your context window.

The `README.md` file at the root of the project contains setup instructions and example API calls.

This Java Spring Boot application is structured as a multi-module Maven project following the ports-and-adapters architectural pattern.
There are the following submodules:

`api` - Maven submodule for controller adapter.

`application` - Maven submodule for Spring Boot application, test data import, and system tests.

`data` - Maven submodule for data adapter.

`domain` - Maven submodule for domain model, main business logic, and ports.

### Existing Code Structure

- **Domain Model**: `domain/src/main/java/de/seuhd/campuscoffee/domain/model/Pos.java` - POS record with fields: id, createdAt, updatedAt, name, description, type (PosType), campus (CampusType), street, houseNumber, postalCode, city
- **Domain Model**: `domain/src/main/java/de/seuhd/campuscoffee/domain/model/OsmNode.java` - Currently only has nodeId, needs extension
- **Domain Model**: `domain/src/main/java/de/seuhd/campuscoffee/domain/model/PosType.java` - Enum: CAFE, VENDING_MACHINE, BAKERY, CAFETERIA
- **Domain Model**: `domain/src/main/java/de/seuhd/campuscoffee/domain/model/CampusType.java` - Enum: ALTSTADT, BERGHEIM, INF
- **Port**: `domain/src/main/java/de/seuhd/campuscoffee/domain/ports/OsmDataService.java` - Interface for fetching OSM nodes
- **Port Implementation**: `data/src/main/java/de/seuhd/campuscoffee/data/impl/OsmDataServiceImpl.java` - Currently stub implementation
- **Service**: `domain/src/main/java/de/seuhd/campuscoffee/domain/impl/PosServiceImpl.java` - Contains `importFromOsmNode()` and `convertOsmNodeToPos()` methods
- **Controller**: `api/src/main/java/de/seuhd/campuscoffee/api/controller/PosController.java` - Contains endpoint `/api/pos/import/osm/{nodeId}`
- **Exceptions**: `OsmNodeNotFoundException`, `OsmNodeMissingFieldsException` already exist

### Example OSM Node
- Node ID: 5589879349
- API URL: https://www.openstreetmap.org/api/0.6/node/5589879349
- Expected result: POS with name "Rada Coffee & Rösterei", type CAFE, campus ALTSTADT

### Technical Constraints
- Use Spring Boot best practices
- Follow existing code style and patterns
- Maintain ports-and-adapters architecture separation
- Handle XML parsing properly (consider using JAXB or similar)
- Handle HTTP errors appropriately
- Validate required fields before creating POS

