# Consumers
## Consumers offsets
Consumers have three options to read:
- from-beginning
- latest
- specific offset

`__consumer_offsets` is a topic inside Kafka.

Consumer offsets behave like a bookmark for the consumer to start reading messages from the point it left off.

## Consumer groups
Are used for scalable message consumption.
Each different application will have a unique consumer group.
We can have multiple consumers with the same `group_id`, reading from different partitions.
A consumer is single-threaded.

Kafka Broker manages consumer groups.