package au.org.ala.pipelines.options;

import org.apache.beam.sdk.io.aws2.options.S3Options;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;

/** Options for piupelines that run against sampling services. */
public interface SamplingPipelineOptions extends AllDatasetsPipelinesOptions, S3Options {

  @Description("Keep latlng export CSVs")
  @Default.Boolean(false)
  Boolean getKeepLatLngExports();

  void setKeepLatLngExports(Boolean keepLatLngExports);

  @Description("Keep download sampling CSVs")
  @Default.Boolean(false)
  Boolean getKeepSamplingDownloads();

  void setKeepSamplingDownloads(Boolean keepSamplingDownloads);

  @Description("Keep download sampling CSVs")
  @Default.Boolean(true)
  Boolean getDeleteSamplingForNewLayers();

  void setDeleteSamplingForNewLayers(Boolean deleteSamplingForNewLayers);
}
