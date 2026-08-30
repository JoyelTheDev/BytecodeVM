package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.math;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.math.abstracts.BinaryMathBranch;

public class SubtractBranch extends BinaryMathBranch
{
    public SubtractBranch()
    {
        super(VMOpcode.SUBTRACT);
    }

    @Override
    protected Expr operation(Expr left, Expr right)
    {
        return AdvIBdr.minus(left, right);
    }
}
