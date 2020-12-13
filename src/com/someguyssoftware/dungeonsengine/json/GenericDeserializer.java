/**
 * 
 */
package com.someguyssoftware.dungeonsengine.json;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

/**
 * @author Mark Gottschling on Dec 21, 2018
 *
 */
public class GenericDeserializer implements JsonDeserializer {
    private Class<?> implClass;

    public GenericDeserializer(Class<?> c) {
        implClass = c;
    }

    @Override
    public Object deserialize(JsonElement json, Type typeOfT, 
            JsonDeserializationContext context) throws JsonParseException {
        return context.deserialize(json, implClass);
    }
}
