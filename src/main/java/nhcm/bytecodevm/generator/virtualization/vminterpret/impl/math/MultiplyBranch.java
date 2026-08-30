package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.math;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.math.abstracts.BinaryMathBranch;

public class MultiplyBranch extends BinaryMathBranch
{
    public MultiplyBranch()
    {
        super(VMOpcode.MULTIPLY);
    }

    @Override
    protected Expr operation(Expr left, Expr right)
    {
        return AdvIBdr.multiply(left, right);
    }
}
