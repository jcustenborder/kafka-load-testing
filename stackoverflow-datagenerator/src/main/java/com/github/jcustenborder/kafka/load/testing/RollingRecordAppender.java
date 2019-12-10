/**
 * Copyright © 2019 Jeremy Custenborder (jcustenborder@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jcustenborder.kafka.load.testing;

import org.apache.avro.Schema;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

class RollingRecordAppender implements RecordAppender {
  private static final Logger log = LoggerFactory.getLogger(RollingRecordAppender.class);
  final Schema schema;
  final String fileNameTemplate;
  final File outputPath;
  final long targetFileSize;
  final DataFileWriter<GenericRecord> templateWriter;
  int fileNumber = 0;
  File currentFile;
  DataFileWriter<GenericRecord> currentWriter;
  long recordsWritten = 0L;

  public RollingRecordAppender(Schema schema, String fileNameTemplate, File outputPath, long targetFileSize, DataFileWriter<GenericRecord> templateWriter) throws IOException {
    this.schema = schema;
    this.fileNameTemplate = fileNameTemplate;
    this.outputPath = outputPath;
    this.targetFileSize = targetFileSize;
    this.templateWriter = templateWriter;
    this.currentWriter = roll();
  }


  private DataFileWriter<GenericRecord> roll() throws IOException {
    if (null != this.currentWriter) {
      log.info("Closing file {}", this.currentFile);
      this.currentWriter.close();
    }
    fileNumber++;
    String fileName = String.format(this.fileNameTemplate, fileNumber);
    this.currentFile = new File(this.outputPath, fileName);
    log.info("Opening file {}", this.currentFile);
    DataFileWriter<GenericRecord> writer = this.templateWriter.create(this.schema, this.currentFile);
    return writer;
  }


  @Override
  public void append(GenericRecord record) {
    try {
      this.currentWriter.append(record);

      if (this.recordsWritten > 0 && this.recordsWritten % 1000 == 0) {
        long size = this.currentFile.length();

        if (size >= (this.targetFileSize)) {
          this.currentWriter = roll();
        }
      }
      if (this.recordsWritten > 0 && this.recordsWritten % 10000 == 0) {
        log.info("Appended {} records to {}.", this.recordsWritten, this.currentFile);
      }
    } catch (IOException e) {
      log.error("Exception thrown", e);
    }
    recordsWritten++;
  }

  @Override
  public void close() throws IOException {
    this.currentWriter.close();
  }
}
