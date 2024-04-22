package org.gbif.pipelines.transforms.table;

import static org.gbif.api.model.pipelines.InterpretationType.RecordType.RESOURCE_RELATIONSHIP_TABLE;
import static org.gbif.pipelines.common.PipelinesVariables.Metrics.RESOURCE_RELATIONSHIP_TABLE_RECORDS_COUNT;

import java.util.Set;
import lombok.Builder;
import org.apache.beam.sdk.values.PCollectionView;
import org.apache.beam.sdk.values.TupleTag;
import org.gbif.pipelines.core.converters.ResourceRelationshipTableConverter;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.IdentifierRecord;
import org.gbif.pipelines.io.avro.MetadataRecord;
import org.gbif.pipelines.io.avro.extension.dwc.ResourceRelationshipTable;

public class ResourceRelationshipTableTransform extends TableTransform<ResourceRelationshipTable> {

  @Builder
  public ResourceRelationshipTableTransform(
      TupleTag<ExtendedRecord> extendedRecordTag,
      TupleTag<IdentifierRecord> identifierRecordTag,
      PCollectionView<MetadataRecord> metadataView,
      String path,
      Set<String> types) {
    super(
        ResourceRelationshipTable.class,
        RESOURCE_RELATIONSHIP_TABLE,
        ResourceRelationshipTableTransform.class.getName(),
        RESOURCE_RELATIONSHIP_TABLE_RECORDS_COUNT,
        ResourceRelationshipTableConverter::convert);
    this.setExtendedRecordTag(extendedRecordTag)
        .setIdentifierRecordTag(identifierRecordTag)
        .setMetadataRecord(metadataView)
        .setPath(path)
        .setTypes(types);
  }
}
