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

import com.google.api.services.bigquery.model.TableRow;
import com.google.auto.value.AutoValue;
import com.google.cloud.pubsublite.proto.PubSubMessage;
import com.google.cloud.teleport.v2.DataChangeRecord;
import com.google.cloud.teleport.v2.templates.bigtablechangestreamstopubsub.model.Mod;
import com.google.cloud.teleport.v2.templates.bigtablechangestreamstopubsub.schemautils.PubSubUtils;
import com.google.cloud.teleport.v2.transforms.PublishToPubSubDoFn;
import com.google.cloud.teleport.v2.transforms.WriteDataChangeRecordsToAvro.DataChangeRecordToAvroFn;
import com.google.cloud.teleport.v2.transforms.WriteDataChangeRecordsToJson.DataChangeRecordToJsonTextFn;
import com.google.cloud.teleport.v2.values.FailsafeElement;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.CoderException;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
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

/**
 * Class {@link FailsafeModJsonToPubSubMessageTransformer} provides methods that convert a
 * {@link Mod} JSON string wrapped in {@link FailsafeElement} to a {@link TableRow}.
 */
public final class FailsafeModJsonToPubSubMessageTransformer {

  /**
   * Primary class for taking a {@link FailsafeElement} {@link Mod} JSON input and converting to a
   * {@link TableRow}.
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


    public PCollectionTuple expand(PCollection<FailsafeElement<String, String>> records) {
      PCollection<byte[]> encodedRecords = null;

      /*
       * Calls appropriate class Builder to performs PTransform based on user provided File Format.
       */
      switch (messageFormat()) {
        case "AVRO":
          AvroCoder<DataChangeRecord> coder =
              AvroCoder.of(com.google.cloud.teleport.v2.DataChangeRecord.class);
          encodedRecords =
              records
                  .apply(
                      "Write DataChangeRecord into AVRO",
                      MapElements.via(new DataChangeRecordToAvroFn()))
                  .apply(
                      "Convert encoded DataChangeRecord in AVRO to bytes to be saved into"
                          + " PubsubMessage.",
                      ParDo.of(
                          // Convert encoded DataChangeRecord in AVRO to bytes that can be saved into
                          // PubsubMessage.
                          new DoFn<com.google.cloud.teleport.v2.DataChangeRecord, byte[]>() {
                            @ProcessElement
                            public void processElement(ProcessContext context) {
                              com.google.cloud.teleport.v2.DataChangeRecord record =
                                  context.element();
                              byte[] encodedRecord = null;
                              try {
                                encodedRecord = CoderUtils.encodeToByteArray(coder, record);
                              } catch (CoderException ce) {
                                throw new RuntimeException(ce);
                              }
                              context.output(encodedRecord);
                            }
                          }));
          sendToPubSub(encodedRecords);

          break;
        case "JSON":
          encodedRecords =
              records
                  .apply(
                      "Write DataChangeRecord into JSON",
                      MapElements.via(new DataChangeRecordToJsonTextFn()))
                  .apply(
                      "Convert encoded DataChangeRecord in JSON to bytes to be saved into"
                          + " PubsubMessage.",
                      ParDo.of(
                          new DoFn<String, byte[]>() {
                            @ProcessElement
                            public void processElement(ProcessContext context) {
                              String record = context.element();
                              byte[] encodedRecord = record.getBytes();
                              context.output(encodedRecord);
                            }
                          }));
          sendToPubSub(encodedRecords);
          break;

        default:
          final String errorMessage =
              "Invalid output format:"
                  + messageFormat()
                  + ". Supported output formats: JSON, AVRO";
          LOG.info(errorMessage);
          throw new IllegalArgumentException(errorMessage);
      }
      return encodedRecords;
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
     * FailsafeElement} to a {@link TableRow}.
     */
    public static class FailsafeModJsonToTableRowFn
        extends DoFn<FailsafeElement<String, String>, TableRow> {
      private final PubSubUtils pubSubUtils;
      public TupleTag<TableRow> transformOut;
      public TupleTag<FailsafeElement<String, String>> transformDeadLetterOut;

      public FailsafeModJsonToTableRowFn(
          PubSubUtils pubSubUtils,
          TupleTag<TableRow> transformOut,
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
          TableRow tableRow = modJsonStringToTableRow(failsafeModJsonString.getPayload());
          if (tableRow == null) {
            // TableRow was not generated because pipeline configuration requires ignoring some
            // column / column families
            return;
          }

          for (String ignoreField : ignoreFields) {
            tableRow.remove(ignoreField);
          }

          context.output(tableRow);
        } catch (Exception e) {
          context.output(
              transformDeadLetterOut,
              FailsafeElement.of(failsafeModJsonString)
                  .setErrorMessage(e.getMessage())
                  .setStacktrace(Throwables.getStackTraceAsString(e)));
        }
      }

      private TableRow modJsonStringToTableRow(String modJsonString) throws Exception {
        ObjectNode modObjectNode = (ObjectNode) new ObjectMapper().readTree(modJsonString);
        for (TransientColumn transientColumn : TransientColumn.values()) {
          if (modObjectNode.has(transientColumn.getColumnName())) {
            modObjectNode.remove(transientColumn.getColumnName());
          }
        }

        TableRow tableRow = new TableRow();
        if (bigQueryUtils.setTableRowFields(
            Mod.fromJson(modObjectNode.toString()), modJsonString, tableRow)) {
          return tableRow;
        } else {
          return null;
        }
      }
    }
  }

    /** Method that takes in byte arrays and outputs PubsubMessages. */
    private PCollection<PubsubMessage> convertByteArrayToPubsubMessage(
        PCollection<byte[]> encodedRecords) {
      PCollection<PubsubMessage> messageCollection =
          encodedRecords.apply(
              ParDo.of(
                  new DoFn<byte[], PubsubMessage>() {
                    @ProcessElement
                    public void processElement(ProcessContext context) {
                      byte[] encodedRecord = context.element();
                      PubsubMessage pubsubMessage = new PubsubMessage(encodedRecord, null);
                      context.output(pubsubMessage);
                    }
                  }));
      return messageCollection;
    }

    /** Builder for {@link FileFormatFactorySpannerChangeStreamsToPubSub}. */
    @AutoValue.Builder
    public abstract static class FailsafeModJsonToPubSubMessageOptions {

      public abstract FileFormatFactorySpannerChangeStreamsToPubSub.WriteToPubSubBuilder setOutputDataFormat(String value);

      public abstract FileFormatFactorySpannerChangeStreamsToPubSub.WriteToPubSubBuilder setProjectId(String value);

      public abstract FileFormatFactorySpannerChangeStreamsToPubSub.WriteToPubSubBuilder setPubsubAPI(String value);

      public abstract FileFormatFactorySpannerChangeStreamsToPubSub.WriteToPubSubBuilder setPubsubTopicName(String value);

      abstract FileFormatFactorySpannerChangeStreamsToPubSub autoBuild();

      public FileFormatFactorySpannerChangeStreamsToPubSub build() {
        return autoBuild();
      }
    }


}