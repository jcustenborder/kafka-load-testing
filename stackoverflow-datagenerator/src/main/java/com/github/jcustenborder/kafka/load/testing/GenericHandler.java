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

import com.github.jcustenborder.kafka.load.testing.parsers.FieldParser;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenericHandler extends DefaultHandler {
  private static final String ROW = "row";
  private final Schema schema;
  private final Schema keySchema;
  private final Schema valueSchema;
  private final List<FieldParser> keyParsers;
  private final List<FieldParser> valueParsers;
  private final String expectedRootElement;
  private final RecordAppender appender;
  private final Map<String, Object> attributeCache;

  public GenericHandler(Schema schema, RecordAppender appender) {
    this.schema = schema;
    this.appender = appender;
    this.keySchema = schema.getField("key").schema();
    this.valueSchema = schema.getField("value").schema();
    this.keyParsers = FieldParser.fieldParsers(this.keySchema);
    this.valueParsers = FieldParser.fieldParsers(this.valueSchema);
    this.expectedRootElement = schema.getProp("xml.expected.root.element");
    this.attributeCache = new HashMap<>(100);
  }

  @Override
  public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
    if (!ROW.equals(qName)) {
      return;
    }
    GenericRecord record = new GenericData.Record(this.schema);
    GenericRecord keyRecord = new GenericData.Record(this.keySchema);
    GenericRecord valueRecord = new GenericData.Record(this.valueSchema);
    this.attributeCache.clear();

    for (FieldParser fieldParser : this.keyParsers) {
      fieldParser.setFieldValue(this.attributeCache, attributes, keyRecord);
    }

    for (FieldParser fieldParser : this.valueParsers) {
      fieldParser.setFieldValue(this.attributeCache, attributes, valueRecord);
    }

    record.put("key", keyRecord);
    record.put("value", valueRecord);
    appender.append(record);
  }
}
