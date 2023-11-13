package org.gbif.pipelines.core.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClusteringRelationshipConfig implements Serializable {

  private static final long serialVersionUID = -3902149702064815655L;

  private int relationshipTableSalt;
  private String relationshipTableName;
  private int retryMaxAttempts;
  private long retryDuration;
}
