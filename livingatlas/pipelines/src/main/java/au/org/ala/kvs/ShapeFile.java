package au.org.ala.kvs;

import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;

/** DTO for a shape file. This is mapped to configuration in pipelines.yaml. */
@AllArgsConstructor
@Data
public class ShapeFile implements Serializable {
  /** Path to the shape file */
  String path;

  /** The name field to use from the shape file. */
  String field;

  /** URL to source of the shapefile */
  String source;

  /** Intersect buffer 0.1 = 11km, 0.135 = 15km, 0.18 = 20km */
  Double intersectBuffer = 0.18;

  /**
   * Border tolerance in degrees, 0.1 = approx 11km, double the tolerance the GBIF geocode service
   * applies. Countries within this distance of the point are returned as additional candidates, so
   * a record near a border matches either side instead of being flagged
   * COUNTRY_COORDINATE_MISMATCH. Set to 0 to disable. Raising this above 0.15 also needs
   * SAFE_RADIUS_PIXELS raised in GeocodeBitmapCache, or the bitmap cache will serve a border answer
   * country-wide.
   */
  Double borderBuffer = 0.1;

  /** Intersect mapping to allow intersected values to mapped to different values e.g. CX -> AU * */
  Map<String, String> intersectMapping;
}
