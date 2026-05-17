/**
 * Copyright 2026 Ken Dobson
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
package uk.co.magictractor.json;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.internal.bind.TypeAdapters;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.internal.ParseContextImpl;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;

import uk.co.magictractor.util.converter.Converter;

/**
 *
 */
public class JsonParser {

    // parseContext is set on demand is is then used to guard against the configuration changing
    private ParseContextImpl parseContext;

    // TODO! bin this and register adapters on JsonParser directly
    private JsonReaderConfig config;

    private List<GsonBuilderConfig> gsonBuilderConfigs = new ArrayList<>();

    /** Caller is responsible for closing the {@code InputStream}. */
    // TODO! add ability to handle encodings other than the default UTF-8.
    // JsonPath.parse() does not accept a charset, but could use new ParseContextImpl(configuration).parse(json, charset);
    // TODO! introduce JsonParser and pass that around instead of JsonReaderConfig
    // JsonParser.parse(InputStream) and JsonParser.parse(InputStream) would return JsonDocument
    // and maybe add interfaces? so Jayway JsonDocument?
    //public JsonDocument(InputStream inputStream, JsonParserConfig config) {
    //    ctx = JsonPath.parse(inputStream, createConfiguration(config));
    //}
    //
    /**
     * @deprecated use no args constructor and register adapters on this
     *             instance
     */
    @Deprecated
    public JsonParser(JsonReaderConfig config) {
        // parseContext = new ParseContextImpl(createConfiguration(config));
        this.config = config;
    }

    public JsonParser() {
    }

    public <T> void registerConverter(Class<T> type, Converter<String, T> converter) {
        ensureNullContext();
        gsonBuilderConfigs.add(new GsonBuilderAddConverter<>(type, converter));
    }

    // TODO! migrate to adding configuration like this and remove JsonParserConfig
    // TODO! restrict this to the permitted classes for typeAdapter
    public void registerTypeAdapter(Type type, Object typeAdapter) {
        ensureNullContext();
        gsonBuilderConfigs.add(new GsonBuilderAddAdapter(type, typeAdapter));
    }

    private void ensureNullContext() {
        if (parseContext != null) {
            throw new IllegalStateException();
        }
    }

    public JsonDocument parse(InputStream inputStream, String charset) {
        return new JsonDocument(getParseContext().parse(inputStream, charset));
    }

    public JsonDocument parse(InputStream inputStream) {
        return parse(inputStream, "UTF-8");
    }

    public JsonDocument parse(Supplier<InputStream> inputStreamSupplier) {
        return parse(inputStreamSupplier, "UTF-8");
    }

    public JsonDocument parse(Supplier<InputStream> inputStreamSupplier, String charset) {
        try (InputStream inputStream = inputStreamSupplier.get()) {
            return parse(inputStream, charset);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private ParseContextImpl getParseContext() {
        if (parseContext == null) {
            parseContext = new ParseContextImpl(createConfiguration(config));
        }
        return parseContext;
    }

    // Based on code from uk.co.magictractor.spew.core.response.parser.jayway.JaywayConfigurationCache
    private Configuration createConfiguration(JsonReaderConfig config) {
        GsonBuilder gsonBuilder = new GsonBuilder();

        // TODO! this was commented out when moving code into the util project.
        // Looks like it it is needed. If so, maybe add SPI?
        // gsonBuilder.registerTypeAdapterFactory(new RefTypeAdapterFactory());

        // Removed because there are several enums in SpellStep where vanilla is fine.
        // gsonBuilder.registerTypeAdapterFactory(new RequireSpecificEnumTypeAdapterFactory());

        // Use SPI to find TypeAdapterFactory implementations.
        // This will include RefTypeAdapterFactory if the service project is on the classpath.
        Iterator<TypeAdapterFactory> typeAdapterFactoryIterator = ServiceLoader.load(TypeAdapterFactory.class).iterator();
        while (typeAdapterFactoryIterator.hasNext()) {
            TypeAdapterFactory typeAdapterFactory = typeAdapterFactoryIterator.next();
            gsonBuilder.registerTypeAdapterFactory(typeAdapterFactory);
        }

        gsonBuilder.registerTypeAdapterFactory(ENUM_FACTORY);

        // Typical use will be to add source specific type adapters.
        if (config != null) {
            config.configureGsonBuilder(gsonBuilder);
        }

        for (GsonBuilderConfig gsonBuilderConfig : gsonBuilderConfigs) {
            gsonBuilderConfig.configure(gsonBuilder);
        }

        Gson gson = gsonBuilder.create();
        JsonProvider jsonProvider = new GsonJsonProvider(gson);
        MappingProvider mappingProvider = new GsonMappingProvider(gson);

        // Option.DEFAULT_PATH_LEAF_TO_NULL required for nextPageToken used with Google paged services
        return new Configuration.ConfigurationBuilder()
                .jsonProvider(jsonProvider)
                .mappingProvider(mappingProvider)
                //.options(Option.DEFAULT_PATH_LEAF_TO_NULL)
                .build();
    }

    private static final TypeAdapterFactory ENUM_FACTORY = new TypeAdapterFactory() {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
            TypeAdapter<T> base = TypeAdapters.ENUM_FACTORY.create(gson, typeToken);
            if (base == null) {
                // Not an enum type.
                return null;
            }

            // Wrap the base.
            return new NoNullTypeAdapter<T>(typeToken, base);
        }
    };

    private static class NoNullTypeAdapter<T> extends TypeAdapter<T> {

        private final TypeToken<T> typeToken;
        private final TypeAdapter<T> base;

        /* default */ NoNullTypeAdapter(TypeToken<T> typeToken, TypeAdapter<T> base) {
            this.typeToken = typeToken;
            this.base = base;
        }

        @Override
        public void write(JsonWriter out, T value) throws IOException {
            base.write(out, value);
        }

        @Override
        public T read(com.google.gson.stream.JsonReader in) throws IOException {
            // Only gets the token type, cannot get the value without consuming it.
            JsonToken peek = in.peek();
            if (peek == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            String str = in.nextString();

            // Create a new Reader containing the String.
            // It does not appear to be possible to get the String from the reader before or after the
            // base adapter consumes the String.
            com.google.gson.stream.JsonReader strReader = new com.google.gson.stream.JsonReader(new StringReader(str));
            // Need to setLenient because the reader contains only a String, so is malformed JSON.
            strReader.setLenient(true);

            T result = base.read(strReader);
            if (result == null) {
                throw new JsonSyntaxException(typeToken.getRawType().getSimpleName() + " enum does not contain a value corresponding to Json value '" + str + "'");
            }

            return result;
        }
    }

    @FunctionalInterface
    public interface GsonBuilderConfig {
        void configure(GsonBuilder gsonBuilder);
    }

    private class GsonBuilderAddAdapter implements GsonBuilderConfig {
        private final Type type;
        private final Object typeAdapter;

        /* default */ GsonBuilderAddAdapter(Type type, Object typeAdapter) {
            this.type = type;
            this.typeAdapter = typeAdapter;
        }

        @Override
        public void configure(GsonBuilder gsonBuilder) {
            gsonBuilder.registerTypeAdapter(type, typeAdapter);
        }
    }

    private class GsonBuilderAddConverter<T> implements GsonBuilderConfig {
        private final Class<T> type;
        private final Converter<String, T> converter;

        /* default */ GsonBuilderAddConverter(Class<T> type, Converter<String, T> converter) {
            this.type = type;
            this.converter = converter;
        }

        @Override
        public void configure(GsonBuilder gsonBuilder) {
            gsonBuilder.registerTypeAdapter(type, ConverterTypeAdapter.adapterFor(converter));
        }
    }

}
