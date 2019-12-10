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


import com.github.jcustenborder.kafka.load.testing.model.BadgeContainer;
import com.github.jcustenborder.kafka.load.testing.model.CommentContainer;
import com.github.jcustenborder.kafka.load.testing.model.PostContainer;
import com.github.jcustenborder.kafka.load.testing.model.PostHistoryContainer;
import com.github.jcustenborder.kafka.load.testing.model.PostLinkContainer;
import com.github.jcustenborder.kafka.load.testing.model.TagContainer;
import com.github.jcustenborder.kafka.load.testing.model.UserContainer;
import com.github.jcustenborder.kafka.load.testing.model.VoteContainer;
import com.google.common.base.Preconditions;
import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class DataGenerator {
  private static final Logger log = LoggerFactory.getLogger(DataGenerator.class);

  public static void main(String... args) throws Exception {
    Thread.setDefaultUncaughtExceptionHandler((t, e) -> log.error("Exception thrown", e));
    Map<String, Schema> schemaByFileName = Stream.of(
        BadgeContainer.SCHEMA$,
        CommentContainer.SCHEMA$,
        PostContainer.SCHEMA$,
        PostHistoryContainer.SCHEMA$,
        PostLinkContainer.SCHEMA$,
        TagContainer.SCHEMA$,
        UserContainer.SCHEMA$,
        VoteContainer.SCHEMA$
    )
        .collect(Collectors.toMap(schema -> schema.getProp("import.file.name"), schema -> schema));

    SAXParserFactory parserFactory = SAXParserFactory.newInstance();
    parserFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, false);
    parserFactory.setValidating(false);
    SAXParser parser = parserFactory.newSAXParser();

    OptionParser optionParser = new OptionParser();
    final OptionSpec<File> outputFileSpec = optionParser.accepts("output", "Directory to write files to")
        .withRequiredArg()
        .ofType(File.class);
    final OptionSpec<Long> fileSizeSpec = optionParser.accepts("filesize", "Target size of the file in megabytes.")
        .withOptionalArg()
        .ofType(Long.class)
        .defaultsTo(256L * 1024L * 1024L);

    final NonOptionArgumentSpec<File> inputFileSpec = optionParser.nonOptions().ofType(File.class);

    OptionSet options = optionParser.parse(args);

    Preconditions.checkState(
        options.has(inputFileSpec),
        "%s must be specified", "--input"
    );
    Preconditions.checkState(
        options.has(outputFileSpec),
        "%s must be specified", "--output"
    );

    final List<File> inputFiles = options.valuesOf(inputFileSpec);
    final File outputPath = options.valueOf(outputFileSpec);
    final Long fileSize = options.valueOf(fileSizeSpec);

    if (!outputPath.isDirectory()) {
      outputPath.mkdirs();
    }

    ExecutorService executorService = Executors.newFixedThreadPool(4);
    CompletableFuture[] tasks = inputFiles
        .stream()
        .map(inputFile -> new ParserRunnable(schemaByFileName, outputPath, fileSize, inputFile))
        .map(runnable -> CompletableFuture.runAsync(runnable, executorService))
        .toArray(CompletableFuture[]::new);
    CompletableFuture allTasks = CompletableFuture.allOf(tasks);
    executorService.shutdown();
  }


}
