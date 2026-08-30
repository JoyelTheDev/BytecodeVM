package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.lowered;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.generator.virtualization.structure.LoweredInstructionPlanner;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public final class DataFlowRegionBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return Set.of(Opcs.DATA_FLOW_REGION);
    }

    @Override
    public void generate(AdvIBdr ib, InterpretContext context, Opcs opcode)
    {
        Local nodeCount = context.intLocal("dataFlowNodeCount", InterpretContext.RIGHT_VALUE);
        Local finalDelta = context.intLocal("dataFlowFinalDelta", InterpretContext.LEFT_VALUE);
        Local payloadSize = context.intLocal("dataFlowPayloadSize", InterpretContext.MIDDLE_VALUE);
        Local index = context.intLocal("dataFlowDecodeIndex", InterpretContext.MIDDLE_VALUE + 1);
        Local value = context.intLocal("dataFlowDecodeValue", InterpretContext.MIDDLE_VALUE + 2);
        Local payload = context.local("dataFlowPayload", "[I", InterpretContext.MIDDLE_VALUE + 3);

        context.nextOperand(ib, nodeCount);
        context.nextOperand(ib, finalDelta);
        ib.set(
                payloadSize,
                AdvIBdr.plus(
                        AdvIBdr.constant(LoweredInstructionPlanner.DATA_FLOW_HEADER_SIZE),
                        AdvIBdr.multiply(
                                nodeCount,
                                AdvIBdr.constant(LoweredInstructionPlanner.DATA_FLOW_NODE_SIZE))));
        ib.set(payload, AdvIBdr.newArray("int", payloadSize));
        ib.setArray(payload, AdvIBdr.constant(0), nodeCount);
        ib.setArray(payload, AdvIBdr.constant(1), finalDelta);
        ib.forLoop(
                b -> b.set(index, AdvIBdr.constant(LoweredInstructionPlanner.DATA_FLOW_HEADER_SIZE)),
                AdvIBdr.lessThan(index, payloadSize),
                b -> b.increment(index, 1),
                b -> {
                    context.nextOperand(b, value);
                    b.setArray(payload, index, value);
                });
        ib.directCall(AdvIBdr.callStatic(
                context.vmClassName,
                context.vm.executeDataFlow.name(),
                "V",
                context.program(),
                context.frame(),
                context.constants(),
                payload,
                context.instructionIndex(),
                context.opcode()));
    }
}
