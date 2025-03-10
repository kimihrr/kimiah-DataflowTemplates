/*
 * Copyright (C) 2022 Google LLC
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
package com.google.cloud.teleport.v2.options;

import com.google.cloud.spanner.Options.RpcPriority;
import com.google.cloud.teleport.metadata.TemplateParameter;
import com.google.cloud.teleport.metadata.TemplateParameter.TemplateEnumOption;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Validation;

/**
 * The {@link SpannerChangeStreamsToPubSubOptions} interface provides the custom execution options
 * passed by the executor at the command-line.
 */
public interface SpannerChangeStreamsToPubSubOptions extends DataflowPipelineOptions {

  @TemplateParameter.ProjectId(
      order = 1,
      optional = true,
      description = "Spanner Project ID",
      helpText =
          "Project to read change streams from. The default for this parameter is the project "
              + "where the Dataflow pipeline is running.")
  @Default.String("")
  String getSpannerProjectId();

  void setSpannerProjectId(String projectId);

  @TemplateParameter.Text(
      order = 2,
      description = "Spanner instance ID",
      helpText = "The Spanner instance to read change streams from.")
  @Validation.Required
  String getSpannerInstanceId();

  void setSpannerInstanceId(String spannerInstanceId);

  @TemplateParameter.Text(
      order = 3,
      description = "Spanner database",
      helpText = "The Spanner database to read change streams from.")
  @Validation.Required
  String getSpannerDatabase();

  void setSpannerDatabase(String spannerDatabase);

  @TemplateParameter.Text(
      order = 4,
      optional = true,
      description = "Spanner database role",
      helpText =
          "Database role user assumes while reading from the change stream. The database role"
              + " should have required privileges to read from change stream. If a database role is"
              + " not specified, the user should have required IAM permissions to read from the"
              + " database.")
  String getSpannerDatabaseRole();

  void setSpannerDatabaseRole(String spannerDatabaseRole);

  @TemplateParameter.Text(
      order = 5,
      description = "Spanner metadata instance ID",
      helpText = "The Spanner instance to use for the change streams connector metadata table.")
  @Validation.Required
  String getSpannerMetadataInstanceId();

  void setSpannerMetadataInstanceId(String spannerMetadataInstanceId);

  @TemplateParameter.Text(
      order = 6,
      description = "Spanner metadata database",
      helpText =
          "The Spanner database to use for the change streams connector metadata table. For change"
              + " streams tracking all tables in a database, we recommend putting the metadata"
              + " table in a separate database.")
  @Validation.Required
  String getSpannerMetadataDatabase();

  void setSpannerMetadataDatabase(String spannerMetadataDatabase);

  @TemplateParameter.Text(
      order = 7,
      optional = true,
      description = "Cloud Spanner metadata table name",
      helpText =
          "The Cloud Spanner change streams connector metadata table name to use. If not provided,"
              + " a Cloud Spanner change streams connector metadata table will automatically be"
              + " created during the pipeline flow. This parameter must be provided when updating"
              + " an existing pipeline and should not be provided otherwise.")
  String getSpannerMetadataTableName();

  void setSpannerMetadataTableName(String value);

  @TemplateParameter.Text(
      order = 8,
      description = "Spanner change stream",
      helpText = "The name of the Spanner change stream to read from.")
  @Validation.Required
  String getSpannerChangeStreamName();

  void setSpannerChangeStreamName(String spannerChangeStreamName);

  @TemplateParameter.DateTime(
      order = 9,
      optional = true,
      description = "The timestamp to read change streams from",
      helpText =
          "The starting DateTime, inclusive, to use for reading change streams"
              + " (https://tools.ietf.org/html/rfc3339). For example, 2022-05-05T07:59:59Z."
              + " Defaults to the timestamp when the pipeline starts.")
  @Default.String("")
  String getStartTimestamp();

  void setStartTimestamp(String startTimestamp);

  @TemplateParameter.DateTime(
      order = 10,
      optional = true,
      description = "The timestamp to read change streams to",
      helpText =
          "The ending DateTime, inclusive, to use for reading change streams"
              + " (https://tools.ietf.org/html/rfc3339). Ex-2022-05-05T07:59:59Z. Defaults to an"
              + " infinite time in the future.")
  @Default.String("")
  String getEndTimestamp();

  void setEndTimestamp(String startTimestamp);

  @TemplateParameter.Text(
      order = 11,
      optional = true,
      description = "Cloud Spanner Endpoint to call",
      helpText = "The Cloud Spanner endpoint to call in the template. Only used for testing.",
      example = "https://spanner.googleapis.com")
  @Default.String("https://spanner.googleapis.com")
  String getSpannerHost();

  void setSpannerHost(String value);

  @TemplateParameter.Text(
      order = 12,
      optional = true,
      description = "Output data format",
      helpText =
          "The format of the output to Pub/Sub. Allowed formats are JSON, AVRO. Default is JSON.")
  @Default.String("JSON")
  String getOutputDataFormat();

  void setOutputDataFormat(String outputDataFormat);

  @TemplateParameter.Text(
      order = 13,
      optional = true,
      description = "Pub/Sub API",
      helpText =
          "Pub/Sub API used to implement the pipeline. Allowed APIs are pubsubio and native_client."
              + " Default is pubsubio. For a small QPS, native_client can achieve a smaller latency"
              + " than pubsubio. For a large QPS, pubsubio has better and more stable performance.")
  @Default.String("pubsubio")
  String getPubsubAPI();

  void setPubsubAPI(String pubsubAPI);

  @TemplateParameter.Text(
      order = 14,
      description = "The output Pub/Sub topic",
      helpText = "The Pub/Sub topic to publish PubsubMessage.")
  @Validation.Required
  String getPubsubTopic();

  void setPubsubTopic(String pubsubTopic);

  @TemplateParameter.Enum(
      order = 15,
      enumOptions = {
        @TemplateEnumOption("LOW"),
        @TemplateEnumOption("MEDIUM"),
        @TemplateEnumOption("HIGH")
      },
      optional = true,
      description = "Priority for Spanner RPC invocations",
      helpText =
          "The request priority for Cloud Spanner calls. The value must be one of:"
              + " [HIGH,MEDIUM,LOW].")
  @Default.Enum("HIGH")
  RpcPriority getRpcPriority();

  void setRpcPriority(RpcPriority rpcPriority);
}
