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

import org.apache.avro.Schema;

import javax.xml.bind.DatatypeConverter;
import java.util.Calendar;

class TimestampFieldParser extends FieldParser {
  public TimestampFieldParser(Schema.Field field) {
    super(field);
  }

  @Override
  protected Object parse(String inputValue) {
    Calendar calendar = DatatypeConverter.parseDateTime(inputValue);
    return calendar.getTime().getTime();
  }
}
