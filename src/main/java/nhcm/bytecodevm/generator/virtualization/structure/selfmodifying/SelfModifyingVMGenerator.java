package nhcm.bytecodevm.generator.virtualization.structure.selfmodifying;

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

public final class SelfModifyingVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    public SelfModifyingVMGenerator()
    {
        super(VMStructure.SELF_MODIFYING);
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvIBdr ib, InterpretContext runtime)
    {
        Local mutationResult = runtime.intLocal("mutationResult", InterpretContext.OPCODE);
        ib.set(mutationResult, AdvIBdr.constant(0));
        ib.whileLoop(
                AdvIBdr.equal(mutationResult, AdvIBdr.constant(0)),
                mutation -> mutation.set(mutationResult, generation.step(runtime)));
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        GeneratedHandlerFamily handlers = GeneratedHandlerFamily.create(dispatch, "Mutable", 1);
        FieldRef keysField = dispatch.generation().fieldRef("MUTABLE_HANDLER_KEYS", "[I");
        FieldRef handlersField = dispatch.generation().fieldRef(
                "MUTABLE_HANDLER_RING", "[L" + handlers.interfaceName() + ";");
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, keysField.name(), keysField.descriptor()));
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL}, handlersField.name(), handlersField.descriptor()));

        Map<Integer, VMDispatchTarget> targets = new LinkedHashMap<>();
        for (VMDispatchTarget target : dispatch.targets())
        {
            targets.putIfAbsent(target.key(), target);
        }
        int size = targets.size();
        dispatch.generation().onClassInitialize(initializer -> {
            Local keys = initializer.var("mutableHandlerKeys", "[I");
            Local ring = initializer.var("mutableHandlerRing", handlersField.descriptor());
            initializer.set(keys, AdvIBdr.newArray("int", AdvIBdr.constant(size)));
            initializer.set(ring, AdvIBdr.newArray(handlers.interfaceName(), AdvIBdr.constant(size)));
            int index = 0;
            for (Map.Entry<Integer, VMDispatchTarget> entry : targets.entrySet())
            {
                initializer.setArray(keys, AdvIBdr.constant(index), AdvIBdr.constant(entry.getKey()));
                initializer.setArray(ring, AdvIBdr.constant(index),
                                     handlers.newHandler(entry.getValue().primaryKey()));
                index++;
            }
            initializer.set(AdvIBdr.staticField(keysField), keys);
            initializer.set(AdvIBdr.staticField(handlersField), ring);
        });

        AdvIBdr ib = dispatch.instructions();
        InterpretContext runtime = dispatch.runtime();
        Local selector = runtime.intLocal("mutableSelector", InterpretContext.DISPATCH_SELECTOR);
        Local cursor = runtime.intLocal("mutableCursor", InterpretContext.DISPATCH_SELECTOR + 1);
        Local probes = runtime.intLocal("mutableProbes", InterpretContext.DISPATCH_SELECTOR + 2);
        Local handler = runtime.local(
                "mutableHandler", handlers.interfaceName(), InterpretContext.DISPATCH_SELECTOR + 3);
        Local result = runtime.intLocal("mutableResult", InterpretContext.DISPATCH_SELECTOR + 4);
        dispatch.setSelector(ib, runtime, selector);
        ib.set(cursor, AdvIBdr.callStatic(
                "java/lang/Math", "floorMod", "I",
                dispatch.generation().mix(
                        selector, runtime.frameStateKey(), runtime.instructionIndex(),
                        AdvIBdr.constant(dispatch.profile().saltHandler)),
                AdvIBdr.constant(size)));
        ib.set(probes, AdvIBdr.constant(0));
        ib.set(handler, AdvIBdr.constant(null));
        ib.whileLoop(
                AdvIBdr.and(
                        AdvIBdr.lessThan(probes, AdvIBdr.constant(size)),
                        AdvIBdr.isNull(handler)),
                probe -> {
                    probe.ifCondition(
                            AdvIBdr.equal(
                                    AdvIBdr.arrayAt(AdvIBdr.staticField(keysField), cursor),
                                    selector),
                            found -> found.set(handler, AdvIBdr.arrayAt(
                                    AdvIBdr.staticField(handlersField), cursor)));
                    probe.set(cursor, AdvIBdr.callStatic(
                            "java/lang/Math", "floorMod", "I",
                            AdvIBdr.plus(cursor, AdvIBdr.constant(1)),
                            AdvIBdr.constant(size)));
                    probe.increment(probes, 1);
                });
        ib.ifCondition(AdvIBdr.isNull(handler), missing -> missing.gotoLabel(dispatch.unknown()));
        ib.set(result, handlers.invoke(handler, runtime));
        dispatch.finishExternal(ib, result);
    }
}
