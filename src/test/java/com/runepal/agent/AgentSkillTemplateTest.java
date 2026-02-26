package com.runepal.agent;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AgentSkillTemplateTest {
    @Test
    public void fromWireNameSupportsUnderscoreAndSpaceForms() {
        Optional<AgentSkillTemplate> direct = AgentSkillTemplate.fromWireName("MINE_POWER");
        Optional<AgentSkillTemplate> spaced = AgentSkillTemplate.fromWireName("mine power");

        assertTrue(direct.isPresent());
        assertTrue(spaced.isPresent());
        assertEquals(AgentSkillTemplate.MINE_POWER, direct.get());
        assertEquals(AgentSkillTemplate.MINE_POWER, spaced.get());
    }

    @Test
    public void stopAllDoesNotStartBot() {
        assertFalse(AgentSkillTemplate.STOP_ALL.startsBot());
        assertTrue(AgentSkillTemplate.MINE_POWER.startsBot());
    }
}
