package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.local;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public class IncrementBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.INCREMENT.getOpcodes();
    }

    @Override
    public void generate(AdvIBdr ib, InterpretContext context, Opcs opcode)
    {
        if (opcode != Opcs.IINC)
        {
            throw new IllegalArgumentException("Unsupported increment opcode: " + opcode);
        }

        Local localIndex = context.intLocal("localIndex", InterpretContext.RIGHT_VALUE);
        Local increment = context.intLocal("increment", InterpretContext.LEFT_VALUE);

        context.nextOperand(ib, localIndex);
        context.nextOperand(ib, increment);
        ib.setArray(
                context.locals(),
                localIndex,
                AdvIBdr.callStatic(
                        "java/lang/Integer",
                        "valueOf",
                        "java/lang/Integer",
                        AdvIBdr.plus(
                                AdvIBdr.unbox(AdvIBdr.arrayAt(context.locals(), localIndex), "I"),
                                increment)));
    }
}
