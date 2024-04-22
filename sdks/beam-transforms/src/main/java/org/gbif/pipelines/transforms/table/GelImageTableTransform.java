package org.gbif.pipelines.transforms.table;

import static org.gbif.api.model.pipelines.InterpretationType.RecordType.GEL_IMAGE_TABLE;
import static org.gbif.pipelines.common.PipelinesVariables.Metrics.GEL_IMAGE_TABLE_RECORDS_COUNT;

import java.util.Set;
import lombok.Builder;
import org.apache.beam.sdk.values.PCollectionView;
import org.apache.beam.sdk.values.TupleTag;
import org.gbif.pipelines.core.converters.GelImageTableConverter;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.IdentifierRecord;
import org.gbif.pipelines.io.avro.MetadataRecord;
import org.gbif.pipelines.io.avro.extension.ggbn.GelImageTable;

public class GelImageTableTransform extends TableTransform<GelImageTable> {

  @Builder
  public GelImageTableTransform(
      TupleTag<ExtendedRecord> extendedRecordTag,
      TupleTag<IdentifierRecord> identifierRecordTag,
      PCollectionView<MetadataRecord> metadataView,
      String path,
      Set<String> types) {
    super(
        GelImageTable.class,
        GEL_IMAGE_TABLE,
        GelImageTableTransform.class.getName(),
        GEL_IMAGE_TABLE_RECORDS_COUNT,
        GelImageTableConverter::convert);
    this.setExtendedRecordTag(extendedRecordTag)
        .setIdentifierRecordTag(identifierRecordTag)
        .setMetadataRecord(metadataView)
        .setPath(path)
        .setTypes(types);
  }
}
