package nhcm.bytecodevm.generator.virtualization.structure.event;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.VMStructure;
import nhcm.bytecodevm.enums.Acc;
import nhcm.bytecodevm.generator.virtualization.structure.api.AbstractVMStructureGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.api.GeneratedHandlerFamily;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchGenerationContext;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchGenerator;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMDispatchTarget;
import nhcm.bytecodevm.generator.virtualization.structure.api.VMStructureGenerationContext;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;
import nhcm.bytecodevm.utils.FieldUtils;
import nhcm.bytecodevm.utils.builder.FieldRef;

import java.util.LinkedHashMap;
import java.util.Map;

public final class EventVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    public EventVMGenerator()
    {
        super(VMStructure.EVENT);
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvIBdr ib, InterpretContext runtime)
    {
        int queueSize = generation.plan().laneCount();
        Local action = runtime.intLocal("eventAction", InterpretContext.OPCODE);
        Local events = runtime.local("eventQueue", "[I", InterpretContext.DISPATCH_SELECTOR + 1);
        Local head = runtime.intLocal("eventHead", InterpretContext.DISPATCH_SELECTOR + 2);
        Local tail = runtime.intLocal("eventTail", InterpretContext.DISPATCH_SELECTOR + 3);
        Local event = runtime.intLocal("currentEvent", InterpretContext.DISPATCH_SELECTOR + 4);
        ib.set(events, AdvIBdr.newArray("int", AdvIBdr.constant(queueSize)));
        ib.set(head, AdvIBdr.constant(0));
        ib.set(tail, AdvIBdr.constant(1));
        ib.set(action, AdvIBdr.constant(0));
        ib.setArray(events, AdvIBdr.constant(0), eventToken(generation, runtime));
        ib.whileLoop(
                AdvIBdr.equal(action, AdvIBdr.constant(0)),
                eventLoop -> {
                    eventLoop.set(event, AdvIBdr.arrayAt(
                            events,
                            AdvIBdr.bitAnd(head, AdvIBdr.constant(queueSize - 1))));
                    eventLoop.increment(head, 1);
                    eventLoop.set(action, generation.step(runtime, event));
                    eventLoop.ifCondition(
                            AdvIBdr.equal(action, AdvIBdr.constant(0)),
                            emit -> {
                                emit.setArray(
                                        events,
                                        AdvIBdr.bitAnd(tail, AdvIBdr.constant(queueSize - 1)),
                                        AdvIBdr.bitXor(eventToken(generation, runtime), event));
                                emit.increment(tail, 1);
                            });
                });
    }

    private Expr eventToken(VMStructureGenerationContext generation, InterpretContext runtime)
    {
        return generation.mix(
                runtime.frameStateKey(),
                runtime.frameProgramCounter(),
                runtime.frameBlockIndex(),
                AdvIBdr.constant(generation.profile().saltHandler));
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        GeneratedHandlerFamily listeners = GeneratedHandlerFamily.create(dispatch, "EventListener", 2);
        FieldRef listenersField = dispatch.generation().fieldRef("EVENT_LISTENERS", "Ljava/util/Map;");
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, listenersField.name(), listenersField.descriptor()));
        Map<Integer, VMDispatchTarget> targets = new LinkedHashMap<>();
        for (VMDispatchTarget target : dispatch.targets())
        {
            targets.putIfAbsent(target.key(), target);
        }
        dispatch.generation().onClassInitialize(initializer -> {
            Local listenerMap = initializer.var("eventListeners", "java/util/Map");
            initializer.set(listenerMap, AdvIBdr.newObject("java/util/concurrent/ConcurrentHashMap"));
            int ordinal = 0;
            for (Map.Entry<Integer, VMDispatchTarget> entry : targets.entrySet())
            {
                Local pair = initializer.var(
                        "listenerPair" + ordinal++, "[L" + listeners.interfaceName() + ";");
                initializer.set(pair, AdvIBdr.newArray(listeners.interfaceName(), AdvIBdr.constant(2)));
                initializer.setArray(pair, AdvIBdr.constant(0),
                                     listeners.newHandler(entry.getValue().primaryKey(), 0));
                initializer.setArray(pair, AdvIBdr.constant(1),
                                     listeners.newHandler(entry.getValue().primaryKey(), 1));
                initializer.directCall(mapPut(listenerMap, integer(AdvIBdr.constant(entry.getKey())), pair));
            }
            initializer.set(AdvIBdr.staticField(listenersField), listenerMap);
        });

        AdvIBdr ib = dispatch.instructions();
        InterpretContext runtime = dispatch.runtime();
        Local selector = runtime.intLocal("eventOpcode", InterpretContext.DISPATCH_SELECTOR);
        Local pair = runtime.local(
                "eventListenerPair", "[L" + listeners.interfaceName() + ";",
                InterpretContext.DISPATCH_SELECTOR + 1);
        Local channel = runtime.intLocal("eventChannel", InterpretContext.DISPATCH_SELECTOR + 2);
        Local listener = runtime.local(
                "eventListener", listeners.interfaceName(), InterpretContext.DISPATCH_SELECTOR + 3);
        Local status = runtime.intLocal("eventStatus", InterpretContext.DISPATCH_SELECTOR + 4);
        dispatch.setSelector(ib, runtime, selector);
        ib.set(pair, AdvIBdr.cast(
                mapGet(AdvIBdr.staticField(listenersField), integer(selector)),
                "[L" + listeners.interfaceName() + ";"));
        ib.ifCondition(AdvIBdr.isNull(pair), missing -> missing.gotoLabel(dispatch.unknown()));
        ib.set(channel, AdvIBdr.bitAnd(
                dispatch.generation().mix(
                        runtime.structureState(), runtime.frameStateKey(), selector,
                        AdvIBdr.constant(dispatch.profile().saltHandler)),
                AdvIBdr.constant(1)));
        ib.set(listener, AdvIBdr.arrayAt(pair, channel));
        ib.set(status, listeners.invoke(listener, runtime));
        dispatch.finishExternal(ib, status);
    }

    private static Expr integer(Expr value)
    {
        return AdvIBdr.callStatic("java/lang/Integer", "valueOf", "java/lang/Integer", value);
    }

    private static Expr mapGet(Expr map, Expr key)
    {
        return AdvIBdr.callInterface(
                map, "java/util/Map", "get", "java/lang/Object", AdvIBdr.cast(key, "java/lang/Object"));
    }

    private static Expr mapPut(Expr map, Expr key, Expr value)
    {
        return AdvIBdr.callInterface(
                map, "java/util/Map", "put", "java/lang/Object",
                AdvIBdr.cast(key, "java/lang/Object"), AdvIBdr.cast(value, "java/lang/Object"));
    }
}
