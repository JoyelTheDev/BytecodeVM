package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.constant;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

import java.util.Set;

public class LoadConstantBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.LOAD_CONSTANT.getOpcodes();
    }

    @Override
    public void generate(AdvIBdr ib, InterpretContext context, Opcs opcode)
    {
        Local constantIndex = context.intLocal("constantIndex", InterpretContext.RIGHT_VALUE);
        Local constant = context.objectLocal("constant", InterpretContext.DUP_VALUE_1);

        context.nextOperand(ib, constantIndex);
        ib.set(constant, AdvIBdr.arrayAt(context.constants(), constantIndex));
        ib.set(constant, AdvIBdr.callStatic(
                context.vm.owner,
                context.vm.resolveConstant.name(),
                "java/lang/Object",
                context.program(),
                constant,
                context.frame(),
                context.instructionIndex(),
                context.opcode()));

        ib.ifElse(
                AdvIBdr.or(
                        AdvIBdr.isInstanceOf(constant, "java/lang/Long"),
                        AdvIBdr.isInstanceOf(constant, "java/lang/Double")),
                category2 -> pushObjectWithWidth(category2, context, constant, AdvIBdr.constant(2)),
                category1 -> pushObject(category1, context, constant));
    }
}
