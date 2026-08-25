package org.gbif.pipelines.core.parsers.location.cache;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.gbif.kvs.geocode.GeocodeRequest;
import org.gbif.rest.client.geocode.GeocodeResponse;
import org.gbif.rest.client.geocode.GeocodeResponse.Location;

/** A cache which uses a bitmap to cache coordinate lookups. */
@Slf4j
public class GeocodeBitmapCache {

  private final Function<GeocodeRequest, GeocodeResponse> loadFn;

  // World map image lookup
  private final BufferedImage img;
  private static final int BORDER = 0x000000;
  private static final int NOTHING = 0xFFFFFF;

  /**
   * How far a pixel must be from any colour change before its answer is safe to share with every
   * other pixel of that colour. A clear radius of r pixels guarantees r pixels of clearance from
   * any differing region, and the shipped bitmap is 7200x3600, i.e. 0.05 degrees per pixel, so 3
   * gives 0.15 degrees. That must stay above the border tolerance the geocode stores apply,
   * currently 0.1 degrees in the ALA shapefile service, otherwise a border answer gets cached by
   * colour and served country-wide.
   */
  private static final int SAFE_RADIUS_PIXELS = 3;
  private final int imgWidth;
  private final int imgHeight;
  private final Map<Integer, GeocodeResponse> colourKey = new ConcurrentHashMap<>();
  public static final String DEFAULT_KV_STORE = "COUNTRY";
  private final String kvStoreType;
  private boolean missEqualsFail = true;

  @SneakyThrows
  private GeocodeBitmapCache(
      BufferedImage img,
      Function<GeocodeRequest, GeocodeResponse> loadFn,
      String kvStoreType,
      boolean missEqualsFail) {
    this.loadFn = loadFn;
    this.img = img;
    this.imgHeight = img != null ? img.getHeight() : -1;
    this.imgWidth = img != null ? img.getWidth() : -1;
    this.kvStoreType = kvStoreType;
    this.missEqualsFail = missEqualsFail;
  }

  public static GeocodeBitmapCache create(
      @NonNull BufferedImage img, @NonNull Function<GeocodeRequest, GeocodeResponse> loadFn) {
    return new GeocodeBitmapCache(img, loadFn, DEFAULT_KV_STORE, false);
  }

  public static GeocodeBitmapCache create(
      @NonNull BufferedImage img,
      @NonNull Function<GeocodeRequest, GeocodeResponse> loadFn,
      String kvStoreType,
      boolean missEqualsFail) {
    return new GeocodeBitmapCache(img, loadFn, kvStoreType, missEqualsFail);
  }

  /**
   * Check the colour of a pixel from the map image to determine the country. <br>
   * Other than the special cases, the colours are looked up using the web service the first time
   * they are found.
   *
   * @return Locations or null if the bitmap can't answer.
   */
  public GeocodeResponse getFromBitmap(GeocodeRequest latLng) {
    double lat = latLng.getLat();
    double lng = latLng.getLng();
    // Convert the latitude and longitude to x,y coordinates on the image.
    // The axes are swapped, and the image's origin is the top left.
    int x = (int) Math.round((lng + 180d) / 360d * (imgWidth - 1));
    int y = imgHeight - 1 - (int) Math.round((lat + 90d) / 180d * (imgHeight - 1));

    int colour = img.getRGB(x, y) & 0x00FFFFFF; // Ignore possible transparency.

    String hex = String.format("#%06x", colour);
    log.debug(
        "[{}] LatLong {},{} has pixel {},{} with colour {}", kvStoreType, lat, lng, x, y, hex);

    switch (colour) {
      case BORDER:
        return null;

      case NOTHING:
        return new GeocodeResponse(Collections.emptyList());

      default:
        // The colour cache reuses one lookup, made at one point, for every pixel of that colour
        // anywhere on earth, so it must only answer where the country is the same across the
        // neighbourhood. Near a border the answer is position dependent: the underlying store
        // reports the neighbouring country too, and caching that by colour would apply it
        // country-wide.
        if (isNearColourChange(x, y, colour)) {
          return null;
        }
        return getDefaultGeocodeResponse(lat, lng, x, y, colour, hex);
    }
  }

  /**
   * True if any pixel within {@link #SAFE_RADIUS_PIXELS} has a different colour, i.e. a border, the
   * sea or another country is close enough that the answer here is not the colour's answer.
   */
  private boolean isNearColourChange(int x, int y, int colour) {
    for (int dx = -SAFE_RADIUS_PIXELS; dx <= SAFE_RADIUS_PIXELS; dx++) {
      for (int dy = -SAFE_RADIUS_PIXELS; dy <= SAFE_RADIUS_PIXELS; dy++) {
        // longitude wraps at the antimeridian, latitude clamps at the poles
        int nx = Math.floorMod(x + dx, imgWidth);
        int ny = Math.min(Math.max(y + dy, 0), imgHeight - 1);
        if ((img.getRGB(nx, ny) & 0x00FFFFFF) != colour) {
          return true;
        }
      }
    }
    return false;
  }

  private GeocodeResponse getDefaultGeocodeResponse(
      double lat, double lng, int x, int y, int colour, String hex) {

    GeocodeResponse locations;
    if (colourKey.containsKey(colour)) {
      locations = colourKey.get(colour);
      log.debug("[{}] Known colour {} (LL {},{}; pixel {},{})", kvStoreType, hex, lat, lng, x, y);
      return locations;
    }

    locations = loadFn.apply(GeocodeRequest.builder().withLat(lat).withLng(lng).build());
    // Don't store this if there aren't any locations.
    if (locations.getLocations().isEmpty()) {
      if (missEqualsFail) {
        log.error(
            "[{}] For colour {} (LL {},{}; pixel {},{}) the webservice gave zero locations.",
            kvStoreType,
            hex,
            lat,
            lng,
            x,
            y);
      } else {
        log.warn(
            "[{}] For colour {} (LL {},{}; pixel {},{}) the webservice gave zero locations.",
            kvStoreType,
            hex,
            lat,
            lng,
            x,
            y);
      }
      colourKey.put(colour, locations);
    } else {
      log.debug(
          "[{}] New colour {} (LL {},{}; pixel {},{}); remembering as {}",
          kvStoreType,
          hex,
          lat,
          lng,
          x,
          y,
          joinLocations(locations));
      colourKey.put(colour, locations);
    }

    return locations;
  }

  private String joinLocations(GeocodeResponse loc) {
    return loc.getLocations().stream()
        .map(Location::getId)
        .distinct()
        .collect(Collectors.joining(", "));
  }
}
