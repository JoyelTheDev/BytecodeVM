package nhcm.bytecodevm.generator.virtualization.structure.graph;

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

public final class GraphVMGenerator extends AbstractVMStructureGenerator implements VMDispatchGenerator
{
    public GraphVMGenerator()
    {
        super(VMStructure.GRAPH);
    }

    @Override
    public void emitScheduler(VMStructureGenerationContext generation, AdvIBdr ib, InterpretContext runtime)
    {
        Local action = runtime.intLocal("graphAction", InterpretContext.OPCODE);
        Local node = runtime.intLocal("graphNode", InterpretContext.DISPATCH_SELECTOR + 1);
        ib.set(action, AdvIBdr.constant(0));
        ib.set(node, nodeToken(generation, runtime));
        ib.whileLoop(
                AdvIBdr.equal(action, AdvIBdr.constant(0)),
                graph -> {
                    graph.set(action, generation.step(runtime, node));
                    graph.ifCondition(
                            AdvIBdr.equal(action, AdvIBdr.constant(0)),
                            edge -> edge.set(node, AdvIBdr.bitXor(nodeToken(generation, runtime), node)));
                });
    }

    private Expr nodeToken(VMStructureGenerationContext generation, InterpretContext runtime)
    {
        return generation.mix(
                runtime.frameProgramCounter(),
                runtime.frameBlockIndex(),
                runtime.frameStateKey(),
                AdvIBdr.constant(generation.profile().saltBlock));
    }

    @Override
    public void emitDispatch(VMDispatchGenerationContext dispatch)
    {
        GeneratedHandlerFamily nodes = GeneratedHandlerFamily.create(dispatch, "GraphNode", 2);
        FieldRef layersField = dispatch.generation().fieldRef("GRAPH_NODE_LAYERS", "[Ljava/util/Map;");
        dispatch.generation().addField(FieldUtils.newFieldNode(
                new Acc[]{Acc.PRIVATE, Acc.STATIC, Acc.FINAL},
                layersField.name(), layersField.descriptor()));
        Map<Integer, VMDispatchTarget> targets = new LinkedHashMap<>();
        for (VMDispatchTarget target : dispatch.targets())
        {
            targets.putIfAbsent(target.key(), target);
        }
        dispatch.generation().onClassInitialize(initializer -> {
            Local layers = initializer.var("graphNodeLayers", "[Ljava/util/Map;");
            initializer.set(layers, AdvIBdr.newArray("java/util/Map", AdvIBdr.constant(2)));
            for (int layer = 0; layer < 2; layer++)
            {
                Local graph = initializer.var("graphLayer" + layer, "java/util/Map");
                initializer.set(graph, AdvIBdr.newObject(layer == 0
                        ? "java/util/HashMap"
                        : "java/util/LinkedHashMap"));
                for (Map.Entry<Integer, VMDispatchTarget> entry : targets.entrySet())
                {
                    initializer.directCall(mapPut(
                            graph,
                            integer(AdvIBdr.constant(entry.getKey())),
                            nodes.newHandler(entry.getValue().primaryKey(), layer)));
                }
                initializer.setArray(layers, AdvIBdr.constant(layer), graph);
            }
            initializer.set(AdvIBdr.staticField(layersField), layers);
        });

        AdvIBdr ib = dispatch.instructions();
        InterpretContext runtime = dispatch.runtime();
        Local selector = runtime.intLocal("graphOpcode", InterpretContext.DISPATCH_SELECTOR);
        Local layer = runtime.intLocal("graphLayer", InterpretContext.DISPATCH_SELECTOR + 1);
        Local graph = runtime.local("graphNodes", "java/util/Map", InterpretContext.DISPATCH_SELECTOR + 2);
        Local node = runtime.local("graphSemanticNode", nodes.interfaceName(), InterpretContext.DISPATCH_SELECTOR + 3);
        Local status = runtime.intLocal("graphStatus", InterpretContext.DISPATCH_SELECTOR + 4);
        dispatch.setSelector(ib, runtime, selector);
        ib.set(layer, AdvIBdr.bitAnd(
                dispatch.generation().mix(
                        runtime.structureState(), selector, runtime.frameBlockIndex(),
                        AdvIBdr.constant(dispatch.profile().saltBlock)),
                AdvIBdr.constant(1)));
        ib.set(graph, AdvIBdr.arrayAt(AdvIBdr.staticField(layersField), layer));
        ib.set(node, AdvIBdr.cast(mapGet(graph, integer(selector)), nodes.interfaceName()));
        ib.ifCondition(AdvIBdr.isNull(node), missing -> missing.gotoLabel(dispatch.unknown()));
        ib.set(status, nodes.invoke(node, runtime));
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
