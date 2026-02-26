package com.runepal.agent.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScriptValidatorTest {
    @Test
    public void validateRejectsMissingTargetState() {
        ScriptParser parser = new ScriptParser();
        ScriptValidator validator = new ScriptValidator();

        JsonObject script = new JsonObject();
        script.addProperty("name", "bad_script");
        script.addProperty("entryState", "main");

        JsonObject states = new JsonObject();
        JsonArray main = new JsonArray();

        JsonObject gotoStep = new JsonObject();
        gotoStep.addProperty("type", "goto");
        gotoStep.addProperty("targetState", "missing");
        main.add(gotoStep);

        states.add("main", main);
        script.add("states", states);

        ScriptSpec spec = parser.parse(script);
        ScriptValidationResult result = validator.validate(spec);
        assertFalse(result.isValid());
    }

    @Test
    public void validateAcceptsSimpleStopScript() {
        ScriptParser parser = new ScriptParser();
        ScriptValidator validator = new ScriptValidator();

        JsonObject script = new JsonObject();
        script.addProperty("name", "ok_script");
        script.addProperty("entryState", "main");

        JsonObject states = new JsonObject();
        JsonArray main = new JsonArray();

        JsonObject stopStep = new JsonObject();
        stopStep.addProperty("type", "stop");
        main.add(stopStep);

        states.add("main", main);
        script.add("states", states);

        ScriptSpec spec = parser.parse(script);
        ScriptValidationResult result = validator.validate(spec);
        assertTrue(result.isValid());
    }
}
