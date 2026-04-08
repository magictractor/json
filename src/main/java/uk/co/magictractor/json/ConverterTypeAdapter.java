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

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import uk.co.magictractor.util.converter.Converter;

/**
 *
 */
public class ConverterTypeAdapter<T> extends TypeAdapter<T> {

    private final Converter<String, T> converter;

    public static <T> TypeAdapter<T> adapterFor(Converter<String, T> converter) {
        return new ConverterTypeAdapter<T>(converter);
    }

    private ConverterTypeAdapter(Converter<String, T> converter) {
        this.converter = converter;
    }

    @Override
    public T read(JsonReader reader) throws IOException {
        if (JsonToken.NULL.equals(reader.peek())) {
            reader.nextNull();
            return null;
        }

        String from = reader.nextString();

        return converter.convert(from);
    }

    @Override
    public void write(JsonWriter writer, T value) throws IOException {
        if (value != null) {
            writer.value(converter.reverse(value));
        }
    }

}
