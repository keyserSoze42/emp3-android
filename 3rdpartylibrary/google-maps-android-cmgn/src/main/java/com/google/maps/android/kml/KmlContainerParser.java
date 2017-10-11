package com.google.maps.android.kml;

//import com.google.android.gms.maps.model.GroundOverlay;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

/**
 * Parses the container of a given KML file into a KmlContainer object
 *
 * NOTE: This file has been modified from its initial content to account for Common Map Geospatial Notation.
 */
/* package */ class KmlContainerParser {

    private final static String PROPERTY_REGEX = "name|description|visibility|open|address|phoneNumber|altitudeMode|extrude";

    private final static String CONTAINER_REGEX = "Folder|Document";

    private final static String PLACEMARK = "Placemark";

    private final static String STYLE = "Style";

    private final static String STYLE_MAP = "StyleMap";

    private final static String EXTENDED_DATA = "ExtendedData";

    private final static String GROUND_OVERLAY = "GroundOverlay";

    private final static String UNSUPPORTED_REGEX = "altitude|altitudeModeGroup|" +
            "begin|bottomFov|cookie|displayName|displayMode|end|expires|flyToView|" +
            "gridOrigin|httpQuery|leftFov|linkDescription|linkName|linkSnippet|listItemType|maxSnippetLines|" +
            "maxSessionLength|message|minAltitude|minFadeExtent|minLodPixels|minRefreshPeriod|maxAltitude|" +
            "maxFadeExtent|maxLodPixels|maxHeight|maxWidth|near|overlayXY|range|" +
            "refreshMode|refreshInterval|refreshVisibility|rightFov|roll|rotationXY|screenXY|shape|" +
            "sourceHref|state|targetHref|tessellate|tileSize|topFov|viewBoundScale|viewFormat|" +
            "viewRefreshMode|viewRefreshTime|when";

    /**
     * Obtains a Container object (created if a Document or Folder start tag is read by the
     * XmlPullParser) and assigns specific elements read from the XmlPullParser to the container.
     * @param documentBase Path leading to kml file directory to generate full path file url for map service.
     *                     For example if the file was in C:/Documents/MyKMZ.kmz/test.kml this value would be C:/Documents/MyKMZ.kmz
     */

    /* package */
    static KmlContainer createContainer(XmlPullParser parser, String documentBase)
            throws XmlPullParserException, IOException {
        return assignPropertiesToContainer(parser, documentBase);
    }

    /**
     * Creates a new KmlContainer objects and assigns specific elements read from the XmlPullParser
     * to the new KmlContainer.
     *
     * @param parser XmlPullParser object reading from a KML file
     * @param documentBase Path leading to kml file directory to generate full path file url for map service.
     *                     For example if the file was in C:/Documents/MyKMZ.kmz/test.kml this value would be C:/Documents/MyKMZ.kmz
     * @return KmlContainer object with properties read from the XmlPullParser
     */
    private static KmlContainer assignPropertiesToContainer(XmlPullParser parser, String documentBase)
            throws XmlPullParserException, IOException {
        String startTag = parser.getName();
        String containerId = null;
        Map<String, String> containerProperties = new HashMap<>();
        Map<String, KmlStyle> containerStyles = new HashMap<>();
        Map<KmlPlacemark, Object> containerPlacemarks = new HashMap<>();
        List<KmlContainer> nestedContainers = new ArrayList<>();
        Map<String, String> containerStyleMaps = new HashMap<>();
        Map<KmlGroundOverlay, Object> containerGroundOverlays = new HashMap<>();
        boolean isDocument = startTag.equals("Document");

        if (parser.getAttributeValue(null, "id") != null) {
            containerId = parser.getAttributeValue(null, "id");
        }

        parser.next();
        int eventType = parser.getEventType();
        while (!(eventType == XmlPullParser.END_TAG && parser.getName().equals(startTag))) {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.getName().matches(UNSUPPORTED_REGEX)) {
                    KmlParser.skip(parser);
                } else if (parser.getName().matches(CONTAINER_REGEX)) {
                    nestedContainers.add(assignPropertiesToContainer(parser, documentBase));
                } else if (parser.getName().matches(PROPERTY_REGEX)) {
                    containerProperties.put(parser.getName(), parser.nextText());
                } else if (parser.getName().equals(STYLE_MAP)) {
                    setContainerStyleMap(parser, containerStyleMaps);
                } else if (parser.getName().equals(STYLE)) {
                    setContainerStyle(parser, containerStyles);
                } else if (parser.getName().equals(PLACEMARK)) {
                    setContainerPlacemark(parser, containerPlacemarks);
                } else if (parser.getName().equals(EXTENDED_DATA)) {
                    setExtendedDataProperties(parser, containerProperties);
                } else if (parser.getName().equals(GROUND_OVERLAY)) {
                    containerGroundOverlays.put(KmlFeatureParser.createGroundOverlay(parser, documentBase), null);
                }
            }
            eventType = parser.next();
        }

        return new KmlContainer(containerProperties, containerStyles, containerPlacemarks,
                containerStyleMaps, nestedContainers, containerGroundOverlays, isDocument, containerId);
    }

    /**
     * Creates a new style map and assigns values from the XmlPullParser parser
     * and stores it into the container.
     */
    private static void setContainerStyleMap(XmlPullParser parser,
            Map<String, String> containerStyleMap) throws XmlPullParserException, IOException {
        containerStyleMap.putAll(KmlStyleParser.createStyleMap(parser));
    }

    /**
     * Assigns properties given as an extended data element, which are obtained from an
     * XmlPullParser and stores it in a container, Untyped data only, no simple data
     * or schema, and entity replacements of the form $[dataName] are unsupported.
     */
    private static void setExtendedDataProperties(XmlPullParser parser,
            Map<String, String> mContainerProperties)
            throws XmlPullParserException, IOException {
        String propertyKey = null;
        int eventType = parser.getEventType();
        while (!(eventType == XmlPullParser.END_TAG && parser.getName().equals(EXTENDED_DATA))) {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.getName().equals("Data")) {
                    propertyKey = parser.getAttributeValue(null, "name");
                } else if (parser.getName().equals("value") && propertyKey != null) {
                    mContainerProperties.put(propertyKey, parser.nextText());
                    propertyKey = null;
                }
            }
            eventType = parser.next();
        }
    }

    /**
     * Creates a new default Kml Style with a specified ID (given as an attribute value in the
     * start tag) and assigns specific elements read from the XmlPullParser to the Style. A new
     * style is not created if it does not have an ID.
     */
    private static void setContainerStyle(XmlPullParser parser,
            Map<String, KmlStyle> containerStyles) throws XmlPullParserException, IOException {
        if (parser.getAttributeValue(null, "id") != null) {
            KmlStyle style = KmlStyleParser.createStyle(parser);
            String styleId = style.getStyleId();
            containerStyles.put(styleId, style);
        }
    }

    /**
     * Creates a new placemark object  and assigns specific elements read from the XmlPullParser
     * to the Placemark and stores this into the given Container.
     */
    private static void setContainerPlacemark(XmlPullParser parser,
            Map<KmlPlacemark, Object> containerPlacemarks)
            throws XmlPullParserException, IOException {
        containerPlacemarks.put(KmlFeatureParser.createPlacemark(parser), null);
    }

}
