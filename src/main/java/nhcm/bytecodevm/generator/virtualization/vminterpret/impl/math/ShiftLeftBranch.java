package nhcm.bytecodevm.generator.virtualization.vminterpret.impl.math;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.advInsn.Expr;
import nhcm.bytecodevm.enums.VMOpcode;
import nhcm.bytecodevm.generator.virtualization.vminterpret.impl.math.abstracts.ShiftMathBranch;

public class ShiftLeftBranch extends ShiftMathBranch
{
    public ShiftLeftBranch()
    {
        super(VMOpcode.SHIFT_LEFT);
    }

    @Override
    protected Expr operation(Expr value, Expr distance)
    {
        return AdvIBdr.shiftLeft(value, distance);
    }
}
