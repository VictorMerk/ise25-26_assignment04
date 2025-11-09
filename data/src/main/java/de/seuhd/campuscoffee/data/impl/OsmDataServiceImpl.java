package de.seuhd.campuscoffee.data.impl;

import de.seuhd.campuscoffee.domain.exceptions.OsmNodeNotFoundException;
import de.seuhd.campuscoffee.domain.model.OsmNode;
import de.seuhd.campuscoffee.domain.ports.OsmDataService;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

/**
 * OSM import service.
 */
@Service
@Slf4j
class OsmDataServiceImpl implements OsmDataService {
    private static final String OSM_API_BASE_URL = "https://www.openstreetmap.org/api/0.6/node/";
    private final RestTemplate restTemplate;

    public OsmDataServiceImpl(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public @NonNull OsmNode fetchNode(@NonNull Long nodeId) throws OsmNodeNotFoundException {
        log.info("Fetching OSM node {} from API...", nodeId);
        
        String url = OSM_API_BASE_URL + nodeId;
        
        try {
            // Set headers to request XML content
            HttpHeaders headers = new HttpHeaders();
            headers.set("Accept", "application/xml, text/xml, */*");
            HttpEntity<?> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            
            log.info("OSM API response status: {}, body length: {}", 
                    response.getStatusCode(), 
                    response.getBody() != null ? response.getBody().length() : 0);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                String responseBody = response.getBody();
                // Log first 500 chars and check if it's valid XML
                log.info("OSM API response body (first 500 chars): {}", 
                        responseBody.length() > 500 ? responseBody.substring(0, 500) : responseBody);
                
                // Check if response starts with XML declaration
                String trimmed = responseBody.trim();
                if (!trimmed.startsWith("<?xml") && !trimmed.startsWith("<osm")) {
                    log.error("Response doesn't appear to be XML. First 200 chars: {}", 
                            trimmed.substring(0, Math.min(200, trimmed.length())));
                    throw new OsmNodeNotFoundException(nodeId);
                }
                
                OsmNode osmNode = parseOsmXml(responseBody, nodeId);
                log.info("Successfully fetched and parsed OSM node {}", nodeId);
                return osmNode;
            } else {
                log.error("Failed to fetch OSM node {}: HTTP status {}, body: {}", 
                        nodeId, response.getStatusCode(), 
                        response.getBody() != null ? response.getBody().substring(0, Math.min(200, response.getBody().length())) : "null");
                throw new OsmNodeNotFoundException(nodeId);
            }
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("OSM node {} not found (404)", nodeId);
            throw new OsmNodeNotFoundException(nodeId);
        } catch (RestClientException e) {
            log.error("Error fetching OSM node {}: {}", nodeId, e.getMessage());
            throw new OsmNodeNotFoundException(nodeId);
        } catch (Exception e) {
            log.error("Unexpected error parsing OSM node {}: {}", nodeId, e.getMessage(), e);
            throw new OsmNodeNotFoundException(nodeId);
        }
    }

    private OsmNode parseOsmXml(String xmlContent, Long nodeId) {
        try {
            if (xmlContent == null || xmlContent.trim().isEmpty()) {
                log.error("Empty XML content received for node {}", nodeId);
                throw new OsmNodeNotFoundException(nodeId);
            }
            
            // Clean XML content - remove BOM and any leading whitespace
            String cleanedXml = xmlContent.trim();
            
            // Remove BOM if present (UTF-8 BOM is 0xEF 0xBB 0xBF = \uFEFF)
            if (cleanedXml.startsWith("\uFEFF")) {
                cleanedXml = cleanedXml.substring(1).trim();
                log.debug("Removed BOM from XML");
            }
            
            // Find the XML declaration or root element
            int xmlStart = cleanedXml.indexOf("<?xml");
            int osmStart = cleanedXml.indexOf("<osm");
            
            if (xmlStart >= 0) {
                // XML declaration found, use it as start
                cleanedXml = cleanedXml.substring(xmlStart);
            } else if (osmStart >= 0) {
                // No XML declaration but root element found
                cleanedXml = cleanedXml.substring(osmStart);
            } else {
                // Neither found - this is an error
                log.error("No XML declaration or root element found in response for node {}. First 200 chars: {}", 
                        nodeId, cleanedXml.substring(0, Math.min(200, cleanedXml.length())));
                throw new OsmNodeNotFoundException(nodeId);
            }
            
            log.debug("Parsing cleaned XML (first 100 chars): {}", cleanedXml.substring(0, Math.min(100, cleanedXml.length())));
            
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setValidating(false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(cleanedXml)));
            
            doc.getDocumentElement().normalize();
            
            NodeList nodeList = doc.getElementsByTagName("node");
            if (nodeList.getLength() == 0) {
                log.error("No node element found in XML for node {}", nodeId);
                throw new OsmNodeNotFoundException(nodeId);
            }
            
            Element nodeElement = (Element) nodeList.item(0);
            
            // Extract node attributes
            Long parsedNodeId = Long.parseLong(nodeElement.getAttribute("id"));
            Double latitude = parseDoubleAttribute(nodeElement, "lat");
            Double longitude = parseDoubleAttribute(nodeElement, "lon");
            
            // Extract tags
            Map<String, String> tags = new HashMap<>();
            NodeList tagList = nodeElement.getElementsByTagName("tag");
            for (int i = 0; i < tagList.getLength(); i++) {
                Element tagElement = (Element) tagList.item(i);
                String key = tagElement.getAttribute("k");
                String value = tagElement.getAttribute("v");
                if (key != null && value != null && !key.isEmpty() && !value.isEmpty()) {
                    tags.put(key, value);
                }
            }
            
            // Extract name with preference: name:de > name:en > name
            String name = tags.get("name:de");
            if (name == null || name.isEmpty()) {
                name = tags.get("name:en");
            }
            if (name == null || name.isEmpty()) {
                name = tags.get("name");
            }
            
            // Decode HTML entities (e.g., &amp; -> &)
            if (name != null) {
                name = decodeHtmlEntities(name);
            }
            
            String description = tags.get("description");
            if (description != null) {
                description = decodeHtmlEntities(description);
            }
            
            String amenity = tags.get("amenity");
            String street = tags.get("addr:street");
            String houseNumber = tags.get("addr:housenumber");
            Integer postalCode = parseInteger(tags.get("addr:postcode"));
            String city = tags.get("addr:city");
            
            return OsmNode.builder()
                    .nodeId(parsedNodeId)
                    .name(name)
                    .description(description)
                    .amenity(amenity)
                    .street(street)
                    .houseNumber(houseNumber)
                    .postalCode(postalCode)
                    .city(city)
                    .latitude(latitude)
                    .longitude(longitude)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error parsing OSM XML for node {}: {}", nodeId, e.getMessage(), e);
            throw new OsmNodeNotFoundException(nodeId);
        }
    }

    private Double parseDoubleAttribute(Element element, String attributeName) {
        String value = element.getAttribute(attributeName);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse {} attribute as double: {}", attributeName, value);
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse value as integer: {}", value);
            return null;
        }
    }

    private String decodeHtmlEntities(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("&amp;", "&")
                  .replace("&lt;", "<")
                  .replace("&gt;", ">")
                  .replace("&quot;", "\"")
                  .replace("&apos;", "'");
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }
}
