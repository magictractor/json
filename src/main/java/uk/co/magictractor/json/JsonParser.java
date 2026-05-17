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
import com.google.gson.InstanceCreator;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
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

    private List<GsonBuilderConfig> gsonBuilderConfigs = new ArrayList<>();

    public <T> JsonParser registerConverter(Class<T> type, Converter<String, T> converter) {
        ensureNullContext();
        gsonBuilderConfigs.add(new GsonBuilderAddConverter<>(type, converter));

        return this;
    }

    /**
     * Where possible, {@link #registerConverter()} should be preferred in case
     * a backend other than Gson is enabled in future.
     */
    public JsonParser registerGsonTypeAdapter(Type type, TypeAdapter<?> typeAdapter) {
        return registerGsonTypeAdapterObject(type, typeAdapter);
    }

    public JsonParser registerGsonTypeAdapter(Type type, InstanceCreator<?> instanceCreator) {
        return registerGsonTypeAdapterObject(type, instanceCreator);
    }

    public JsonParser registerGsonTypeAdapter(Type type, JsonSerializer<?> jsonSerializer) {
        return registerGsonTypeAdapterObject(type, jsonSerializer);
    }

    public JsonParser registerGsonTypeAdapter(Type type, JsonDeserializer<?> jsonDeserializer) {
        return registerGsonTypeAdapterObject(type, jsonDeserializer);
    }

    /**
     * Four classes are permitted for {@code typeAdapter}. These are checked in
     * {@code GsonBuilder.registerTypeAdapter()}. In this class, the four
     * permitted types each have a method that delegates to this method.
     */
    private JsonParser registerGsonTypeAdapterObject(Type type, Object typeAdapter) {
        ensureNullContext();
        gsonBuilderConfigs.add(new GsonBuilderAddAdapter(type, typeAdapter));

        return this;
    }

    private void ensureNullContext() {
        if (parseContext != null) {
            throw new IllegalStateException();
        }
    }

    public JsonDocument parse(InputStream inputStream, String charset) {
        return new JsonDocument(getParseContext().parse(inputStream, charset));
    }

    // https://pkg.go.dev/encoding/json
    public JsonDocument parse(InputStream inputStream) {
        // <quote> JSON text SHALL be encoded in UTF-8, UTF-16, or UTF-32.</quote>
        // TODO! maybe check the first bytes to identify UTF-16 and UTF-32, otherwise
        // UTF-8 is assumed.
        // https://www.rfc-editor.org/rfc/rfc7159.html#page-9
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
            parseContext = new ParseContextImpl(createConfiguration());
        }
        return parseContext;
    }

    // Based on code from uk.co.magictractor.spew.core.response.parser.jayway.JaywayConfigurationCache
    private Configuration createConfiguration() {
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
