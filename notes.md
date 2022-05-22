# Kafka terminology

- **Brokers** live inside a cluster
- Brokers are managed by **Zookeeper**
- **Producers** produce events via Brokers - they use the *ProducerAPI*
- **Consumers** consume events via Brokers - they use the *ConsumerAPI*

[Learn - documentation](https://docs.spring.io/spring-kafka/docs/current/reference/html/)

## Kafka Broker

Uses *ConnectAPI*
It has:

- **Source connector** - pulls data from an external source (DB, file system, etc) into a Kafka Topic
- **Sink connector** - pushes data to an external source

## Kafka streams

Uses *StreamsAPI* to take the data from Kafka, perform transformations on it, and then put it back into Kafka.

## Topics

It is an entity in Kafka with a name (it is the equivalent of a table in a database)
Topics live inside the Broker.
Producers use the Topic name to produce a message.
Consumers continuously poll messages from the Broker using the Topic name.
The message still remains in Kafka after being consumed, as per the configured retention time.

## Partitions

Partition is where the messages live inside the Topic.
Each Topic has one or more Partitions.
Each Partition is an *ordered, immutable sequence of Records*.
Each Partition is independent of each other.
Each Record is assigned a sequential number called **offset**.
Ordering is guaranteed only at the partition level.
The Producer has control over in which partition the message should go.

## Consumers

### Consumers offsets

Consumers have three options to read:
- from-beginning
- latest
- specific offset

`__consumer_offsets` is a topic inside Kafka.

Consumer offsets behave like a bookmark for the consumer to start reading messages from the point it left off.

### Consumer groups

Are used for scalable message consumption.
Each different application will have a unique consumer group.
We can have multiple consumers with the same `group_id`, reading from different partitions.
A consumer is single-threaded.

Kafka Broker manages consumer groups.

## Kafka Producer configurations
- acks
  - acks = 0, 1 and all
  - acks = 1 -> guarantees message is written to a leader (default)
  - acks = all -> guarantees message is written to a leader and all replicas
  - acks = 0 -> no guarantee (not recommended)
- retries
  - Integer value = [0 - 2147483647]
  - in Spring Kafka, the default value is the max of this interval
- retry.backoff.ms
  - default is 100 ms
- [Full Producer config](https://kafka.apache.org/documentation/#producerconfigs)