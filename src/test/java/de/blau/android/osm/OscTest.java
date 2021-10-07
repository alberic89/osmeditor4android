package de.blau.android.osm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Test;
import org.xml.sax.SAXException;

import de.blau.android.OscTestCommon;

public class OscTest {

    /**
     * Read an osc file ps,amd format
     */
    @Test
    public void readOsmAndOsc() {
        InputStream input = getClass().getResourceAsStream("/osmand.osc");
        OsmChangeParser parser = new OsmChangeParser();
        try {
            parser.start(input);
            Storage storage = parser.getStorage();
            assertFalse(storage.isEmpty());
            assertEquals(1, storage.getElements().size());
            assertEquals(1, parser.getNotes().size());
        } catch (SAXException | IOException | ParserConfigurationException | IllegalArgumentException | IllegalStateException e) {
            fail(e.getMessage());
        }
    }

    /**
     * Read and apply osc file
     * 
     * Unit test version of ApplyOSCTest
     */
    @Test
    public void readAndApplyOsc() {
        final OsmParser osmParser = new OsmParser();
        osmParser.clearBoundingBoxes();
        try (final InputStream in = getClass().getResourceAsStream("/" + OscTestCommon.OSM_FILE)) {
            osmParser.start(in);
            StorageDelegator d = new StorageDelegator();
            try {
                d.reset(false);
                d.setCurrentStorage(osmParser.getStorage()); // this sets dirty flag
                d.fixupApiStorage();
                OscTestCommon.checkInitialState(d);
                InputStream input = getClass().getResourceAsStream("/" + OscTestCommon.OSC_FILE);
                OsmChangeParser parser = new OsmChangeParser();
                try {
                    parser.start(input);
                    Storage osc = parser.getStorage();
                    d.applyOsc(osc, null);
                    OscTestCommon.checkNewState(d);
                } catch (SAXException | IOException | ParserConfigurationException | IllegalArgumentException | IllegalStateException e) {
                    fail(e.getMessage());
                }

            } catch (Exception e) {
                fail(e.getMessage());
            }
        } catch (SAXException | IOException | ParserConfigurationException e1) {
            fail(e1.getMessage());
        }
    }
}