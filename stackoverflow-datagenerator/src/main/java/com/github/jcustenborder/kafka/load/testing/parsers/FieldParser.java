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
package com.github.jcustenborder.kafka.load.testing.parsers;

import com.google.common.base.Preconditions;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.xml.sax.Attributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class FieldParser {
  private final String attributeName;
  private final int fieldPosition;

  protected FieldParser(Schema.Field field) {
    this.attributeName = field.name();
    this.fieldPosition = field.pos();
  }

  public static FieldParser fieldParser(Schema.Field field) {
    final Schema schema = field.schema();
    final Schema nonNullSchema = nonNullSchema(schema);
    final String logicalType = null != nonNullSchema.getLogicalType() ? nonNullSchema.getLogicalType().getName() : null;

    final FieldParser result;

    if (Schema.Type.LONG == nonNullSchema.getType() && "timestamp-millis".equals(logicalType)) {
      result = new TimestampFieldParser(field);
    } else if (Schema.Type.STRING == nonNullSchema.getType()) {
      result = new StringFieldParser(field);
    } else if (Schema.Type.LONG == nonNullSchema.getType()) {
      result = new LongFieldParser(field);
    } else if (Schema.Type.INT == nonNullSchema.getType()) {
      result = new IntFieldParser(field);
    } else if (Schema.Type.BOOLEAN == nonNullSchema.getType()) {
      result = new BooleanFieldParser(field);
    } else {
      throw new UnsupportedOperationException(
          String.format("Unsupported schema: %s", nonNullSchema.toString(true))
      );
    }

    return result;
  }

  private static Schema nonNullSchema(Schema schema) {
    Schema result;

    if (Schema.Type.UNION == schema.getType()) {
      List<Schema> nonNullSchemas = schema.getTypes().stream()
          .filter(s -> Schema.Type.NULL != s.getType())
          .collect(Collectors.toList());
      Preconditions.checkState(nonNullSchemas.size() == 1, "Only one non null schema should be allowed.");
      result = nonNullSchemas.get(0);
    } else {
      result = schema;
    }

    return result;
  }

  public static List<FieldParser> fieldParsers(Schema schema) {
    List<FieldParser> result = new ArrayList<>();

    for (Schema.Field field : schema.getFields()) {
      FieldParser fieldParser = fieldParser(field);
      result.add(fieldParser);
    }

    return result;
  }

  protected abstract Object parse(String inputValue);

  public void setFieldValue(Map<String, Object> attributeCache, Attributes attributes, GenericRecord record) {
    Object fieldValue = attributeCache.computeIfAbsent(this.attributeName, s -> {
      Object result;
      String inputValue = attributes.getValue(attributeName);
      if (null != inputValue) {
        result = parse(inputValue);
      } else {
        result = null;
      }
      return result;
    });

    record.put(this.fieldPosition, fieldValue);
  }

}
