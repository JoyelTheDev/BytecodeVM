package nhcm.bytecodevm.generator.virtualization.structure.threadedindirect;

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

public final class IndirectThreadedVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    public IndirectThreadedVMGenerator()
    {
        super(VMStructure.THREADED_INDIRECT);
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvIBdr ib, InterpretContext runtime)
    {
        Local continuation = runtime.intLocal("indirectContinuation", InterpretContext.OPCODE);
        ib.set(continuation, AdvIBdr.constant(0));
        ib.whileLoop(
                AdvIBdr.equal(continuation, AdvIBdr.constant(0)),
                trampoline -> trampoline.set(continuation, generation.step(runtime)));
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        GeneratedHandlerFamily handlers = GeneratedHandlerFamily.create(dispatch, "IndirectThread", 1);
        FieldRef resolverField = dispatch.generation().fieldRef("INDIRECT_TOKEN_RESOLVER", "Ljava/util/Map;");
        FieldRef handlersField = dispatch.generation().fieldRef(
                "INDIRECT_THREADED_HANDLERS",
                "[L" + handlers.interfaceName() + ";");
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL},
                resolverField.name(),
                resolverField.descriptor(),
                "Ljava/util/Map<Ljava/lang/Integer;Ljava/lang/Integer;>;"));
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL},
                handlersField.name(),
                handlersField.descriptor()));

        Map<Integer, Integer> slotByPrimary = new LinkedHashMap<>();
        Map<Integer, Integer> slotByKey = new LinkedHashMap<>();
        for (VMDispatchTarget target : dispatch.targets())
        {
            int slot = slotByPrimary.computeIfAbsent(target.primaryKey(), ignored -> slotByPrimary.size());
            slotByKey.putIfAbsent(target.key(), slot);
        }
        dispatch.generation().onClassInitialize(initializer -> {
            Local resolver = initializer.var("indirectTokenResolver", "java/util/Map");
            Local table = initializer.var("indirectHandlerArray", handlersField.descriptor());
            initializer.set(resolver, AdvIBdr.newObject("java/util/HashMap"));
            for (Map.Entry<Integer, Integer> entry : slotByKey.entrySet())
            {
                initializer.directCall(mapPut(
                        resolver,
                        integer(AdvIBdr.constant(entry.getKey())),
                        integer(AdvIBdr.constant(entry.getValue()))));
            }
            initializer.set(AdvIBdr.staticField(resolverField), resolver);
            initializer.set(table, AdvIBdr.newArray(
                    handlers.interfaceName(),
                    AdvIBdr.constant(slotByPrimary.size())));
            for (Map.Entry<Integer, Integer> entry : slotByPrimary.entrySet())
            {
                initializer.setArray(
                        table,
                        AdvIBdr.constant(entry.getValue()),
                        handlers.newHandler(entry.getKey()));
            }
            initializer.set(AdvIBdr.staticField(handlersField), table);
        });

        AdvIBdr ib = dispatch.instructions();
        InterpretContext runtime = dispatch.runtime();
        Local selector = runtime.intLocal("indirectOpcodeIndex", InterpretContext.DISPATCH_SELECTOR);
        Local slotObject = runtime.local(
                "indirectTokenObject",
                "java/lang/Integer",
                InterpretContext.DISPATCH_SELECTOR + 1);
        Local slot = runtime.intLocal("indirectToken", InterpretContext.DISPATCH_SELECTOR + 2);
        Local handler = runtime.local(
                "indirectHandler",
                handlers.interfaceName(),
                InterpretContext.DISPATCH_SELECTOR + 3);
        Local result = runtime.intLocal("indirectResult", InterpretContext.DISPATCH_SELECTOR + 4);
        dispatch.setSelector(ib, runtime, selector);
        ib.set(slotObject, AdvIBdr.cast(
                mapGet(AdvIBdr.staticField(resolverField), integer(selector)),
                "java/lang/Integer"));
        ib.ifCondition(AdvIBdr.isNull(slotObject), missing -> missing.gotoLabel(dispatch.unknown()));
        ib.set(slot, AdvIBdr.callVirtual(slotObject, "java/lang/Integer", "intValue", "I"));
        ib.set(handler, AdvIBdr.arrayAt(AdvIBdr.staticField(handlersField), slot));
        ib.set(result, handlers.invoke(handler, runtime));
        dispatch.finishExternal(ib, result);
    }

    private static nhcm.bytecodevm.advInsn.Expr integer(nhcm.bytecodevm.advInsn.Expr value)
    {
        return AdvIBdr.callStatic("java/lang/Integer", "valueOf", "java/lang/Integer", value);
    }

    private static nhcm.bytecodevm.advInsn.Expr mapGet(
            nhcm.bytecodevm.advInsn.Expr map,
            nhcm.bytecodevm.advInsn.Expr key)
    {
        return AdvIBdr.callInterface(
                map,
                "java/util/Map",
                "get",
                "java/lang/Object",
                AdvIBdr.cast(key, "java/lang/Object"));
    }

    private static nhcm.bytecodevm.advInsn.Expr mapPut(
            nhcm.bytecodevm.advInsn.Expr map,
            nhcm.bytecodevm.advInsn.Expr key,
            nhcm.bytecodevm.advInsn.Expr value)
    {
        return AdvIBdr.callInterface(
                map,
                "java/util/Map",
                "put",
                "java/lang/Object",
                AdvIBdr.cast(key, "java/lang/Object"),
                AdvIBdr.cast(value, "java/lang/Object"));
    }
}
