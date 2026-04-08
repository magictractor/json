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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.base.Splitter;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import uk.co.magictractor.service.ref.Ref;

/**
 *
 */
public class RefTypeAdapter<REF extends Ref<?>> extends TypeAdapter<REF> {

    //private final Class<KEY> keyType;
    private final List<Constructor<?>> constructors = new ArrayList<>();
    private final Function<Object, REF> refFunction;
    // Two fields used because padding could be nulls.
    private boolean removeListPadding = false;
    private Object listPadding;
    private Function<Object, Object> keyMapping;

    public RefTypeAdapter(Class<?> refType) {
        if (!Ref.class.isAssignableFrom(refType)) {
            throw new IllegalArgumentException("Not a Ref implementation: " + refType.getSimpleName());
        }

        for (Constructor<?> constructor : refType.getConstructors()) {
            if (constructor.getParameterCount() == 1) {
                constructors.add(constructor);
            }
        }
        if (constructors.isEmpty()) {
            throw new IllegalStateException("No single arg constrctors in Ref type " + refType);
        }

        this.refFunction = this::createRef;
    }

    /**
     * Specify values to be removed from a list of Json values. Typically used
     * to remove padding values.
     */
    public void removeListPadding(Object listPadding) {
        removeListPadding = true;
        this.listPadding = listPadding;
    }

    public <KEY> void mapKey(Function<KEY, KEY> keyMapping) {
        if (this.keyMapping != null) {
            throw new IllegalStateException("Key mapping has already been set");
        }
        this.keyMapping = (Function) keyMapping;
    }

    // TODO! remove this constructor, just use the other constructor via RefTypeAdapterFactory
    //    public RefTypeAdapter(/* Class<KEY> keyType, */ Function refFunction) {
    //        //this.keyType = keyType;
    //        this.refFunction = refFunction;
    //    }

    private REF createRef(Object jsonValue) {
        Constructor<?> constructor = findConstructorForJsonValue(jsonValue);

        //  Class<?> paramType = constructor.getParameterTypes()[0];
        Parameter param = constructor.getParameters()[0];
        Class<?> paramType = param.getType();
        if (!paramType.isInstance(jsonValue)) {
            if (int.class.equals(paramType) && jsonValue instanceof Integer) {
                // Do nothing
            }
            else if (String.class.equals(paramType)) {
                jsonValue = jsonValue.toString();
            }
            else if (int.class.equals(paramType) && jsonValue instanceof String) {
                jsonValue = Integer.valueOf((String) jsonValue);
            }
            else if (List.class.equals(paramType) && jsonValue instanceof String) {
                List<String> elements = Splitter.on(",").trimResults().splitToList((String) jsonValue);
                // Parameter paramType = constructor.getParameters()[0];
                // Type pType = (paramType.getParameterizedType());
                // sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl
                Type pType = param.getParameterizedType();
                // System.out.println("pType: " + pType + "  " + pType.getClass().getName());
                // System.out.println(((ParameterizedType) pType).getActualTypeArguments()[0]);
                Type genericsType = ((ParameterizedType) pType).getActualTypeArguments()[0];
                // System.out.println(genericsType + "  " + genericsType.getClass());
                if (String.class.equals(genericsType)) {
                    jsonValue = elements;
                }
                else if (Integer.class.equals(genericsType)) {
                    jsonValue = elements.stream().map(Integer::parseInt).collect(Collectors.toList());
                }
                else {
                    throw new IllegalStateException("Code need modified to convert String to a List of " + genericsType);
                }
            }
            else if (!String.class.isInstance(paramType)) {
                throw new IllegalStateException("Code need modified to convert "
                        + jsonValue.getClass().getSimpleName() + " to " + paramType.getSimpleName());
            }
        }

        // Typically this is used for trait spoilers were all traits are "none"
        // "none", "none", "pathfinder" has also been seen
        // TODO! change name to "ignoreValue"?
        // TODO! differentiate between all instances and trailing?
        if (removeListPadding) {
            // TODO! pType and genericsType also set above
            Type pType = param.getParameterizedType();
            Type genericsType = ((ParameterizedType) pType).getActualTypeArguments()[0];
            if (!genericsType.equals(listPadding.getClass())) {
                throw new IllegalStateException(
                    "Cannot remove elements of type " + listPadding.getClass().getName() + " from a List with generic type " + genericsType);
            }

            if (List.class.isInstance(jsonValue)) {
                jsonValue = ((List) jsonValue).stream().filter(elem -> !listPadding.equals(elem)).collect(Collectors.toList());
            }
            else {
                throw new IllegalStateException(
                    "Code needs modified to remove values from " + jsonValue.getClass().getName() + ", or removeListPadding() has been incorrrectly used.");
            }
        }

        try {
            return (REF) constructor.newInstance(jsonValue);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Failed to create instance of "
                    + constructor.getDeclaringClass().getName()
                    + " with parameter of type " + jsonValue.getClass().getSimpleName()
                    + " and value " + jsonValue,
                e);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Constructor<?> findConstructorForJsonValue(Object jsonValue) {
        Class<?> jsonValueType = jsonValue.getClass();
        for (Constructor<?> constructor : constructors) {
            // TODO! cache some of this...
            Parameter paramType = constructor.getParameters()[0];
            Type pType = (paramType.getParameterizedType());
            //System.err.println(pType); //int

            if (constructors.size() != 1) {
                throw new IllegalStateException();
            }

            // TODO! check all params
            return constructor;
        }

        throw new IllegalStateException();
    }

    @Override
    public REF read(JsonReader reader) throws IOException {
        if (JsonToken.NULL.equals(reader.peek())) {
            reader.nextNull();
            return null;
        }

        Object key;
        if (JsonToken.BEGIN_ARRAY.equals(reader.peek())) {
            key = readList(reader);
        }
        else {
            key = readValue(reader);
            if (key == null) {
                // key mapping has converted a value such as -1 to null
                return null;
            }
        }

        return refFunction.apply(key);
    }

    private Object readValue(JsonReader reader) throws IOException {
        Object value;
        if (JsonToken.STRING.equals(reader.peek())) {
            value = reader.nextString();
        }
        else if (JsonToken.NUMBER.equals(reader.peek())) {
            value = reader.nextInt();
        }
        else {
            throw new IllegalStateException("Unexpected Json token " + reader.peek());
        }

        if (keyMapping != null) {
            value = keyMapping.apply(value);
        }

        return value;
    }

    private List readList(JsonReader reader) throws IOException {
        reader.beginArray();

        List list = new ArrayList();
        while (!JsonToken.END_ARRAY.equals(reader.peek())) {
            list.add(readValue(reader));
        }

        reader.endArray();

        return list;
    }

    @Override
    public void write(JsonWriter writer, Ref ref) throws IOException {
        Object key = ref.key();
        if (key instanceof String) {
            writer.value((String) key);
        }
        else if (key instanceof Integer) {
            writer.value((Integer) key);
        }
        else {
            throw new IllegalStateException();
        }
    }

}
