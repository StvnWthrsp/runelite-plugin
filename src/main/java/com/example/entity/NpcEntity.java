package com.example.entity;

import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import java.awt.Shape;
import java.util.Objects;

/**
 * Adapter class that wraps a RuneLite NPC and implements the Interactable interface.
 * This allows NPCs to be used polymorphically with other interactable entities.
 */
public class NpcEntity implements Interactable {
    
    private final NPC npc;
    
    public NpcEntity(NPC npc) {
        this.npc = Objects.requireNonNull(npc, "NPC cannot be null");
    }
    
    @Override
    public String getName() {
        return npc.getName();
    }
    
    @Override
    public WorldPoint getWorldLocation() {
        return npc.getWorldLocation();
    }
    
    @Override
    public Shape getClickbox() {
        return npc.getConvexHull();
    }
    
    /**
     * Gets the underlying NPC.
     * @return the wrapped NPC
     */
    public NPC getNpc() {
        return npc;
    }
    
    /**
     * Gets the ID of the underlying NPC.
     * @return the NPC ID
     */
    public int getId() {
        return npc.getId();
    }
    
    /**
     * Gets the health ratio of the underlying NPC.
     * @return the health ratio (0-100, where 0 is dead)
     */
    public int getHealthRatio() {
        return npc.getHealthRatio();
    }
    
    /**
     * Gets what the NPC is currently interacting with.
     * @return the Actor the NPC is interacting with, or null
     */
    public net.runelite.api.Actor getInteracting() {
        return npc.getInteracting();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        NpcEntity that = (NpcEntity) obj;
        return Objects.equals(npc, that.npc);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(npc);
    }
    
    @Override
    public String toString() {
        return "NpcEntity{" +
               "name='" + npc.getName() + '\'' +
               ", id=" + npc.getId() +
               ", location=" + npc.getWorldLocation() +
               '}';
    }
} 