package au.org.ala.pipelines.beam;

import java.util.Optional;
import org.apache.beam.sdk.io.aws2.options.S3Options;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.gbif.pipelines.common.beam.options.InterpretationPipelineOptions;

public interface ALAInterpretationPipelineOptions extends InterpretationPipelineOptions, S3Options {

  @Description("Events pipeline processing enabled")
  @Default.Boolean(true)
  boolean isEventsEnabled();

  void setEventsEnabled(boolean eventsEnabled);

  @Description("Match using raw taxon ID")
  Optional<Boolean> getMatchOnTaxonId();

  void setMatchOnTaxonId(boolean matchOnTaxonId);
}
