package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.math;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.math.abstracts.UnaryMathBranch;

public class NegateBranch extends UnaryMathBranch
{
    public NegateBranch()
    {
        super(VMOpcode.NEGATE);
    }

    @Override
    protected Expr operation(Expr value)
    {
        return AdvIBdr.negative(value);
    }
}
