package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.array;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public class ArrayLengthBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.ARRAY_LENGTH.getOpcodes();
    }

    @Override
    public void generate(AdvIBdr ib, InterpretContext context, Opcs opcode)
    {
        popObject(ib, context);
        pushInt(ib, context, AdvIBdr.callStatic(
                "java/lang/reflect/Array",
                "getLength",
                "I",
                context.stackObject()));
    }
}
