package nhcm.bytecodevm.generator.virtualization.structure.continuation;

import nhcm.bytecodevm.advInsn.AdvIBdr;
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

public final class ContinuationPassingVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    public ContinuationPassingVMGenerator()
    {
        super(VMStructure.CONTINUATION_PASSING);
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvIBdr ib, InterpretContext runtime)
    {
        int key = generation.profile().saltHandler;
        int schedule = key;
        int execute = key ^ 1;
        int exit = key ^ 2;
        Local continuation = runtime.intLocal("continuation", InterpretContext.DISPATCH_SELECTOR + 1);
        Local selector = runtime.intLocal("continuationSelector", InterpretContext.DISPATCH_SELECTOR + 2);
        Local action = runtime.intLocal("continuationAction", InterpretContext.OPCODE);
        ib.set(continuation, AdvIBdr.constant(execute));
        ib.whileLoop(
                AdvIBdr.notEqual(continuation, AdvIBdr.constant(exit)),
                trampoline -> {
                    trampoline.set(selector, AdvIBdr.bitXor(continuation, AdvIBdr.constant(key)));
                    trampoline.ifElse(
                            AdvIBdr.equal(selector, AdvIBdr.constant(0)),
                            next -> next.set(continuation, AdvIBdr.constant(execute)),
                            executeContinuation -> executeContinuation.ifElse(
                                    AdvIBdr.equal(selector, AdvIBdr.constant(1)),
                                    handler -> {
                                        handler.set(action, generation.step(runtime, continuation));
                                        handler.ifElse(
                                                AdvIBdr.equal(action, AdvIBdr.constant(0)),
                                                next -> next.set(continuation, AdvIBdr.constant(schedule)),
                                                done -> done.set(continuation, AdvIBdr.constant(exit)));
                                    },
                                    invalid -> invalid.set(continuation, AdvIBdr.constant(exit))));
                });
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        GeneratedHandlerFamily continuations = GeneratedHandlerFamily.create(dispatch, "Continuation", 2);
        FieldRef continuationTable = dispatch.generation().fieldRef("CONTINUATION_TARGETS", "Ljava/util/Map;");
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL},
                continuationTable.name(), continuationTable.descriptor()));
        Map<Integer, VMDispatchTarget> targets = new LinkedHashMap<>();
        for (VMDispatchTarget target : dispatch.targets())
        {
            targets.putIfAbsent(target.key(), target);
        }
        dispatch.generation().onClassInitialize(initializer -> {
            Local table = initializer.var("continuationTargets", "java/util/Map");
            initializer.set(table, AdvIBdr.newObject("java/util/HashMap"));
            int ordinal = 0;
            for (Map.Entry<Integer, VMDispatchTarget> entry : targets.entrySet())
            {
                Local pair = initializer.var(
                        "continuationPair" + ordinal++, "[L" + continuations.interfaceName() + ";");
                initializer.set(pair, AdvIBdr.newArray(
                        continuations.interfaceName(), AdvIBdr.constant(2)));
                initializer.setArray(pair, AdvIBdr.constant(0),
                                     continuations.newHandler(entry.getValue().primaryKey(), 0));
                initializer.setArray(pair, AdvIBdr.constant(1),
                                     continuations.newHandler(entry.getValue().primaryKey(), 1));
                initializer.directCall(mapPut(table, integer(AdvIBdr.constant(entry.getKey())), pair));
            }
            initializer.set(AdvIBdr.staticField(continuationTable), table);
        });

        AdvIBdr ib = dispatch.instructions();
        InterpretContext runtime = dispatch.runtime();
        Local selector = runtime.intLocal("cpsOpcode", InterpretContext.DISPATCH_SELECTOR);
        Local pair = runtime.local(
                "cpsContinuationPair", "[L" + continuations.interfaceName() + ";",
                InterpretContext.DISPATCH_SELECTOR + 1);
        Local lane = runtime.intLocal("cpsContinuationLane", InterpretContext.DISPATCH_SELECTOR + 2);
        Local handler = runtime.local(
                "cpsContinuationHandler", continuations.interfaceName(), InterpretContext.DISPATCH_SELECTOR + 3);
        Local result = runtime.intLocal("cpsResult", InterpretContext.DISPATCH_SELECTOR + 4);
        dispatch.setSelector(ib, runtime, selector);
        ib.set(pair, AdvIBdr.cast(
                mapGet(AdvIBdr.staticField(continuationTable), integer(selector)),
                "[L" + continuations.interfaceName() + ";"));
        ib.ifCondition(AdvIBdr.isNull(pair), missing -> missing.gotoLabel(dispatch.unknown()));
        ib.set(lane, AdvIBdr.bitAnd(
                AdvIBdr.bitXor(runtime.structureState(), runtime.frameStateKey()),
                AdvIBdr.constant(1)));
        ib.set(handler, AdvIBdr.arrayAt(pair, lane));
        ib.set(result, continuations.invoke(handler, runtime));
        dispatch.finishExternal(ib, result);
    }

    private static nhcm.bytecodevm.advInsn.Expr integer(nhcm.bytecodevm.advInsn.Expr value)
    {
        return AdvIBdr.callStatic("java/lang/Integer", "valueOf", "java/lang/Integer", value);
    }

    private static nhcm.bytecodevm.advInsn.Expr mapGet(nhcm.bytecodevm.advInsn.Expr map, nhcm.bytecodevm.advInsn.Expr key)
    {
        return AdvIBdr.callInterface(
                map, "java/util/Map", "get", "java/lang/Object", AdvIBdr.cast(key, "java/lang/Object"));
    }

    private static nhcm.bytecodevm.advInsn.Expr mapPut(
            nhcm.bytecodevm.advInsn.Expr map,
            nhcm.bytecodevm.advInsn.Expr key,
            nhcm.bytecodevm.advInsn.Expr value)
    {
        return AdvIBdr.callInterface(
                map, "java/util/Map", "put", "java/lang/Object",
                AdvIBdr.cast(key, "java/lang/Object"), AdvIBdr.cast(value, "java/lang/Object"));
    }
}
