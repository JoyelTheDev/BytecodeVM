package nhcm.bytecodevm.generator.virtualization.structure.fsm;

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

public final class FsmVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    public FsmVMGenerator()
    {
        super(VMStructure.FSM);
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvIBdr ib, InterpretContext runtime)
    {
        Local action = runtime.intLocal("fsmAction", InterpretContext.OPCODE);
        Local state = runtime.intLocal("fsmState", InterpretContext.DISPATCH_SELECTOR + 1);
        Local transition = runtime.intLocal("fsmTransition", InterpretContext.DISPATCH_SELECTOR + 2);
        ib.set(action, AdvIBdr.constant(0));
        ib.set(state, state(generation, runtime, AdvIBdr.constant(0)));
        ib.whileLoop(
                AdvIBdr.equal(action, AdvIBdr.constant(0)),
                machine -> {
                    machine.set(transition, AdvIBdr.bitXor(
                            state,
                            AdvIBdr.constant(generation.profile().saltState)));
                    machine.set(action, generation.step(runtime, state));
                    machine.ifCondition(
                            AdvIBdr.equal(action, AdvIBdr.constant(0)),
                            next -> next.set(state, state(generation, runtime, transition)));
                });
    }

    private Expr state(
            VMStructureGenerationContext generation,
            InterpretContext runtime,
            Expr previous)
    {
        return AdvIBdr.bitXor(
                generation.mix(
                        runtime.frameProgramCounter(),
                        runtime.frameBlockIndex(),
                        previous,
                        AdvIBdr.constant(generation.profile().saltState)),
                AdvIBdr.constant(generation.profile().saltState));
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        int stateCount = dispatch.plan().laneCount();
        GeneratedHandlerFamily handlers = GeneratedHandlerFamily.create(dispatch, "State", stateCount);
        FieldRef resolverField = dispatch.generation().fieldRef("FSM_SYMBOLS", "Ljava/util/Map;");
        FieldRef transitionField = dispatch.generation().fieldRef(
                "FSM_TRANSITIONS", "[L" + handlers.interfaceName() + ";");
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, resolverField.name(), resolverField.descriptor()));
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, transitionField.name(), transitionField.descriptor()));

        Map<Integer, Integer> slotByPrimary = new LinkedHashMap<>();
        Map<Integer, Integer> slotByKey = new LinkedHashMap<>();
        for (VMDispatchTarget target : dispatch.targets())
        {
            int slot = slotByPrimary.computeIfAbsent(target.primaryKey(), ignored -> slotByPrimary.size());
            slotByKey.putIfAbsent(target.key(), slot);
        }
        int symbolCount = slotByPrimary.size();
        dispatch.generation().onClassInitialize(initializer -> {
            Local symbols = initializer.var("fsmSymbols", "java/util/Map");
            Local transitions = initializer.var("fsmTransitions", transitionField.descriptor());
            initializer.set(symbols, AdvIBdr.newObject("java/util/HashMap"));
            for (Map.Entry<Integer, Integer> entry : slotByKey.entrySet())
            {
                initializer.directCall(mapPut(
                        symbols,
                        integer(AdvIBdr.constant(entry.getKey())),
                        integer(AdvIBdr.constant(entry.getValue()))));
            }
            initializer.set(transitions, AdvIBdr.newArray(
                    handlers.interfaceName(), AdvIBdr.constant(stateCount * symbolCount)));
            for (int state = 0; state < stateCount; state++)
            {
                for (Map.Entry<Integer, Integer> entry : slotByPrimary.entrySet())
                {
                    initializer.setArray(
                            transitions,
                            AdvIBdr.constant(state * symbolCount + entry.getValue()),
                            handlers.newHandler(entry.getKey(), state));
                }
            }
            initializer.set(AdvIBdr.staticField(resolverField), symbols);
            initializer.set(AdvIBdr.staticField(transitionField), transitions);
        });

        AdvIBdr ib = dispatch.instructions();
        InterpretContext runtime = dispatch.runtime();
        Local selector = runtime.intLocal("fsmSymbol", InterpretContext.DISPATCH_SELECTOR);
        Local symbolObject = runtime.local("fsmSymbolObject", "java/lang/Integer", InterpretContext.DISPATCH_SELECTOR + 1);
        Local symbol = runtime.intLocal("fsmSymbolIndex", InterpretContext.DISPATCH_SELECTOR + 2);
        Local state = runtime.intLocal("fsmActiveState", InterpretContext.DISPATCH_SELECTOR + 3);
        Local handler = runtime.local("fsmStateHandler", handlers.interfaceName(), InterpretContext.DISPATCH_SELECTOR + 4);
        Local status = runtime.intLocal("fsmStatus", InterpretContext.DISPATCH_SELECTOR + 5);
        dispatch.setSelector(ib, runtime, selector);
        ib.set(symbolObject, AdvIBdr.cast(
                mapGet(AdvIBdr.staticField(resolverField), integer(selector)), "java/lang/Integer"));
        ib.ifCondition(AdvIBdr.isNull(symbolObject), missing -> missing.gotoLabel(dispatch.unknown()));
        ib.set(symbol, AdvIBdr.callVirtual(symbolObject, "java/lang/Integer", "intValue", "I"));
        ib.set(state, AdvIBdr.callStatic(
                "java/lang/Math", "floorMod", "I", runtime.structureState(), AdvIBdr.constant(stateCount)));
        ib.set(handler, AdvIBdr.arrayAt(
                AdvIBdr.staticField(transitionField),
                AdvIBdr.plus(AdvIBdr.multiply(state, AdvIBdr.constant(symbolCount)), symbol)));
        ib.set(status, handlers.invoke(handler, runtime));
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
