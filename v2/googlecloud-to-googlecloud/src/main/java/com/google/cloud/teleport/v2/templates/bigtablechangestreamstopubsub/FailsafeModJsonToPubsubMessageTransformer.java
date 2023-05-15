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
import com.google.cloud.teleport.v2.templates.bigtablechangestreamstopubsub.model.Mod;
import com.google.cloud.teleport.v2.templates.bigtablechangestreamstopubsub.schemautils.PubSubUtils;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import com.google.cloud.teleport.v2.coders.FailsafeElementCoder;
import com.google.cloud.teleport.v2.values.FailsafeElement;
import java.io.Serializable;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionTuple;
import org.apache.beam.sdk.values.TupleTag;
import org.apache.beam.sdk.values.TupleTagList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.beam.vendor.guava.v26_0_jre.com.google.common.base.Throwables;
import java.io.IOException;

/**
 * Class {@link FailsafeModJsonToPubsubMessageTransformer} provides methods that convert a
 * {@link Mod} JSON string wrapped in {@link FailsafeElement} to a {@link PubsubMessage}.
 */
public final class FailsafeModJsonToPubsubMessageTransformer {

  /**
   * Primary class for taking a {@link FailsafeElement} {@link Mod} JSON input and converting to a
   * {@link PubsubMessage}.
   */
  public static class FailsafeModJsonToPubsubMessage
      extends PTransform<PCollection<FailsafeElement<String, String>>, PCollectionTuple> {

    private final PubSubUtils pubSubUtils;
    private static final Logger LOG = LoggerFactory.getLogger(FailsafeModJsonToPubsubMessageTransformer.class);

    private static final String NATIVE_CLIENT = "native_client";
    private static final String PUBSUBIO = "pubsubio";


    /**
     * The tag for the main output of the transformation.
     */
    public TupleTag<PubsubMessage> transformOut = new TupleTag<>() {
    };

    /**
     * The tag for the dead letter output of the transformation.
     */
    public TupleTag<FailsafeElement<String, String>> transformDeadLetterOut =
            new TupleTag<>() {
            };

    private final FailsafeModJsonToPubsubMessageOptions failsafeModJsonToPubsubMessageOptions;

    public FailsafeModJsonToPubsubMessage(
            PubSubUtils pubSubUtils,
            FailsafeModJsonToPubsubMessageOptions failsafeModJsonToPubsubMessageOptions) {
      this.pubSubUtils = pubSubUtils;
      this.failsafeModJsonToPubsubMessageOptions = failsafeModJsonToPubsubMessageOptions;
    }

    public PCollectionTuple expand(PCollection<FailsafeElement<String, String>> input) {
      PCollectionTuple out =
              input.apply(
                      ParDo.of(
                                      new FailsafeModJsonToPubsubMessageFn(
                                              pubSubUtils,
                                              transformOut,
                                              transformDeadLetterOut))
                              .withOutputTags(transformOut, TupleTagList.of(transformDeadLetterOut)));
      out.get(transformDeadLetterOut).setCoder(failsafeModJsonToPubsubMessageOptions.getCoder());
      return out;
    }

    /**
     * The {@link FailsafeModJsonToPubsubMessageFn} converts a {@link Mod} JSON string wrapped in {@link
     * FailsafeElement} to a {@link PubsubMesage}.
     */
    public static class FailsafeModJsonToPubsubMessageFn
            extends DoFn<FailsafeElement<String, String>, PubsubMessage> {
      private final PubSubUtils pubSubUtils;
      public TupleTag<PubsubMessage> transformOut;
      public TupleTag<FailsafeElement<String, String>> transformDeadLetterOut;

      public FailsafeModJsonToPubsubMessageFn(
              PubSubUtils pubSubUtils,
              TupleTag<PubsubMessage> transformOut,
              TupleTag<FailsafeElement<String, String>> transformDeadLetterOut) {
        this.pubSubUtils = pubSubUtils;
        this.transformOut = transformOut;
        this.transformDeadLetterOut = transformDeadLetterOut;
      }

      @Setup
      public void setUp() {
      }

      @Teardown
      public void tearDown() {
      }

      @ProcessElement
      public void processElement(ProcessContext context) {
        FailsafeElement<String, String> failsafeModJsonString = context.element();
        if (failsafeModJsonString == null) {
          return;
        }

        try {
          PubsubMessage pubSubMessage = modJsonStringToPubsubMessage(failsafeModJsonString.getPayload());
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

      private PubsubMessage modJsonStringToPubsubMessage(String modJsonString)
              throws IOException, InterruptedException {
        TopicName topicName = TopicName.of(pubSubUtils.getDestination().getPubSubProject(),
                pubSubUtils.getDestination().getPubSubTopic());


        byte[] encodedRecord = modJsonString.getBytes();
        PubsubMessage pubsubMessage = new PubsubMessage(encodedRecord, null);
        return pubsubMessage;

      }
    }
  }
  /**
   * {@link FailsafeModJsonToPubsubMessageOptions} provides options to initialize {@link
   * FailsafeModJsonToPubsubMessageTransformer}.
   */
  @AutoValue
  public abstract static class FailsafeModJsonToPubsubMessageOptions implements Serializable {

    public abstract FailsafeElementCoder<String, String> getCoder();

    static Builder builder() {
      return new AutoValue_FailsafeModJsonToPubsubMessageTransformer_FailsafeModJsonToPubsubMessageOptions
              .Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder setCoder(FailsafeElementCoder<String, String> coder);

      abstract FailsafeModJsonToPubsubMessageOptions build();
    }
  }
}