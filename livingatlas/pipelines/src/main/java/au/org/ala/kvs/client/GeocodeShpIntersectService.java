package au.org.ala.kvs.client;

import au.org.ala.kvs.GeocodeShpConfig;
import au.org.ala.kvs.ShapeFile;
import au.org.ala.layers.intersect.SimpleShapeFile;
import com.google.common.base.Strings;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.gbif.pipelines.common.PipelinesException;
import org.gbif.rest.client.geocode.GeocodeResponse.Location;

/**
 * This is a port of the functionality in geocode to using ALA's layer-store
 * (https://github.com/AtlasOfLivingAustralia/layers-store) SimpleShapeFile for intersections.
 *
 * @see SimpleShapeFile
 */
@Slf4j
public class GeocodeShpIntersectService {

  public static final String LINE_SEPARATOR = System.getProperty("line.separator");
  public static final String STATE_PROVINCE_LOCATION_TYPE = "StateProvince";
  public static final String POLITICAL_LOCATION_TYPE = "Political";
  public static final String EEZ_LOCATION_TYPE = "EEZ";

  private static GeocodeShpIntersectService instance;
  private final GeocodeShpConfig config;
  private final SimpleShapeFile countries;
  private final SimpleShapeFile eez;
  private final SimpleShapeFile states;

  private GeocodeShpIntersectService(GeocodeShpConfig config) {
    synchronized (this) {
      checkResourceFiles(config);
      this.config = config;
      this.countries =
          new SimpleShapeFile(config.getCountry().getPath(), config.getCountry().getField());
      this.eez = new SimpleShapeFile(config.getEez().getPath(), config.getEez().getField());
      this.states =
          new SimpleShapeFile(
              config.getStateProvince().getPath(), config.getStateProvince().getField());
    }
  }

  /** Validate resource file paths are available. */
  private void checkResourceFiles(GeocodeShpConfig config) {
    String error = "";
    if (config == null) {
      error = "FATAL: No SHP file configuration found. Please add to YAML.";
    } else {
      if (config.getCountry() == null
          || !new File(config.getCountry().getPath() + ".dbf").exists()) {
        error =
            String.format(
                "FATAL: SHP file of Country: %s does not exist! Check property file defined in --properties argument!",
                config.getCountry().getPath() + ".dbf");
      }
      if (config.getEez() == null || !new File(config.getEez().getPath() + ".dbf").exists()) {
        error =
            String.format(
                "FATAL: SHP file of EEZ: %s does not exist! Check property file defined in --properties argument!",
                config.getEez().getPath() + ".dbf");
      }
      if (config.getStateProvince() == null
          || !new File(config.getStateProvince().getPath() + ".dbf").exists()) {
        error =
            String.format(
                "FATAL: SHP file of State: %s does not exist! Check property file defined in --properties argument!",
                config.getStateProvince().getPath() + ".dbf");
      }
    }

    if (!Strings.isNullOrEmpty(error)) {
      error = LINE_SEPARATOR + Strings.repeat("*", 128) + LINE_SEPARATOR + error + LINE_SEPARATOR;
      error +=
          LINE_SEPARATOR
              + "The following properties are mandatory in the pipelines.yaml for location interpretation:";
      error +=
          LINE_SEPARATOR
              + "Those properties need to be defined in a property file given by -- properties argument.";
      error += LINE_SEPARATOR;
      error +=
          LINE_SEPARATOR
              + "\t"
              + String.format(
                  "%-32s%-48s%-32s",
                  "geocodeConfig.country.path",
                  "SHP file for country searching.",
                  "Example: /data/pipelines-shp/political (DO NOT INCLUDE extension)");
      error +=
          LINE_SEPARATOR
              + "\t"
              + String.format(
                  "%-32s%-48s", "geocodeConfig.country.nameField", "SHP field of country name");
      error +=
          LINE_SEPARATOR
              + "\t"
              + String.format(
                  "%-32s%-48s%-32s",
                  "geocodeConfig.eez.path",
                  "SHP file for country searching.",
                  "Example: /data/pipelines-shp/eez (DO NOT INCLUDE extension)");
      error +=
          LINE_SEPARATOR
              + "\t"
              + String.format(
                  "%-32s%-48s", "geocodeConfig.eez.nameField", "SHP field of country name");
      error +=
          LINE_SEPARATOR
              + "\t"
              + String.format(
                  "%-32s%-48s%-32s",
                  "geocodeConfig.stateProvince.path",
                  "SHP file for state searching.",
                  "Example: /data/pipelines-shp/cw_state_poly (DO NOT INCLUDE extension)");
      error +=
          LINE_SEPARATOR
              + "\t"
              + String.format(
                  "%-32s%-48s", "geocodeConfig.stateProvince.nameField", "SHP field of state name");
      error += LINE_SEPARATOR + Strings.repeat("*", 128);
      log.error(error);
      throw new PipelinesException(error);
    }
  }

  public static GeocodeShpIntersectService getInstance(GeocodeShpConfig config) {
    if (instance == null) {
      instance = new GeocodeShpIntersectService(config);
    }
    return instance;
  }

  public List<Location> lookupCountry(Double latitude, Double longitude) {
    List<Location> locations = new ArrayList<>();
    String countryValue = countries.intersect(longitude, latitude);
    String eezValue = null;
    if (countryValue != null) {
      Location l = new Location();
      l.setType(POLITICAL_LOCATION_TYPE);
      l.setSource(config.getCountry().getSource());
      l.setId(countryValue);
      l.setName(countryValue);
      l.setIsoCountryCode2Digit(countryValue);
      l.setDistance(0d);
      locations.add(l);
    } else {
      eezValue = eez.intersect(longitude, latitude);
      if (eezValue != null) {
        Location l = new Location();
        l.setId(eezValue);
        l.setType(EEZ_LOCATION_TYPE);
        l.setSource(config.getEez().getSource());
        l.setName(eezValue);
        l.setIsoCountryCode2Digit(eezValue);
        l.setDistance(0d);
        locations.add(l);
      }
    }

    // A point just inside one country can be within the coordinate precision of its neighbour, so
    // offer the neighbours as extra candidates, as the GBIF geocode service does. The matcher keeps
    // the record's own country when it is among them and otherwise still derives the containing
    // one, which is first, so this only relaxes flags, it never changes an existing good match.
    if (countryValue != null && borderBuffer() > 0) {
      addNeighbours(locations, countryValue, latitude, longitude);
    }

    // if no country value, add a 10km buffer
    if (countryValue == null
        && eezValue == null
        && config.getCountry().getIntersectBuffer() != null
        && config.getCountry().getIntersectBuffer() > 0) {
      // add the buffer of 0.1 = approx 11km
      String consensus = intersectWithBuffer(countries, config.getCountry(), latitude, longitude);
      if (consensus != null) {
        Location l = new Location();
        l.setType(POLITICAL_LOCATION_TYPE);
        l.setSource(config.getCountry().getSource());
        l.setId(consensus);
        l.setName(consensus);
        l.setIsoCountryCode2Digit(consensus);
        l.setDistance(0d);
        locations.add(l);
      }

      if (consensus == null
          && config.getEez().getIntersectBuffer() != null
          && config.getEez().getIntersectBuffer() > 0) {
        consensus = intersectWithBuffer(eez, config.getEez(), latitude, longitude);
        if (consensus != null) {
          Location l = new Location();
          l.setType(POLITICAL_LOCATION_TYPE);
          l.setSource(config.getCountry().getSource());
          l.setId(consensus);
          l.setName(consensus);
          l.setIsoCountryCode2Digit(consensus);
          l.setDistance(0d);
          locations.add(l);
        }
      }
    }

    return locations;
  }

  public List<Location> lookupStateProvince(Double latitude, Double longitude) {
    List<Location> locations = new ArrayList<>();
    String state = states.intersect(longitude, latitude);
    if (state != null) {
      Location l = new Location();
      l.setType(STATE_PROVINCE_LOCATION_TYPE);
      l.setSource(config.getStateProvince().getSource());
      l.setId(state);
      l.setName(state);
      l.setDistance(0d);
      locations.add(l);
    }

    // if no state vlaue value, add a 10km buffer
    if (state == null
        && config.getStateProvince().getIntersectBuffer() != null
        && config.getStateProvince().getIntersectBuffer() > 0) {
      // add the buffer of 0.1 = approx 11km
      String consensus =
          intersectWithBuffer(states, config.getStateProvince(), latitude, longitude);
      if (consensus != null) {
        Location l = new Location();
        l.setType(POLITICAL_LOCATION_TYPE);
        l.setSource(config.getCountry().getSource());
        l.setId(consensus);
        l.setName(consensus);
        l.setIsoCountryCode2Digit(consensus);
        l.setDistance(0d);
        locations.add(l);
      }
    }

    return locations;
  }

  /**
   * Border tolerance in degrees, used when the config does not set one. This cannot rely on the
   * field initialiser in {@link ShapeFile}: lombok.config sets
   * lombok.anyConstructor.addConstructorProperties, so Jackson deserialises through the all-args
   * constructor and every field initialiser in that class is bypassed. An absent borderBuffer
   * therefore arrives as null, not as the declared default.
   */
  private static final double DEFAULT_BORDER_BUFFER = 0.1;

  /** Configured border tolerance in degrees, or the default when the config omits it. */
  private double borderBuffer() {
    Double configured = config.getCountry().getBorderBuffer();
    return configured == null ? DEFAULT_BORDER_BUFFER : configured;
  }

  /** Adds any country within borderBuffer degrees, other than the one already matched. */
  private void addNeighbours(
      List<Location> locations, String countryValue, Double latitude, Double longitude) {
    double b = borderBuffer();
    // ponytail: 4 corner samples, the same square approximation intersectWithBuffer already uses.
    // Misses a neighbour whose territory only reaches the N/S/E/W midpoints, sample 8 if that shows
    // up in practice.
    List<String> corners =
        Arrays.asList(
            countries.intersect(longitude - b, latitude - b),
            countries.intersect(longitude - b, latitude + b),
            countries.intersect(longitude + b, latitude - b),
            countries.intersect(longitude + b, latitude + b));

    corners.stream()
        .filter(c -> c != null && !c.equals(countryValue))
        .distinct()
        .forEach(
            c -> {
              Location l = new Location();
              l.setType(POLITICAL_LOCATION_TYPE);
              l.setSource(config.getCountry().getSource());
              l.setId(c);
              l.setName(c);
              l.setIsoCountryCode2Digit(c);
              // non-zero, so the containing country always sorts first in the matcher
              l.setDistance(b);
              locations.add(l);
            });
  }

  private String intersectWithBuffer(
      SimpleShapeFile simpleShapeFile, ShapeFile config, Double latitude, Double longitude) {
    String sw =
        simpleShapeFile.intersect(
            longitude - config.getIntersectBuffer(), latitude - config.getIntersectBuffer());
    String nw =
        simpleShapeFile.intersect(
            longitude - config.getIntersectBuffer(), latitude + config.getIntersectBuffer());
    String se =
        simpleShapeFile.intersect(
            longitude + config.getIntersectBuffer(), latitude - config.getIntersectBuffer());
    String ne =
        simpleShapeFile.intersect(
            longitude + config.getIntersectBuffer(), latitude + config.getIntersectBuffer());
    return getConsensus(Arrays.asList(sw, nw, se, ne));
  }

  private String getConsensus(List<String> survey) {
    String consensus = null;
    for (String s : survey) {
      if (consensus == null) {
        consensus = s;
      } else if (s != null && !consensus.equals(s)) {
        return null;
      }
    }
    return consensus;
  }
}
