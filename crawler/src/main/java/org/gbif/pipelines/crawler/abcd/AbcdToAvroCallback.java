package org.gbif.pipelines.crawler.abcd;

import java.util.Objects;
import java.util.concurrent.ExecutorService;

import org.gbif.api.model.pipelines.StepType;
import org.gbif.common.messaging.AbstractMessageCallback;
import org.gbif.common.messaging.api.MessagePublisher;
import org.gbif.common.messaging.api.messages.PipelinesAbcdMessage;
import org.gbif.common.messaging.api.messages.PipelinesVerbatimMessage;
import org.gbif.common.messaging.api.messages.PipelinesXmlMessage;
import org.gbif.pipelines.crawler.PipelinesCallback;
import org.gbif.pipelines.crawler.StepHandler;
import org.gbif.pipelines.crawler.xml.XmlToAvroCallback;
import org.gbif.pipelines.crawler.xml.XmlToAvroConfiguration;
import org.gbif.registry.ws.client.pipelines.PipelinesHistoryWsClient;

import org.apache.curator.framework.CuratorFramework;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;

/**
 * Call back which is called when the {@link PipelinesXmlMessage} is received.
 */
@Slf4j
public class AbcdToAvroCallback extends AbstractMessageCallback<PipelinesAbcdMessage>
    implements StepHandler<PipelinesAbcdMessage, PipelinesVerbatimMessage> {

  private final CuratorFramework curator;
  private final XmlToAvroConfiguration config;
  private final ExecutorService executor;
  private final MessagePublisher publisher;
  private final PipelinesHistoryWsClient client;

  public AbcdToAvroCallback(CuratorFramework curator, XmlToAvroConfiguration config, ExecutorService executor,
      MessagePublisher publisher, PipelinesHistoryWsClient client) {
    this.curator = curator;
    this.config = config;
    this.executor = executor;
    this.publisher = publisher;
    this.client = client;
  }

  @Override
  public void handleMessage(PipelinesAbcdMessage message) {
    PipelinesCallback.<PipelinesAbcdMessage, PipelinesVerbatimMessage>builder()
        .client(client)
        .config(config)
        .curator(curator)
        .stepType(StepType.ABCD_TO_VERBATIM)
        .publisher(publisher)
        .message(message)
        .handler(this)
        .build()
        .handleMessage();
  }

  @Override
  public Runnable createRunnable(PipelinesAbcdMessage message) {
    return XmlToAvroCallback.createRunnable(
        config,
        message.getDatasetUuid(),
        message.getAttempt().toString(),
        executor,
        XmlToAvroCallback.SKIP_RECORDS_CHECK
    );
  }

  @Override
  public PipelinesVerbatimMessage createOutgoingMessage(PipelinesAbcdMessage message) {
    Objects.requireNonNull(message.getEndpointType(), "endpointType can't be NULL!");

    if (message.getPipelineSteps().isEmpty()) {
      message.setPipelineSteps(Sets.newHashSet(
          StepType.ABCD_TO_VERBATIM.name(),
          StepType.VERBATIM_TO_INTERPRETED.name(),
          StepType.INTERPRETED_TO_INDEX.name(),
          StepType.HDFS_VIEW.name()
      ));
    }

    return new PipelinesVerbatimMessage(
        message.getDatasetUuid(),
        message.getAttempt(),
        config.interpretTypes,
        message.getPipelineSteps(),
        message.getEndpointType()
    );
  }

  @Override
  public boolean isMessageCorrect(PipelinesAbcdMessage message) {
    return message.isModified();
  }
}