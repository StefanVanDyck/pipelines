// java
package au.org.ala.pipelines.transforms;

import org.gbif.common.shaded.com.fasterxml.jackson.dataformat.csv.CsvMapper;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.pipelines.io.avro.IndexRecord;
import org.junit.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static au.org.ala.pipelines.transforms.IndexRecordTransform.parseDynamicProperties;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class IndexRecordTransformTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testParseValidJsonObject() throws Exception {
        var input = Map.of(
                "a", 1,
                "b", "x"
        );
        var builder = IndexRecord.newBuilder().setStrings(new HashMap<>());
        var serialized = objectMapper.writeValueAsString(input);

        parseDynamicProperties(serialized, builder);

        assertEquals(Map.of(
                "a", "1",
                "b", "x"
        ), builder.getDynamicProperties());
        assertEquals(serialized, builder.getStrings().get(DwcTerm.dynamicProperties.simpleName()));
    }

    @Test
    // The CSV file does not escape the quotes when present (as for example on a gbif export)
    public void testParseJsonStringWithCSVEscapedQuotes() throws Exception {
        // inner JSON contains an escaped quote in the string value
        var input = Map.of(
                "text", "He said \"hello\", which makes this sentence contain a quote"
        );
        var builder = IndexRecord.newBuilder().setStrings(new HashMap<>());
        var serialized = objectMapper.writeValueAsString(input);
        var csvMapper = new CsvMapper();
        var csvEncoded = csvMapper.writeValueAsString(serialized).trim();

        parseDynamicProperties(csvEncoded, builder);

        assertEquals(input, builder.getDynamicProperties());
        assertEquals(csvEncoded, builder.getStrings().get(DwcTerm.dynamicProperties.simpleName()));
    }

    @Test
    public void testParseInvalidJsonDoesNotThrow() {
        var input = ",,";
        var builder = IndexRecord.newBuilder().setStrings(new HashMap<>());

        parseDynamicProperties(input, builder);

        var properties = builder.getDynamicProperties();
        assertNull(properties);
        assertEquals(input, builder.getStrings().get(DwcTerm.dynamicProperties.simpleName()));
    }

    @Test
    public void testParseEmptyReturnsNull() throws Exception {
        for (var input : List.of("", "     \n")) {
            var builder = IndexRecord.newBuilder().setStrings(new HashMap<>());

            parseDynamicProperties(input, builder);

            assertNull(builder.getDynamicProperties());
            assertEquals(input, builder.getStrings().get(DwcTerm.dynamicProperties.simpleName()));
        }
    }

    @Test
    public void testParseNullReturnsNull() throws Exception {
        String input = null;
        var builder = IndexRecord.newBuilder().setStrings(new HashMap<>());

        parseDynamicProperties(input, builder);

        assertNull(builder.getDynamicProperties());
        assertEquals(input, builder.getStrings().get(DwcTerm.dynamicProperties.simpleName()));
    }
//
//    @Test
//    public void testParseJsonStringWithInvalidInnerThrows() throws Exception {
//        // top-level is a JSON string, but inner content is invalid JSON
//        var invalidInner = "{a:1}";
//        var input = mapper.writeValueAsString(invalidInner);
//        try {
//            parseDynamicProperties(input);
//            fail("Expected exception when inner JSON is invalid");
//assertEquals(input, builder.getStrings().get(DwcTerm.dynamicProperties.simpleName()));
//        } catch (Exception e) {
//            // expected
//        }
//    }
}