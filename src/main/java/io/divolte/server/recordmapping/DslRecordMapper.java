/*
 * Copyright 2014 GoDataDriven B.V.
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

package io.divolte.server.recordmapping;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import io.divolte.server.ip2geo.LookupService;
import io.undertow.server.HttpServerExchange;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.concurrent.NotThreadSafe;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.GenericRecordBuilder;
import org.codehaus.groovy.control.CompilerConfiguration;

import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.typesafe.config.Config;

@ParametersAreNonnullByDefault
@NotThreadSafe
public class DslRecordMapper implements RecordMapper {
    private final Schema schema;
    private final List<DslRecordMapping.MappingAction> actions;

    public DslRecordMapper(final Config config, final Schema schema, final Optional<LookupService> geoipService) {
        this.schema = Objects.requireNonNull(schema);

        final String groovyFile = config.getString("divolte.tracking.schema_mapping.mapping_script_file");

        try {
            final DslRecordMapping mapping = new DslRecordMapping(schema, new UserAgentParserAndCache(config), geoipService);

            final String groovyScript = Files.toString(new File(groovyFile), StandardCharsets.UTF_8);

            final CompilerConfiguration compilerConfig = new CompilerConfiguration();
            compilerConfig.setScriptBaseClass("io.divolte.groovyscript.MappingBase");

            final Binding binding = new Binding();
            binding.setProperty("mapping", mapping);

            final GroovyShell shell = new GroovyShell(binding, compilerConfig);
            final Script script = shell.parse(groovyScript);

            script.run();

            actions = mapping.actions();
        } catch (IOException e) {
            throw new RuntimeException("Could not load mapping script file: " + groovyFile, e);
        }
    }

    public DslRecordMapper(final Schema schema, final DslRecordMapping mapping) {
        this.schema = schema;
        actions = mapping.actions();
    }

    @Override
    public GenericRecord newRecordFromExchange(HttpServerExchange exchange) {
        final GenericRecordBuilder builder = new GenericRecordBuilder(schema);
        Map<String,Object> context = Maps.newHashMapWithExpectedSize(20);

        actions.forEach((action) -> action.perform(exchange, context, builder));

        return builder.build();
    }
}
