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

import org.apache.avro.file.DataFileReader;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

class ProducerRunnable implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(ProducerRunnable.class);
  final Producer<Object, Object> producer;
  final File inputFile;
  final Map<String, Object> topicLookup;
  final Function<String, Object> topicHandler;
  final Map<String, AtomicLong> topicTotals;

  ProducerRunnable(
      Producer<Object, Object> producer,
      File inputFile,
      Map<String, Object> topicLookup,
      Function<String, Object> topicHandler,
      Map<String, AtomicLong> topicTotals) {
    this.producer = producer;
    this.inputFile = inputFile;
    this.topicLookup = topicLookup;
    this.topicHandler = topicHandler;
    this.topicTotals = topicTotals;
  }

  @Override
  public void run() {
    log.info("Processing file {}", inputFile);
    DatumReader<GenericRecord> datumReader = new GenericDatumReader<>();
    try (DataFileReader<GenericRecord> fileReader = new DataFileReader<>(inputFile, datumReader)) {
      final String topic = fileReader.getMetaString("kafka.topic");
      GenericRecord avroRecord = null;
      AtomicInteger inFlightRequests = new AtomicInteger(0);
      AtomicLong totalRequests = this.topicTotals.computeIfAbsent(topic, s -> new AtomicLong(0));
      while (fileReader.hasNext()) {
        while (inFlightRequests.get() > 10000) {
          Thread.sleep(100);
        }
        avroRecord = fileReader.next(avroRecord);
        Object key = avroRecord.get("key");
        Object value = avroRecord.get("value");

        ProducerRecord<Object, Object> producerRecord = new ProducerRecord<>(
            topic,
            key,
            value
        );
        inFlightRequests.incrementAndGet();
        Future<RecordMetadata> future = producer.send(producerRecord, (metadata, exception) -> {
          if (null != exception) {
            log.error("Exception thrown", exception);
          }
          inFlightRequests.decrementAndGet();
        });

        long requests = totalRequests.incrementAndGet();
        if (requests % 10000L == 0 && log.isInfoEnabled()) {
          log.info("Produced {} messages to {}.", requests, topic);
        }
      }
    } catch (IOException | InterruptedException e) {
      log.error("Exception thrown", e);
    }
  }
}
