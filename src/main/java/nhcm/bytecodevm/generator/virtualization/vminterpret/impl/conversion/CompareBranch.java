package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.conversion;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Condition;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.advInsn.Local;
import nhcm.bytecodevm.enums.Opcs;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretBranch;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;
import nhcm.bytecodevm.generator.virtualization.vminterpret.NumericType;

import java.util.Set;

public class CompareBranch extends InterpretBranch
{
    @Override
    public Set<Opcs> opcodes()
    {
        return VMOpcode.COMPARE.getOpcodes();
    }

    @Override
    public void generate(AdvIBdr ib, InterpretContext context, Opcs opcode)
    {
        NumericType type = NumericType.fromOpcode(opcode);
        Local result = context.intLocal("compareResult", InterpretContext.MIDDLE_VALUE);

        popNumber(ib, context, type, InterpretContext.RIGHT_VALUE);
        popNumber(ib, context, type, InterpretContext.LEFT_VALUE);

        if (opcode == Opcs.LCMP)
        {
            compareOrdered(ib, result, context.leftValue(type), context.rightValue(type));
        }
        else
        {
            compareFloating(ib, opcode, result, context.leftValue(type), context.rightValue(type));
        }

        pushInt(ib, context, result);
    }

    private static void compareFloating(
            AdvIBdr ib,
            Opcs opcode,
            Local result,
            Expr left,
            Expr right)
    {
        boolean nanAsGreater = opcode == Opcs.FCMPG || opcode == Opcs.DCMPG;
        Condition hasNaN = switch (opcode)
        {
            case FCMPL, FCMPG -> AdvIBdr.or(
                    AdvIBdr.isTrue(AdvIBdr.callStatic("java/lang/Float", "isNaN", "Z", left)),
                    AdvIBdr.isTrue(AdvIBdr.callStatic("java/lang/Float", "isNaN", "Z", right)));
            case DCMPL, DCMPG -> AdvIBdr.or(
                    AdvIBdr.isTrue(AdvIBdr.callStatic("java/lang/Double", "isNaN", "Z", left)),
                    AdvIBdr.isTrue(AdvIBdr.callStatic("java/lang/Double", "isNaN", "Z", right)));
            default -> throw new IllegalArgumentException("Not a floating compare opcode: " + opcode);
        };

        ib.ifElse(
                hasNaN,
                b -> b.set(result, AdvIBdr.constant(nanAsGreater ? 1 : -1)),
                b -> compareOrdered(b, result, left, right));
    }

    private static void compareOrdered(AdvIBdr ib, Local result, Expr left, Expr right)
    {
        ib.ifElse(
                AdvIBdr.greaterThan(left, right),
                b -> b.set(result, AdvIBdr.constant(1)),
                b -> b.ifElse(
                        AdvIBdr.equal(left, right),
                        equal -> equal.set(result, AdvIBdr.constant(0)),
                        less -> less.set(result, AdvIBdr.constant(-1))));
    }
}
