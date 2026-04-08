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
package uk.co.magictractor.json.ref;

import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;

import uk.co.magictractor.service.ref.Ref;

/**
 *
 */
public class RefTypeAdapterFactory implements TypeAdapterFactory {

    private final Map<Class, TypeAdapter> typeAdapters = new HashMap<>();

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {

        Class<?> type = typeToken.getRawType();
        if (!Ref.class.isAssignableFrom(type)) {
            // Not a ref.
            return null;
        }

        if (typeAdapters.containsKey(type)) {
            // Have seem this Ref type before.
            return typeAdapters.get(type);
        }

        if (type.isInterface()) {
            // Not a concrete type.
            throw new IllegalStateException("Binding to Ref interfaces is not supported");
        }

        RefTypeAdapter typeAdapter = new RefTypeAdapter(type);
        typeAdapters.put(type, typeAdapter);
        // System.out.println("factory created type adapter for " + type);

        return typeAdapter;
    }

}
