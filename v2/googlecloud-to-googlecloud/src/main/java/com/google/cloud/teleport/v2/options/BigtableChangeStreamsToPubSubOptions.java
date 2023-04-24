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

import com.google.cloud.teleport.metadata.TemplateParameter;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Validation;

/**
 * The {@link BigtableChangeStreamsToPubSubOptions} class provides the custom execution options
 * passed by the executor at the command-line.
 */
public interface BigtableChangeStreamsToPubSubOptions extends DataflowPipelineOptions {
    
    @TemplateParameter.Text(
    order = 1,
    description = "Cloud Bigtable instance ID",
    helpText = "The Cloud Bigtable instance to read change streams from.")
    @Validation.Required
    String getBigtableInstanceId();

    void setBigtableInstanceId(String value);

    @TemplateParameter.Text(
        order = 2,
        description = "Cloud Bigtable table ID",
        helpText = "The Cloud Bigtable table to read change streams from.")
    @Validation.Required
    String getBigtableTableId();

    void setBigtableTableId(String value);

    @TemplateParameter.Text(
        order = 3,
        description = "Cloud Bigtable application profile ID",
        helpText = "The application profile is used to distinguish workload in Cloud Bigtable")
    @Validation.Required
    String getBigtableAppProfileId();

    void setBigtableAppProfileId(String value);

    @TemplateParameter.Text(
        order = 4,
        description = "PubSub Topic",
        helpText = "The PubSub topic to publish a message to.")
    @Validation.Required
    String getPubsubTopic();

    void setPubsubTopic(String value);

    @TemplateParameter.DateTime(
        order = 5,
        optional = true,
        description = "The timestamp to read change streams from",
        helpText =
            "The starting DateTime, inclusive, to use for reading change streams "
                + "(https://tools.ietf.org/html/rfc3339). For example, 2022-05-05T07:59:59Z. Defaults to the "
                + "timestamp when the pipeline starts.")
    @Default.String("")
    String getStartTimestamp();

    void setStartTimestamp(String startTimestamp);

    @TemplateParameter.Text(
        order = 6,
        optional = true,
        description = "Cloud Bigtable column families to ignore",
        helpText = "A comma-separated list of column family names changes to which won't be captured")
    @Default.String("")
    String getIgnoreColumnFamilies();
  
    void setIgnoreColumnFamilies(String value);
  
    @TemplateParameter.Text(
        order = 7,
        optional = true,
        description = "Cloud Bigtable columns to ignore",
        helpText = "A comma-separated list of column names changes to which won't be captured")
    @Default.String("")
    String getIgnoreColumns();
  
    void setIgnoreColumns(String value);

    @TemplateParameter.Text(
        order = 8,
        optional = true,
        description = "Cloud Bigtable metadata table ID",
        helpText =
            "The Cloud Bigtable change streams connector metadata table ID to use. If not "
                + "provided, a Cloud Bigtable change streams connector metadata table will automatically be "
                + "created during the pipeline flow. This parameter must be provided when updating an "
                + "existing pipeline and should not be provided otherwise.")
    @Default.String("__change_stream_md_table")
    String getBigtableMetadataTableTableId();
  
    void setBigtableMetadataTableTableId(String value);

    @TemplateParameter.Text(
        order = 9,
        optional = true,
        description = "The encoding of the message written into PubSub",
        helpText = "The format of the message to be written into PubSub. Allowed formats are Binary and JSON Text.")
    @Default.String("JSON")
    String getMessageEncoding();
  
    void setMessageEncoding(String value);

    @TemplateParameter.Text(
        order = 10,
        optional = true,
        description = "The format of the message written into PubSub",
        helpText = "The message format chosen for outputting data to PubSub. Allowed formats are AVRO, Protocol Buffer and JSON Text.")
    @Default.String("JSON")
    String getMessageFormat();
  
    void setMessageFormat(String value);

    @TemplateParameter.GcsWriteFolder(
        order = 11,
        optional = true,
        description = "Dead letter queue directory",
        helpText =
            "The file path to store any unprocessed records with the reason they failed to be processed. Default is a directory under the Dataflow job's temp location. The default value is enough under most conditions.")
    @Default.String("")
    String getDlqDirectory();

    void setDlqDirectory(String value);

    @TemplateParameter.Integer(
        order = 12,
        optional = true,
        description = "Dead letter queue retry minutes",
        helpText = "The number of minutes between dead letter queue retries. Defaults to 10.")
    @Default.Integer(10)
    Integer getDlqRetryMinutes();

    void setDlqRetryMinutes(Integer value);

    @TemplateParameter.Integer(
        order = 13,
        optional = true,
        description = "Dead letter maximum retries",
        helpText = "The number of attempts to process change stream mutations. Defaults to 5.")
    @Default.Integer(5)
    Integer getDlqMaxRetries();

    void setDlqMaxRetries(Integer value);
}