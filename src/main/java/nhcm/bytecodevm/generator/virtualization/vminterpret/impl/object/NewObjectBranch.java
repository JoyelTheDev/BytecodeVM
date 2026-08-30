package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.object;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public class NewObjectBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.NEW_OBJECT.getOpcodes();
    }

    @Override
    public void generate(AdvIBdr ib, InterpretContext context, Opcs opcode)
    {
        var classIndex = context.intLocal("classIndex", InterpretContext.JUMP_TARGET);
        var marker = context.local("identityMarker", "[Ljava/lang/Object;", InterpretContext.FIELD_VALUE);

        context.nextOperand(ib, classIndex);
        ib.set(marker, AdvIBdr.newArray("java/lang/Object", AdvIBdr.constant(1)));
        ib.setArray(marker, AdvIBdr.constant(0), AdvIBdr.arrayAt(context.constants(), classIndex));
        pushObject(ib, context, marker);
    }
}
