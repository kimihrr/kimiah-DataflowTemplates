

package com.google.cloud.teleport.v2.templates.bigtablechangestreamstopubsub;

import com.google.cloud.teleport.metadata.Template;
import com.google.cloud.teleport.v2.options.BigtableChangeStreamsToPubSubOptions;
import com.google.cloud.teleport.metadata.TemplateCategory;



@Template(
    name = "Spanner_Change_Streams_to_PubSub",
    category = TemplateCategory.STREAMING,
    displayName = "Cloud Spanner change streams to Pub/Sub",
    description =
        "Streaming pipeline. Streams Spanner change stream data records and writes them into a"
            + " Pub/Sub topic using Dataflow Runner V2.",
    optionsClass = BigtableChangeStreamsToPubSubOptions.class,
    flexContainerName = "spanner-changestreams-to-pubsub",
    documentation =
        "https://cloud.google.com/dataflow/docs/guides/templates/provided/cloud-spanner-change-streams-to-pubsub",
    contactInformation = "https://cloud.google.com/support")

public class BigtableChangeStreamsToPubSub {

    /** String/String Coder for {@link FailsafeElement}. */
    public static final FailsafeElementCoder<String, String> FAILSAFE_ELEMENT_CODER =
    FailsafeElementCoder.of(StringUtf8Coder.of(), StringUtf8Coder.of());

    private static final Logger LOG = LoggerFactory.getLogger(SpannerChangeStreamsToBigQuery.class);

    // Max number of deadletter queue retries.
    private static final int DLQ_MAX_RETRIES = 5;

    private static final String USE_RUNNER_V2_EXPERIMENT = "use_runner_v2";

    /**
    * Main entry point for executing the pipeline.
    *
    * @param args The command-line arguments to the pipeline.
    */
    public static void main(String[] args) {
    UncaughtExceptionLogger.register();

    LOG.info("Starting to replicate change records from Spanner change streams to BigQuery");

    SpannerChangeStreamsToBigQueryOptions options =
        PipelineOptionsFactory.fromArgs(args)
            .withValidation()
            .as(SpannerChangeStreamsToBigQueryOptions.class);

    run(options);
    }

    private static void validateOptions(SpannerChangeStreamsToBigQueryOptions options) {
    if (options.getDlqRetryMinutes() <= 0) {
    throw new IllegalArgumentException("dlqRetryMinutes must be positive.");
    }

    BigQueryIOUtils.validateBQStorageApiOptionsStreaming(options);
    }

    private static void setOptions(SpannerChangeStreamsToBigQueryOptions options) {
    options.setStreaming(true);
    options.setEnableStreamingEngine(true);

    // Add use_runner_v2 to the experiments option, since change streams connector is only supported
    // on Dataflow runner v2.
    List<String> experiments = options.getExperiments();
    if (experiments == null) {
    experiments = new ArrayList<>();
    }
    if (!experiments.contains(USE_RUNNER_V2_EXPERIMENT)) {
    experiments.add(USE_RUNNER_V2_EXPERIMENT);
    }
    options.setExperiments(experiments);
    }

    /**
    * Runs the pipeline with the supplied options.
    *
    * @param options The execution parameters to the pipeline.
    * @return The result of the pipeline execution.
    */
    public static PipelineResult run(SpannerChangeStreamsToBigQueryOptions options) {
    setOptions(options);
    validateOptions(options);

    /**
     * Stages: 1) Read {@link DataChangeRecord} from change stream. 2) Create {@link
     * FailsafeElement} of {@link Mod} JSON and merge from: - {@link DataChangeRecord}. - GCS Dead
     * letter queue. 3) Convert {@link Mod} JSON into {@link TableRow} by reading from Spanner at
     * commit timestamp. 4) Append {@link TableRow} to BigQuery. 5) Write Failures from 2), 3) and
     * 4) to GCS dead letter queue.
     */
    Pipeline pipeline = Pipeline.create(options);
    DeadLetterQueueManager dlqManager = buildDlqManager(options);
    String spannerProjectId = getSpannerProjectId(options);

    String dlqDirectory = dlqManager.getRetryDlqDirectoryWithDateTime();
    String tempDlqDirectory = dlqManager.getRetryDlqDirectory() + "tmp/";

    // Retrieve and parse the startTimestamp and endTimestamp.
    Timestamp startTimestamp =
        options.getStartTimestamp().isEmpty()
            ? Timestamp.now()
            : Timestamp.parseTimestamp(options.getStartTimestamp());
    Timestamp endTimestamp =
        options.getEndTimestamp().isEmpty()
            ? Timestamp.MAX_VALUE
            : Timestamp.parseTimestamp(options.getEndTimestamp());

    SpannerConfig spannerConfig =
        SpannerConfig.create()
            .withHost(ValueProvider.StaticValueProvider.of(options.getSpannerHost()))
            .withProjectId(spannerProjectId)
            .withInstanceId(options.getSpannerInstanceId())
            .withDatabaseId(options.getSpannerDatabase())
            .withRpcPriority(options.getRpcPriority());

    SpannerIO.ReadChangeStream readChangeStream =
        SpannerIO.readChangeStream()
            .withSpannerConfig(spannerConfig)
            .withMetadataInstance(options.getSpannerMetadataInstanceId())
            .withMetadataDatabase(options.getSpannerMetadataDatabase())
            .withChangeStreamName(options.getSpannerChangeStreamName())
            .withInclusiveStartAt(startTimestamp)
            .withInclusiveEndAt(endTimestamp)
            .withRpcPriority(options.getRpcPriority());

    String spannerMetadataTableName = options.getSpannerMetadataTableName();
    if (spannerMetadataTableName != null) {
    readChangeStream = readChangeStream.withMetadataTable(spannerMetadataTableName);
    }

    PCollection<DataChangeRecord> dataChangeRecord =
        pipeline
            .apply("Read from Spanner Change Streams", readChangeStream)
            .apply("Reshuffle DataChangeRecord", Reshuffle.viaRandomKey());

    PCollection<FailsafeElement<String, String>> sourceFailsafeModJson =
        dataChangeRecord
            .apply("DataChangeRecord To Mod JSON", ParDo.of(new DataChangeRecordToModJsonFn()))
            .apply(
                "Wrap Mod JSON In FailsafeElement",
                ParDo.of(
                    new DoFn<String, FailsafeElement<String, String>>() {
                    @ProcessElement
                    public void process(
                        @Element String input,
                        OutputReceiver<FailsafeElement<String, String>> receiver) {
                        receiver.output(FailsafeElement.of(input, input));
                    }
                    }))
            .setCoder(FAILSAFE_ELEMENT_CODER);

    PCollectionTuple dlqModJson =
        dlqManager.getReconsumerDataTransform(
            pipeline.apply(dlqManager.dlqReconsumer(options.getDlqRetryMinutes())));
    PCollection<FailsafeElement<String, String>> retryableDlqFailsafeModJson =
        dlqModJson.get(DeadLetterQueueManager.RETRYABLE_ERRORS).setCoder(FAILSAFE_ELEMENT_CODER);

    PCollection<FailsafeElement<String, String>> failsafeModJson =
        PCollectionList.of(sourceFailsafeModJson)
            .and(retryableDlqFailsafeModJson)
            .apply("Merge Source And DLQ Mod JSON", Flatten.pCollections());

    ImmutableSet.Builder<String> ignoreFieldsBuilder = ImmutableSet.builder();
    for (String ignoreField : options.getIgnoreFields().split(",")) {
    ignoreFieldsBuilder.add(ignoreField);
    }
    ImmutableSet<String> ignoreFields = ignoreFieldsBuilder.build();
    FailsafeModJsonToTableRowTransformer.FailsafeModJsonToTableRowOptions
        failsafeModJsonToTableRowOptions =
            FailsafeModJsonToTableRowTransformer.FailsafeModJsonToTableRowOptions.builder()
                .setSpannerConfig(spannerConfig)
                .setSpannerChangeStream(options.getSpannerChangeStreamName())
                .setIgnoreFields(ignoreFields)
                .setCoder(FAILSAFE_ELEMENT_CODER)
                .build();
    FailsafeModJsonToTableRowTransformer.FailsafeModJsonToTableRow failsafeModJsonToTableRow =
        new FailsafeModJsonToTableRowTransformer.FailsafeModJsonToTableRow(
            failsafeModJsonToTableRowOptions);

    PCollectionTuple tableRowTuple =
        failsafeModJson.apply("Mod JSON To TableRow", failsafeModJsonToTableRow);

    BigQueryDynamicDestinations.BigQueryDynamicDestinationsOptions
        bigQueryDynamicDestinationsOptions =
            BigQueryDynamicDestinations.BigQueryDynamicDestinationsOptions.builder()
                .setSpannerConfig(spannerConfig)
                .setChangeStreamName(options.getSpannerChangeStreamName())
                .setIgnoreFields(ignoreFields)
                .setBigQueryProject(getBigQueryProjectId(options))
                .setBigQueryDataset(options.getBigQueryDataset())
                .setBigQueryTableTemplate(options.getBigQueryChangelogTableNameTemplate())
                .build();
    WriteResult writeResult =
        tableRowTuple
            .get(failsafeModJsonToTableRow.transformOut)
            .apply(
                "Write To BigQuery",
                BigQueryIO.<TableRow>write()
                    .to(BigQueryDynamicDestinations.of(bigQueryDynamicDestinationsOptions))
                    .withFormatFunction(element -> removeIntermediateMetadataFields(element))
                    .withFormatRecordOnFailureFunction(element -> element)
                    .withCreateDisposition(CreateDisposition.CREATE_IF_NEEDED)
                    .withWriteDisposition(Write.WriteDisposition.WRITE_APPEND)
                    .withExtendedErrorInfo()
                    .withFailedInsertRetryPolicy(InsertRetryPolicy.retryTransientErrors()));

    PCollection<String> transformDlqJson =
        tableRowTuple
            .get(failsafeModJsonToTableRow.transformDeadLetterOut)
            .apply(
                "Failed Mod JSON During Table Row Transformation",
                MapElements.via(new StringDeadLetterQueueSanitizer()));

    PCollection<String> bqWriteDlqJson =
        BigQueryIOUtils.writeResultToBigQueryInsertErrors(writeResult, options)
            .apply(
                "Failed Mod JSON During BigQuery Writes",
                MapElements.via(new BigQueryDeadLetterQueueSanitizer()));

    PCollectionList.of(transformDlqJson)
        .and(bqWriteDlqJson)
        .apply("Merge Failed Mod JSON From Transform And BigQuery", Flatten.pCollections())
        .apply(
            "Write Failed Mod JSON To DLQ",
            DLQWriteTransform.WriteDLQ.newBuilder()
                .withDlqDirectory(dlqDirectory)
                .withTmpDirectory(tempDlqDirectory)
                .setIncludePaneInfo(true)
                .build());

    PCollection<FailsafeElement<String, String>> nonRetryableDlqModJsonFailsafe =
        dlqModJson.get(DeadLetterQueueManager.PERMANENT_ERRORS).setCoder(FAILSAFE_ELEMENT_CODER);

    nonRetryableDlqModJsonFailsafe
        .apply(
            "Write Mod JSON With Non-retryable Error To DLQ",
            MapElements.via(new StringDeadLetterQueueSanitizer()))
        .setCoder(StringUtf8Coder.of())
        .apply(
            DLQWriteTransform.WriteDLQ.newBuilder()
                .withDlqDirectory(dlqManager.getSevereDlqDirectoryWithDateTime())
                .withTmpDirectory(dlqManager.getSevereDlqDirectory() + "tmp/")
                .setIncludePaneInfo(true)
                .build());

    return pipeline.run();
    }

    private static DeadLetterQueueManager buildDlqManager(
    SpannerChangeStreamsToBigQueryOptions options) {
    String tempLocation =
        options.as(DataflowPipelineOptions.class).getTempLocation().endsWith("/")
            ? options.as(DataflowPipelineOptions.class).getTempLocation()
            : options.as(DataflowPipelineOptions.class).getTempLocation() + "/";
    String dlqDirectory =
        options.getDeadLetterQueueDirectory().isEmpty()
            ? tempLocation + "dlq/"
            : options.getDeadLetterQueueDirectory();

    LOG.info("Dead letter queue directory: {}", dlqDirectory);
    return DeadLetterQueueManager.create(dlqDirectory, DLQ_MAX_RETRIES);
    }

    private static String getSpannerProjectId(SpannerChangeStreamsToBigQueryOptions options) {
    return options.getSpannerProjectId().isEmpty()
        ? options.getProject()
        : options.getSpannerProjectId();
    }

    private static String getBigQueryProjectId(SpannerChangeStreamsToBigQueryOptions options) {
    return options.getBigQueryProjectId().isEmpty()
        ? options.getProject()
        : options.getBigQueryProjectId();
    }

    /**
    * Remove the following intermediate metadata fields that are not user data from {@link TableRow}:
    * _metadata_error, _metadata_retry_count, _metadata_spanner_original_payload_json.
    */
    private static TableRow removeIntermediateMetadataFields(TableRow tableRow) {
    TableRow cleanTableRow = tableRow.clone();
    Set<String> rowKeys = tableRow.keySet();
    Set<String> metadataFields = BigQueryUtils.getBigQueryIntermediateMetadataFieldNames();

    for (String rowKey : rowKeys) {
    if (metadataFields.contains(rowKey)) {
        cleanTableRow.remove(rowKey);
    }
    }

    return cleanTableRow;
    }

    /**
    * DoFn that converts a {@link DataChangeRecord} to multiple {@link Mod} in serialized JSON
    * format.
    */
    static class DataChangeRecordToModJsonFn extends DoFn<DataChangeRecord, String> {

    @ProcessElement
    public void process(@Element DataChangeRecord input, OutputReceiver<String> receiver) {
    for (org.apache.beam.sdk.io.gcp.spanner.changestreams.model.Mod changeStreamsMod :
        input.getMods()) {
        Mod mod =
            new Mod(
                changeStreamsMod.getKeysJson(),
                changeStreamsMod.getNewValuesJson(),
                input.getCommitTimestamp(),
                input.getServerTransactionId(),
                input.isLastRecordInTransactionInPartition(),
                input.getRecordSequence(),
                input.getTableName(),
                input.getModType(),
                input.getValueCaptureType(),
                input.getNumberOfRecordsInTransaction(),
                input.getNumberOfPartitionsInTransaction());

        String modJsonString;

        try {
        modJsonString = mod.toJson();
        } catch (IOException e) {
        // Ignore exception and print bad format.
        modJsonString = String.format("\"%s\"", input);
        }
        receiver.output(modJsonString);
    }
    }
    }
}
