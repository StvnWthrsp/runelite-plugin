package com.runepal.agent.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ScriptParserTest {
    @Test
    public void parseBuildsStateMapAndSteps() {
        ScriptParser parser = new ScriptParser();

        JsonObject script = new JsonObject();
        script.addProperty("name", "mine_loop");
        script.addProperty("entryState", "main");

        JsonObject states = new JsonObject();
        JsonArray mainSteps = new JsonArray();

        JsonObject ifStep = new JsonObject();
        ifStep.addProperty("type", "if");
        JsonObject condition = new JsonObject();
        condition.addProperty("type", "inventory_full");
        ifStep.add("condition", condition);
        ifStep.addProperty("thenState", "bank");
        ifStep.addProperty("elseState", "mine");
        mainSteps.add(ifStep);

        states.add("main", mainSteps);

        JsonArray mineSteps = new JsonArray();
        JsonObject gotoStep = new JsonObject();
        gotoStep.addProperty("type", "goto");
        gotoStep.addProperty("targetState", "main");
        mineSteps.add(gotoStep);
        states.add("mine", mineSteps);

        JsonArray bankSteps = new JsonArray();
        JsonObject stopStep = new JsonObject();
        stopStep.addProperty("type", "stop");
        bankSteps.add(stopStep);
        states.add("bank", bankSteps);

        script.add("states", states);

        ScriptSpec spec = parser.parse(script);
        assertEquals("mine_loop", spec.getName());
        assertEquals("main", spec.getEntryState());
        assertEquals(3, spec.getStates().size());
        assertNotNull(spec.getStates().get("main"));
        assertEquals(ScriptStepType.IF, spec.getStates().get("main").get(0).getType());
    }
}
