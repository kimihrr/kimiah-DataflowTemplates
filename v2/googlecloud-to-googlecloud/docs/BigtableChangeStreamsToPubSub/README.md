@@ -0,0 +1,16 @@
# Bigtable Change Streams To PubSub Dataflow Template

The [BigtableChangeStreamsToPubSub]
(src/main/java/com/google/cloud/teleport/v2/templates/BigtableChangeStreamsToPubSub.java)
pipeline reads messages from Cloud Bigtable Change Streams and publish messages with a changelog schema into topics in
PubSub. The supported message formats could be AVRO or Protocol Buffer.


Changelog schema is defined as follows:

| Column name     | BigQuery Type | Required? | Description                                                                                                                                           |
| --------------- | ------------- | --------- |-------------------------------------------------------------------------------------------------------------------------------------------------------|
| row_key | STRING | Y | Bigtable row key                                                                                                                                      |
| mod_type | STRING | Y | Modification type: {SET_CELL, DELETE_CELLS, DELETE_FAMILY}. DeleteFromRow mutation is converted into a series of DELETE_FROM_FAMILY entries.          |
| is_gc* | BOOL | N | TRUE indicates that mutation was made by garbage collection in CBT                                                                                    |
| tiebreaker* | INT | N | CBT tie-breaker value. Used for conflict resolution if two mutations are committed at the sametime.                                                   | 
| commit_timestamp | TIMESTAMP | Y | Time when CBT wrote this mutation to a tablet                                                                                                         |
| column_family | STRING | Y | CBT column family name                                                                                                                                |
| column | STRING | N | CBT column qualifier                                                                                                                                  |
| timestamp | TIMESTAMP/INT | N | CBT cell’s timestamp. Type is determined by _writeNumericTimestamps_ pipeline option                                                                  |
| timestamp_from | TIMESTAMP/INT | N | Time range start (inclusive) for a DeleteFromColumn mutation. Type is determined by _writeNumericTimestamps_ pipeline option                          |
| timestamp_to | TIMESTAMP/INT | N | Time range end (exclusive) for a DeleteFromColumn mutation. Type is determined by _writeNumericTimestamps_ pipeline option                            |                             
| value | STRING/BYTES | N | Bigtable cell value. Not specified for delete operations                                                                                              |

