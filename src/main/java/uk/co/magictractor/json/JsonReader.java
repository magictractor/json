/**
 * Copyright 2019 Ken Dobson
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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;

import uk.co.magictractor.util.exception.ExceptionUtil.SupplierWithException;

/**
 *
 */
public class JsonReader {

    private final DocumentContext ctx;

    // Commented out when code imported into util project.
    // public JsonReader(DataResource dataResource, JsonReaderConfig config) {
    //     this(() -> dataResource.openInputStream(), config);
    // }

    public JsonReader(SupplierWithException<InputStream, IOException> jsonSupplier, JsonReaderConfig config) {
        try (InputStream jsonStream = jsonSupplier.get()) {
            ctx = JsonPath.parse(jsonStream, createConfiguration(config));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public DocumentContext getDocumentContext() {
        return ctx;
    }

    public <E> E root(Class<? extends E> elementType) {
        return read("$", elementType);
    }

    public <E> List<E> rootList(Class<? extends E> elementType) {
        return readList("$", elementType);
    }

    public <E> E read(String jsonPath, Class<? extends E> elementType) {
        checkConcrete(elementType);
        return ctx.read(jsonPath, elementType);
    }

    public <E> List<E> readList(String jsonPath, Class<? extends E> elementType) {
        checkConcrete(elementType);
        return ctx.read(jsonPath, new TypeRef<List<E>>() {
            @Override
            public Type getType() {
                return new ParameterizedType() {

                    @Override
                    public Type getRawType() {
                        return List.class;
                    }

                    @Override
                    public Type getOwnerType() {
                        return null;
                    }

                    @Override
                    public Type[] getActualTypeArguments() {
                        return new Type[] { elementType };
                    }
                };
            }
        });
    }

    private void checkConcrete(Class<?> elementType) {
        if (elementType.isInterface()) {
            throw new IllegalArgumentException("elementType must be a concrete class");
        }
    }

    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> readMap(String jsonPath) {
        return read(jsonPath, LinkedHashMap.class);
    }

    // Based on code from uk.co.magictractor.spew.core.response.parser.jayway.JaywayConfigurationCache
    private Configuration createConfiguration(JsonReaderConfig config) {
        GsonBuilder gsonBuilder = new GsonBuilder();

        // TODO! this was commented out when moving code into the util project.
        // Looks like it it is needed. If so, maybe add SPI?
        // gsonBuilder.registerTypeAdapterFactory(new RefTypeAdapterFactory());
        // Removed because there are several enums in SpellStep where vanilla is fine.
        // gsonBuilder.registerTypeAdapterFactory(new RequireSpecificEnumTypeAdapterFactory());
        gsonBuilder.registerTypeAdapterFactory(ENUM_FACTORY);

        // Typical use will be to add source specific type adapters.
        if (config != null) {
            config.configureGsonBuilder(gsonBuilder);
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
}
