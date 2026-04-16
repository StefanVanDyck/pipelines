package au.org.ala.pipelines.options;

import org.apache.beam.sdk.io.aws2.options.S3Options;
import org.gbif.pipelines.common.beam.options.InterpretationPipelineOptions;

/** Options for running DwCA to Verbatim AVRO pipelines. */
public interface DwcaToVerbatimPipelineOptions extends InterpretationPipelineOptions, S3Options {}
