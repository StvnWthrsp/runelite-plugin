# FSM Task Generation Prompt Template

```
TASK: Create a new FSM-based BotTask implementation for the Runepal RuneLite plugin.

ULTRATHINK about the task requirements and architecture before coding. Analyze the existing MiningTask, CombatTask, FishingTask, and WoodcuttingTask implementations to understand established patterns.

## SPECIFICATIONS
**Task Name**: [TaskName]  
**Primary Skill**: [Skill enum]  
**Activity**: [Description]  
**Type**: [CONTINUOUS|COMPLETION_BASED]  

**FSM States**: [STATE1, STATE2, STATE3, ...]  
**Object IDs**: [Primary: X,Y,Z | Secondary: A,B,C]  
**NPC IDs**: [X,Y,Z] (if applicable)  
**Location**: [WorldPoint or enum]  
**Items**: [Required: X | Collected: Y | Consumables: Z]  
**Inventory Action**: [BANK|DROP|STOP]  
**Config Options**: [option1: enum/boolean, option2: enum/boolean, ...]  
**Subtasks**: [WalkTask, BankTask, ...]  

**Events**: [AnimationChanged, StatChanged, InteractingChanged, GameTick, ...]  
**Special Behaviors**: [Describe unique logic]  
**Error Handling**: [No objects: X | Missing items: Y | Failed action: Z]

## IMPLEMENTATION REQUIREMENTS

FOLLOW these mandatory patterns from existing tasks:

1. **Class Structure**: Extend BotTask, implement proper constructor with service injection
2. **State Enum**: Create nested enum with all FSM states including IDLE, WAITING_FOR_SUBTASK
3. **Event Handlers**: Store as instance variables, subscribe in onStart(), unsubscribe in onStop()
4. **Service Usage**: ActionService for interactions, GameService for world queries, HumanizerService for delays
5. **ActionQueue**: Use for sequencing actions, never direct state changes in event handlers
6. **Lifecycle**: Implement onStart/onLoop/onStop/isFinished/isStarted/getTaskName correctly
7. **Logging**: Add comprehensive debug logging for state transitions and key actions
8. **Validation**: Always validate objects before interaction, handle null cases
9. **Timeouts**: Include retry counters and failure timeouts for robustness
10. **Pre-targeting**: Implement next-object hovering for optimization when applicable

NEVER use Thread.sleep() - use HumanizerService delays only.
NEVER use deprecated RuneLite API methods.
ALWAYS validate objects before interaction.
ALWAYS use ActionQueue for action sequencing.

## VALIDATION CHECKLIST

After generating the task, verify:
- [ ] Follows exact patterns from existing tasks
- [ ] All service dependencies properly injected
- [ ] Event handlers stored as instance variables
- [ ] State transitions use ActionQueue
- [ ] No Thread.sleep() usage
- [ ] Comprehensive error handling
- [ ] Proper logging throughout
- [ ] Configuration integration
- [ ] Subtask integration follows WAITING_FOR_SUBTASK pattern

Generate the complete task implementation now.
```

