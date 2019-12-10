/**
 * Copyright © 2019 Jeremy Custenborder (jcustenborder@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jcustenborder.kafka.load.testing;

import com.google.common.collect.ImmutableMap;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import joptsimple.ArgumentAcceptingOptionSpec;
import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.KafkaAdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class AvroFileLoadTestProducer {
  private static final Logger log = LoggerFactory.getLogger(AvroFileLoadTestProducer.class);

  public static void main(String... args) throws Exception {


    OptionParser optionParser = new OptionParser();
    final OptionSpec<File> producerConfigSpec = optionParser.accepts("producer-config", "Producer configuration.")
        .withRequiredArg()
        .ofType(File.class);

    final ArgumentAcceptingOptionSpec<Short> replicationFactorSpec = optionParser.accepts("replication-factor")
        .withOptionalArg()
        .ofType(Short.class)
        .defaultsTo((short) 3);
    final OptionSpec<Integer> partitionsSpec = optionParser.accepts("partitions")
        .withOptionalArg()
        .ofType(Integer.class)
        .defaultsTo(40);
    final OptionSpec<Integer> diskIoThreadsSpec = optionParser.accepts("disk-io-threads")
        .withOptionalArg()
        .ofType(Integer.class)
        .defaultsTo(Runtime.getRuntime().availableProcessors());
    final OptionSpec<Integer> inSyncPreplicationSpec = optionParser.accepts("min-insync-replicas")
        .withOptionalArg()
        .ofType(Integer.class)
        .defaultsTo(3);

    OptionSpec createTopicSpec = optionParser.accepts("create-topic");


    final NonOptionArgumentSpec<File> inputFileSpec = optionParser.nonOptions().ofType(File.class);
    OptionSet options = optionParser.parse(args);
    final boolean createTopic = options.has(createTopicSpec);
    final int diskIoThreads = options.valueOf(diskIoThreadsSpec);


    File producerConfigFile = options.valueOf(producerConfigSpec);
    List<File> inputFiles = options.valuesOf(inputFileSpec);

    Properties properties = new Properties();
    log.info("Loading properties from {}", producerConfigFile);
    try (InputStream propertiesStream = new FileInputStream(producerConfigFile)) {
      properties.load(propertiesStream);
    }
    log.info("Configuring serializers to {}", KafkaAvroSerializer.class.getName());
    properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
    properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());

    Map<String, AtomicLong> topicTotals = new ConcurrentHashMap<>();
    Map<String, Object> topicLookup = new ConcurrentHashMap<>();
    Function<String, Object> topicHandler = topic -> {
      final Object result;

      if (createTopic) {
        result = null;
        log.info("Skipping topic creation for {}", topic);
      } else {
        log.info("Creating topic {}", topic);
        try (AdminClient adminClient = KafkaAdminClient.create(properties)) {
          short replicationFactor = options.valueOf(replicationFactorSpec);
          int partitions = options.valueOf(partitionsSpec);
          int insyncReplicas = options.valueOf(inSyncPreplicationSpec);
          NewTopic newTopic = new NewTopic(topic, partitions, replicationFactor);
          newTopic.configs(
              ImmutableMap.of("min.insync.replicas", Integer.toString(insyncReplicas))
          );
          adminClient.createTopics(
              Collections.singletonList(newTopic)
          );
        }
        result = null;
      }
      return result;
    };
    log.info("Creating thread pool with {} threads.", diskIoThreads);
    final ExecutorService executorService = Executors.newScheduledThreadPool(diskIoThreads);
    try (Producer<Object, Object> producer = new KafkaProducer<>(properties)) {
      CompletableFuture[] tasks = inputFiles
          .stream()
          .map(file -> new ProducerRunnable(producer, file, topicLookup, topicHandler, topicTotals))
          .map(runnable -> CompletableFuture.runAsync(runnable, executorService))
          .toArray(CompletableFuture[]::new);
      CompletableFuture allTasks = CompletableFuture.allOf(tasks);
      allTasks.get();
    } finally {
      executorService.shutdown();
    }

  }
}
