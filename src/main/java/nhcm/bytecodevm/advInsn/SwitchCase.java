package nhcm.bytecodevm.advInsn;

import java.util.function.Consumer;

public record SwitchCase(int key, Consumer<AdvIBdr> body)
{
    public static SwitchCase of(int key, Consumer<AdvIBdr> body)
    {
        return new SwitchCase(key, body);
    }
}
