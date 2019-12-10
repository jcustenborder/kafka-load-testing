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

import org.apache.avro.Schema;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumWriter;
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.InputStream;
import java.util.Map;

public class ParserRunnable implements Runnable {
  private static final Logger log = LoggerFactory.getLogger(ParserRunnable.class);
  private final Map<String, Schema> schemaByFileName;
  private final SAXParser parser;
  private final File outputPath;
  private final long fileSize;
  private final File inputFile;

  public ParserRunnable(Map<String, Schema> schemaByFileName, File outputPath, long fileSize, File inputFile) {
    this.schemaByFileName = schemaByFileName;
    this.outputPath = outputPath;
    this.fileSize = fileSize;
    this.inputFile = inputFile;

    SAXParserFactory parserFactory = SAXParserFactory.newInstance();
    try {
      parserFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
      parserFactory.setValidating(false);
      this.parser = parserFactory.newSAXParser();
    } catch (Exception ex) {
      log.error("Exception thrown", ex);
      throw new IllegalStateException("Could not create parser", ex);
    }
  }


  @Override
  public void run() {
    try (SevenZFile sevenZFile = new SevenZFile(inputFile)) {
      SevenZArchiveEntry entry;
      while (null != (entry = sevenZFile.getNextEntry())) {
        try (InputStream inputStream = new ArchiveEntryInputStream(sevenZFile, entry)) {
          Schema schema = schemaByFileName.get(entry.getName());
          if (null == schema) {
            log.warn("No schema for {}", entry.getName());
            continue;
          }
          String fileNameTemplate = schema.getProp("output.file.name.template");
          String topicName = schema.getProp("kafka.topic");
          log.info("Processing {} with {}", entry.getName(), schema.toString(true));
          DatumWriter<GenericRecord> datumWriter = new GenericDatumWriter<>(schema);
          DataFileWriter<GenericRecord> template = new DataFileWriter<>(datumWriter);
          template.setMeta("kafka.topic", topicName);
          template.setCodec(CodecFactory.snappyCodec());
          template.setSyncInterval(1024 * 1024 * 2);

          try (RollingRecordAppender appender = new RollingRecordAppender(
              schema,
              fileNameTemplate,
              outputPath,
              fileSize, template
          )) {
            GenericHandler handler = new GenericHandler(schema, appender);
            parser.parse(inputStream, handler);
          }
          template.close();
        }
      }
    } catch (Exception ex) {
      log.error("Exception thrown while processing {}", this.inputFile, ex);
    }
  }
}
