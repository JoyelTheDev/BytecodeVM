package nhcm.bytecodevm.utils.builder;

import nhcm.bytecodevm.advInsn.AdvIBdr;

public record MethodRef(String owner, String name, String descriptor)
{
    public void invokeVirtual(InsnBuilder ib)
    {
        ib.invokeVirtual(this);
    }

    public void invokeVirtual(AdvIBdr ib)
    {
        invokeVirtual(ib.rawBuilder());
    }

    public void callVirtualMethod(AdvIBdr ib)
    {
        invokeVirtual(ib);
    }

    public void invokeStatic(InsnBuilder ib)
    {
        ib.invokeStatic(this);
    }

    public void invokeStatic(AdvIBdr ib)
    {
        invokeStatic(ib.rawBuilder());
    }

    public void callStaticMethod(AdvIBdr ib)
    {
        invokeStatic(ib);
    }

    public void invokeSpecial(InsnBuilder ib)
    {
        ib.invokeSpecial(this);
    }

    public void invokeSpecial(AdvIBdr ib)
    {
        invokeSpecial(ib.rawBuilder());
    }

    public void callSpecialMethod(AdvIBdr ib)
    {
        invokeSpecial(ib);
    }

    public void invokeInterface(InsnBuilder ib)
    {
        ib.invokeInterface(this);
    }

    public void invokeInterface(AdvIBdr ib)
    {
        invokeInterface(ib.rawBuilder());
    }

    public void callInterfaceMethod(AdvIBdr ib)
    {
        invokeInterface(ib);
    }
}
