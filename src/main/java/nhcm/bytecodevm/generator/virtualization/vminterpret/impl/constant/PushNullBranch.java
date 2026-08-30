package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.constant;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public class PushNullBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.PUSH_NULL.getOpcodes();
    }

    @Override
    public void generate(AdvIBdr ib, InterpretContext context, Opcs opcode)
    {
        pushObject(ib, context, AdvIBdr.nullValue("java/lang/Object"));
    }
}
