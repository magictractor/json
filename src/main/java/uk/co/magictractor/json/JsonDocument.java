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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.TypeRef;

/**
 *
 */
public class JsonDocument {

    private final DocumentContext ctx;

    // Instances created via JsonParser.
    /* default */ JsonDocument(DocumentContext ctx) {
        this.ctx = ctx;
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

    public <V> Map<String, V> rootMap() {
        return readMap("$");
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
    public <V> Map<String, V> readMap(String jsonPath) {
        return read(jsonPath, LinkedHashMap.class);
    }

    public <K, V> Map<K, V> readMap(String jsonPath, Class<? extends K> keyType, Class<? extends V> valueType) {
        checkConcrete(keyType);
        checkConcrete(valueType);
        return ctx.read(jsonPath, new TypeRef<Map<K, V>>() {
            @Override
            public Type getType() {
                return new ParameterizedType() {

                    @Override
                    public Type getRawType() {
                        return Map.class;
                    }

                    @Override
                    public Type getOwnerType() {
                        return null;
                    }

                    @Override
                    public Type[] getActualTypeArguments() {
                        return new Type[] { keyType, valueType };
                    }
                };
            }
        });
    }

}
