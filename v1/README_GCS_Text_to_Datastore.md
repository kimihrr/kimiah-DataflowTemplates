Text Files on Cloud Storage to Datastore [Deprecated] Template
---
Batch pipeline. Reads from text files stored in Cloud Storage and writes JSON-encoded entities to Datastore.

:memo: This is a Google-provided template! Please
check [Provided templates documentation](https://cloud.google.com/dataflow/docs/guides/templates/provided/cloud-storage-to-datastore)
on how to use it without having to build from sources using [Create job from template](https://console.cloud.google.com/dataflow/createjob?template=GCS_Text_to_Datastore).


:bulb: This is a generated documentation based
on [Metadata Annotations](https://github.com/GoogleCloudPlatform/DataflowTemplates#metadata-annotations)
. Do not change this file directly.

## Parameters

### Required Parameters

* **textReadPattern** (Input file(s) in Cloud Storage): The input file pattern Dataflow reads from. Ex: gs://your-bucket/.../*.json.
* **datastoreWriteProjectId** (Project ID): The Google Cloud project ID of where to write Datastore entities.
* **errorWritePath** (Output failure file): The error log output folder to use for write failures that occur during processing. (Example: gs://your-bucket/errors/).

### Optional Parameters

* **javascriptTextTransformGcsPath** (JavaScript UDF path in Cloud Storage): The Cloud Storage path pattern for the JavaScript code containing your user-defined functions.
* **javascriptTextTransformFunctionName** (JavaScript UDF name): The name of the function to call from your JavaScript file. Use only letters, digits, and underscores. (Example: transform_udf1).
* **datastoreWriteEntityKind** (Datastore entity kind): Datastore kind under which entities will be written in the output Google Cloud project.
* **datastoreWriteNamespace** (Datastore namespace): Datastore namespace under which entities will be written in the output Google Cloud project.
* **datastoreHintNumWorkers** (Expected number of workers): Hint for the expected number of workers in the Datastore ramp-up throttling step. Defaults to: 500.


## User-Defined functions (UDFs)

The Text Files on Cloud Storage to Datastore [Deprecated] Template supports User-Defined functions (UDFs).
UDFs allow you to customize functionality by providing a JavaScript function
without having to maintain or build the entire template code.

Check [Create user-defined functions for Dataflow templates](https://cloud.google.com/dataflow/docs/guides/templates/create-template-udf)
and [Using UDFs](https://github.com/GoogleCloudPlatform/DataflowTemplates#using-udfs)
for more information about how to create and test those functions.


## Getting Started

### Requirements

* Java 11
* Maven
* [gcloud CLI](https://cloud.google.com/sdk/gcloud), and execution of the
  following commands:
  * `gcloud auth login`
  * `gcloud auth application-default login`

:star2: Those dependencies are pre-installed if you use Google Cloud Shell!
[![Open in Cloud Shell](http://gstatic.com/cloudssh/images/open-btn.svg)](https://console.cloud.google.com/cloudshell/editor?cloudshell_git_repo=https%3A%2F%2Fgithub.com%2FGoogleCloudPlatform%2FDataflowTemplates.git&cloudshell_open_in_editor=/v1/src/main/java/com/google/cloud/teleport/templates/TextToDatastore.java)

### Templates Plugin

This README provides instructions using
the [Templates Plugin](https://github.com/GoogleCloudPlatform/DataflowTemplates#templates-plugin)
. Install the plugin with the following command before proceeding:

```shell
mvn clean install -pl plugins/templates-maven-plugin -am
```

### Building Template

This template is a Classic Template, meaning that the pipeline code will be
executed only once and the pipeline will be saved to Google Cloud Storage for
further reuse. Please check [Creating classic Dataflow templates](https://cloud.google.com/dataflow/docs/guides/templates/creating-templates)
and [Running classic templates](https://cloud.google.com/dataflow/docs/guides/templates/running-templates)
for more information.

#### Staging the Template

If the plan is to just stage the template (i.e., make it available to use) by
the `gcloud` command or Dataflow "Create job from template" UI,
the `-PtemplatesStage` profile should be used:

```shell
export PROJECT=<my-project>
export BUCKET_NAME=<bucket-name>

mvn clean package -PtemplatesStage  \
-DskipTests \
-DprojectId="$PROJECT" \
-DbucketName="$BUCKET_NAME" \
-DstagePrefix="templates" \
-DtemplateName="GCS_Text_to_Datastore" \
-pl v1 \
-am
```

The `-DgcpTempLocation=<temp-bucket-name>` parameter can be specified to set the GCS bucket used by the DataflowRunner to write
temp files to during serialization. The path used will be `gs://<temp-bucket-name>/temp/`.

The command should build and save the template to Google Cloud, and then print
the complete location on Cloud Storage:

```
Classic Template was staged! gs://<bucket-name>/templates/GCS_Text_to_Datastore
```

The specific path should be copied as it will be used in the following steps.

#### Running the Template

**Using the staged template**:

You can use the path above run the template (or share with others for execution).

To start a job with the template at any time using `gcloud`, you are going to
need valid resources for the required parameters.

Provided that, the following command line can be used:

```shell
export PROJECT=<my-project>
export BUCKET_NAME=<bucket-name>
export REGION=us-central1
export TEMPLATE_SPEC_GCSPATH="gs://$BUCKET_NAME/templates/GCS_Text_to_Datastore"

### Required
export TEXT_READ_PATTERN=<textReadPattern>
export DATASTORE_WRITE_PROJECT_ID=<datastoreWriteProjectId>
export ERROR_WRITE_PATH=<errorWritePath>

### Optional
export JAVASCRIPT_TEXT_TRANSFORM_GCS_PATH=<javascriptTextTransformGcsPath>
export JAVASCRIPT_TEXT_TRANSFORM_FUNCTION_NAME=<javascriptTextTransformFunctionName>
export DATASTORE_WRITE_ENTITY_KIND=<datastoreWriteEntityKind>
export DATASTORE_WRITE_NAMESPACE=<datastoreWriteNamespace>
export DATASTORE_HINT_NUM_WORKERS=500

gcloud dataflow jobs run "gcs-text-to-datastore-job" \
  --project "$PROJECT" \
  --region "$REGION" \
  --gcs-location "$TEMPLATE_SPEC_GCSPATH" \
  --parameters "textReadPattern=$TEXT_READ_PATTERN" \
  --parameters "javascriptTextTransformGcsPath=$JAVASCRIPT_TEXT_TRANSFORM_GCS_PATH" \
  --parameters "javascriptTextTransformFunctionName=$JAVASCRIPT_TEXT_TRANSFORM_FUNCTION_NAME" \
  --parameters "datastoreWriteProjectId=$DATASTORE_WRITE_PROJECT_ID" \
  --parameters "datastoreWriteEntityKind=$DATASTORE_WRITE_ENTITY_KIND" \
  --parameters "datastoreWriteNamespace=$DATASTORE_WRITE_NAMESPACE" \
  --parameters "datastoreHintNumWorkers=$DATASTORE_HINT_NUM_WORKERS" \
  --parameters "errorWritePath=$ERROR_WRITE_PATH"
```

For more information about the command, please check:
https://cloud.google.com/sdk/gcloud/reference/dataflow/jobs/run


**Using the plugin**:

Instead of just generating the template in the folder, it is possible to stage
and run the template in a single command. This may be useful for testing when
changing the templates.

```shell
export PROJECT=<my-project>
export BUCKET_NAME=<bucket-name>
export REGION=us-central1

### Required
export TEXT_READ_PATTERN=<textReadPattern>
export DATASTORE_WRITE_PROJECT_ID=<datastoreWriteProjectId>
export ERROR_WRITE_PATH=<errorWritePath>

### Optional
export JAVASCRIPT_TEXT_TRANSFORM_GCS_PATH=<javascriptTextTransformGcsPath>
export JAVASCRIPT_TEXT_TRANSFORM_FUNCTION_NAME=<javascriptTextTransformFunctionName>
export DATASTORE_WRITE_ENTITY_KIND=<datastoreWriteEntityKind>
export DATASTORE_WRITE_NAMESPACE=<datastoreWriteNamespace>
export DATASTORE_HINT_NUM_WORKERS=500

mvn clean package -PtemplatesRun \
-DskipTests \
-DprojectId="$PROJECT" \
-DbucketName="$BUCKET_NAME" \
-Dregion="$REGION" \
-DjobName="gcs-text-to-datastore-job" \
-DtemplateName="GCS_Text_to_Datastore" \
-Dparameters="textReadPattern=$TEXT_READ_PATTERN,javascriptTextTransformGcsPath=$JAVASCRIPT_TEXT_TRANSFORM_GCS_PATH,javascriptTextTransformFunctionName=$JAVASCRIPT_TEXT_TRANSFORM_FUNCTION_NAME,datastoreWriteProjectId=$DATASTORE_WRITE_PROJECT_ID,datastoreWriteEntityKind=$DATASTORE_WRITE_ENTITY_KIND,datastoreWriteNamespace=$DATASTORE_WRITE_NAMESPACE,datastoreHintNumWorkers=$DATASTORE_HINT_NUM_WORKERS,errorWritePath=$ERROR_WRITE_PATH" \
-pl v1 \
-am
```
