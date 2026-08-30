package nhcm.bytecodevm.advInsn;

import nhcm.bytecodevm.utils.builder.InsnBuilder;
import org.objectweb.asm.Type;

public interface Expr
{
    Type type();

    void emit(InsnBuilder builder);

    default void emit(AdvIBdr builder)
    {
        emit(builder.rawBuilder());
    }

    default String source()
    {
        return "<expr>";
    }
}
