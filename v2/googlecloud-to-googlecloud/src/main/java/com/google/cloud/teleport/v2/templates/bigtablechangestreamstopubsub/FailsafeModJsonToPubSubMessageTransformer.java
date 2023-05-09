/*
 * Copyright (C) 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.teleport.v2.templates.bigtablechangestreamstopubsub;

import com.google.auto.value.AutoValue;
import com.google.cloud.pubsublite.proto.PubSubMessage;
import com.google.cloud.teleport.v2.templates.bigtablechangestreamstopubsub.model.Mod;
import com.google.cloud.teleport.v2.templates.bigtablechangestreamstopubsub.schemautils.PubSubUtils;
import com.google.pubsub.v1.PubsubMessage;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.pubsub.v1.TopicName;
import com.google.cloud.teleport.v2.transforms.PublishToPubSubDoFn;
import com.google.cloud.teleport.v2.values.FailsafeElement;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.util.CoderUtils;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionTuple;
import org.apache.beam.sdk.values.TupleTag;
import org.apache.beam.sdk.values.TupleTagList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.beam.vendor.guava.v26_0_jre.com.google.common.base.Throwables;
import java.io.IOException;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.rpc.ApiException;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Class {@link FailsafeModJsonToPubSubMessageTransformer} provides methods that convert a
 * {@link Mod} JSON string wrapped in {@link FailsafeElement} to a {@link PubSubMessage}.
 */
public final class FailsafeModJsonToPubSubMessageTransformer {

  /**
   * Primary class for taking a {@link FailsafeElement} {@link Mod} JSON input and converting to a
   * {@link PubSubMessage}.
   */
  public static class FailsafeModJsonToPubSubMessage
      extends PTransform<PCollection<FailsafeElement<String, String>>, PCollectionTuple> {

    private final PubSubUtils pubSubUtils;
    private static final Logger LOG = LoggerFactory.getLogger(FailsafeModJsonToPubSubMessageTransformer.class);

    private static final String NATIVE_CLIENT = "native_client";
    private static final String PUBSUBIO = "pubsubio";


    /** The tag for the main output of the transformation. */
    public TupleTag<PubSubMessage> transformOut = new TupleTag<>() {
    };

    /** The tag for the dead letter output of the transformation. */
    public TupleTag<FailsafeElement<String, String>> transformDeadLetterOut =
        new TupleTag<>() {
        };

    private final FailsafeModJsonToPubSubMessageOptions failsafeModJsonToPubSubMessageOptions;

    public FailsafeModJsonToPubSubMessage(
        PubSubUtils pubSubUtils,
        FailsafeModJsonToPubSubMessageOptions failsafeModJsonToPubSubMessageOptions) {
      this.pubSubUtils = pubSubUtils;
      this.failsafeModJsonToPubSubMessageOptions = failsafeModJsonToPubSubMessageOptions;
    }

    public PCollectionTuple expand(PCollection<FailsafeElement<String, String>> input) {
      PCollectionTuple out =
          input.apply(
              ParDo.of(
                      new FailsafeModJsonToTableRowFn(
                          pubSubUtils,
                          transformOut,
                          transformDeadLetterOut))
                  .withOutputTags(transformOut, TupleTagList.of(transformDeadLetterOut)));
      out.get(transformDeadLetterOut).setCoder(failsafeModJsonToPubSubMessageOptions.getCoder());
      return out;
    }

    /**
     * The {@link FailsafeModJsonToTableRowFn} converts a {@link Mod} JSON string wrapped in {@link
     * FailsafeElement} to a {@link PubSubMesage}.
     */
    public static class FailsafeModJsonToTableRowFn
        extends DoFn<FailsafeElement<String, String>, PubSubMessage> {
      private final PubSubUtils pubSubUtils;
      public TupleTag<PubSubMessage> transformOut;
      public TupleTag<FailsafeElement<String, String>> transformDeadLetterOut;

      public FailsafeModJsonToTableRowFn(
          PubSubUtils pubSubUtils,
          TupleTag<PubSubMessage> transformOut,
          TupleTag<FailsafeElement<String, String>> transformDeadLetterOut) {
        this.pubSubUtils = pubSubUtils;
        this.transformOut = transformOut;
        this.transformDeadLetterOut = transformDeadLetterOut;
      }

      @Setup
      public void setUp() {}

      @Teardown
      public void tearDown() {}

      @ProcessElement
      public void processElement(ProcessContext context) {
        FailsafeElement<String, String> failsafeModJsonString = context.element();
        if (failsafeModJsonString == null) {
          return;
        }

        try {
          PubSubMessage pubSubMessage = modJsonStringToPubSubMessage(failsafeModJsonString.getPayload());
          if (pubSubMessage == null) {
            return;
          }

          context.output(pubSubMessage);
        } catch (Exception e) {
          context.output(
              transformDeadLetterOut,
              FailsafeElement.of(failsafeModJsonString)
                  .setErrorMessage(e.getMessage())
                  .setStacktrace(Throwables.getStackTraceAsString(e)));
        }
      }

      private PubSubMessage modJsonStringToPubSubMessage(String modJsonString)
          throws IOException, InterruptedException  {
        TopicName topicName = TopicName.of(pubSubUtils.source.getBigtableProjectId(), pubSubUtils.destination.pubSubTopic);
        Publisher publisher = null;
        switch (pubSubUtils.destination.messageFormat) {
          case "AVRO":
            String message = modJsonString;
          case "Protobuf":
            String message = modJsonString;
        }

        try {
          // Create a publisher instance with default settings bound to the topic
          publisher = Publisher.newBuilder(topicName).build();

          ByteString data = ByteString.copyFromUtf8(modJsonString);
          PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(data).build();

          // Once published, returns a server-assigned message id (unique within the topic)
          ApiFuture<String> future = publisher.publish(pubsubMessage);

          // Add an asynchronous callback to handle success / failure
          ApiFutures.addCallback(
              future,
              new ApiFutureCallback<String>() {

                @Override
                public void onFailure(Throwable throwable) {
                  if (throwable instanceof ApiException) {
                    ApiException apiException = ((ApiException) throwable);
                    // details on the API exception
                    System.out.println(apiException.getStatusCode().getCode());
                    System.out.println(apiException.isRetryable());
                  }
                  System.out.println("Error publishing message : " + modJsonString);
                }

                @Override
                public void onSuccess(String messageId) {
                  // Once published, returns server-assigned message ids (unique within the topic)
                  System.out.println("Published message ID: " + messageId);
                }
              },
              MoreExecutors.directExecutor());
        } finally {
          if (publisher != null) {
            // When finished with the publisher, shutdown to free up resources.
            publisher.shutdown();
            publisher.awaitTermination(1, TimeUnit.MINUTES);
          }
        }
      }
   }

    /** Builder for {@link FileFormatFactorySpannerChangeStreamsToPubSub}. */
    @AutoValue.Builder
    public abstract static class FailsafeModJsonToPubSubMessageOptions {

      public abstract ImmutableSet<String> getIgnoreFields();

      public abstract FailsafeElementCoder<String, String> getCoder();

      static Builder builder() {
        return new AutoValue_FailsafeModJsonToChangelogTableRowTransformer_FailsafeModJsonToTableRowOptions
            .Builder();
      }

      @AutoValue.Builder
      abstract static class Builder {
        abstract Builder setIgnoreFields(ImmutableSet<String> ignoreFields);

        abstract Builder setCoder(FailsafeElementCoder<String, String> coder);

        abstract FailsafeModJsonToTableRowOptions build();
      }
    }


}