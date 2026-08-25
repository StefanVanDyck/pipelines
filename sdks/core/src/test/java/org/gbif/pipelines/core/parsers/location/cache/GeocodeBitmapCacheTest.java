package org.gbif.pipelines.core.parsers.location.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.gbif.kvs.geocode.GeocodeRequest;
import org.gbif.rest.client.geocode.GeocodeResponse;
import org.gbif.rest.client.geocode.GeocodeResponse.Location;
import org.junit.Test;

/**
 * The colour cache serves one lookup, made at one point, for every pixel of that colour, so it must
 * not answer near a border, where the store also reports the neighbouring country.
 */
public class GeocodeBitmapCacheTest {

  private static final int BE = 0x00FF0000;
  private static final int NL = 0x000000FF;

  private static final int WIDTH = 32;
  private static final int HEIGHT = 16;

  /** Left half one colour, right half another, so the change runs down the middle. */
  private static BufferedImage twoCountries() {
    BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < HEIGHT; y++) {
      for (int x = 0; x < WIDTH; x++) {
        img.setRGB(x, y, x < WIDTH / 2 ? BE : NL);
      }
    }
    return img;
  }

  private static BufferedImage oneCountry() {
    BufferedImage img = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    for (int y = 0; y < HEIGHT; y++) {
      for (int x = 0; x < WIDTH; x++) {
        img.setRGB(x, y, BE);
      }
    }
    return img;
  }

  /** Longitude of the centre of pixel column x, inverting the mapping in getFromBitmap. */
  private static double lngOfColumn(int x) {
    return x * 360d / (WIDTH - 1) - 180d;
  }

  private static GeocodeRequest at(double lng) {
    return GeocodeRequest.create(0d, lng);
  }

  @Test
  public void refusesToAnswerWithinTheSafeRadiusOfAColourChange() {
    GeocodeBitmapCache cache =
        GeocodeBitmapCache.create(twoCountries(), r -> beResponse(), "COUNTRY", false);

    // columns 13-15 are the last three of the BE half, 16-18 the first three of the NL half
    for (int x : new int[] {13, 14, 15, 16, 17, 18}) {
      assertNull(
          "column " + x + " is within 3px of the change", cache.getFromBitmap(at(lngOfColumn(x))));
    }
  }

  @Test
  public void stillAnswersAndCachesWellAwayFromAnyChange() {
    AtomicInteger calls = new AtomicInteger();
    GeocodeBitmapCache cache =
        GeocodeBitmapCache.create(
            oneCountry(),
            r -> {
              calls.incrementAndGet();
              return beResponse();
            },
            "COUNTRY",
            false);

    assertEquals("BE", firstCountry(cache.getFromBitmap(at(lngOfColumn(8)))));
    assertEquals("BE", firstCountry(cache.getFromBitmap(at(lngOfColumn(20)))));
    assertEquals("one lookup per colour", 1, calls.get());
  }

  private static GeocodeResponse beResponse() {
    return new GeocodeResponse(
        Collections.singletonList(
            Location.builder().type("Political").isoCountryCode2Digit("BE").distance(0d).build()));
  }

  private static String firstCountry(GeocodeResponse r) {
    return r.getLocations().get(0).getIsoCountryCode2Digit();
  }
}
