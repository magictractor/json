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
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.spi.json.GsonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.GsonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;

import uk.co.magictractor.util.exception.ExceptionUtil.SupplierWithException;

/**
 *
 */
public class JsonWriter {

    private final DocumentContext ctx;

    // Commented out when code imported into util project.
    //    public JsonWriter(DataResource dataResource, JsonReaderConfig config) {
    //        try (InputStream in = dataResource.openInputStream()) {
    //            ctx = JsonPath.parse(in, createConfiguration(config));
    //        }
    //        catch (IOException e) {
    //            throw new UncheckedIOException(e);
    //        }
    //    }

    public JsonWriter(Object object, JsonReaderConfig config) {
        ctx = JsonPath.parse(object, createConfiguration(config));
    }

    public JsonWriter(DocumentContext ctx) {
        this.ctx = ctx;
    }

    public void write(SupplierWithException<OutputStream, IOException> outSupplier) {
        try (OutputStream out = outSupplier.get()) {
            write(out, StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void write(OutputStream out) {
        write(out, StandardCharsets.UTF_8);
    }

    public void write(OutputStream out, Charset charset) {
        write(new OutputStreamWriter(out, charset));
    }

    public void write(Writer out) {
        try {
            out.append(ctx.jsonString());
            out.flush();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Configuration createConfiguration(JsonReaderConfig config) {
        GsonBuilder gsonBuilder = new GsonBuilder();

        Gson gson = gsonBuilder.setPrettyPrinting().create();
        JsonProvider jsonProvider = new GsonJsonProvider(gson);
        MappingProvider mappingProvider = new GsonMappingProvider(gson);

        if (config != null) {
            config.configureGsonBuilder(gsonBuilder);
        }

        return new Configuration.ConfigurationBuilder()
                .jsonProvider(jsonProvider)
                .mappingProvider(mappingProvider)
                .build();
    }

}

//private final DocumentContext ctx;
//
//public JsonWriter(DataResource dataResource, JsonReaderConfig config) {
//    try (InputStream in = dataResource.openInputStream()) {
//        ctx = JsonPath.parse(in, createConfiguration(config));
//    }
//    catch (IOException e) {
//        throw new UncheckedIOException(e);
//    }
//}
//
//public JsonWriter(Object object, JsonReaderConfig config) {
//    ctx = JsonPath.parse(object, createConfiguration(config));
//}
//
//public void write(OutputStream out) {
//    write(out, StandardCharsets.UTF_8);
//}
//
//public void write(OutputStream out, Charset charset) {
//    write(new OutputStreamWriter(out, charset));
//}
//
//public void write(Writer out) {
//    try {
//        out.append(ctx.jsonString());
//        out.flush();
//    }
//    catch (IOException e) {
//        throw new UncheckedIOException(e);
//    }
//}
//
//private Configuration createConfiguration(JsonReaderConfig config) {
//    GsonBuilder gsonBuilder = new GsonBuilder();
//
//    Gson gson = gsonBuilder.setPrettyPrinting().create();
//    JsonProvider jsonProvider = new GsonJsonProvider(gson);
//    MappingProvider mappingProvider = new GsonMappingProvider(gson);
//
//    if (config != null) {
//        config.configureGsonBuilder(gsonBuilder);
//    }
//
//    return new Configuration.ConfigurationBuilder()
//            .jsonProvider(jsonProvider)
//            .mappingProvider(mappingProvider)
//            .build();
//}
