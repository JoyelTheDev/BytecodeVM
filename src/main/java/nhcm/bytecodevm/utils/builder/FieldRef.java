package nhcm.bytecodevm.utils.builder;

import nhcm.bytecodevm.advInsn.AdvIBdr;

public record FieldRef(String owner, String name, String descriptor)
{
    public void get(InsnBuilder ib)
    {
        ib.getField(this);
    }

    public void get(AdvIBdr ib)
    {
        get(ib.rawBuilder());
    }

    public void readField(AdvIBdr ib)
    {
        get(ib);
    }

    public void put(InsnBuilder ib)
    {
        ib.putField(this);
    }

    public void put(AdvIBdr ib)
    {
        put(ib.rawBuilder());
    }

    public void writeField(AdvIBdr ib)
    {
        put(ib);
    }

    public void getStatic(InsnBuilder ib)
    {
        ib.getStatic(this);
    }

    public void getStatic(AdvIBdr ib)
    {
        getStatic(ib.rawBuilder());
    }

    public void readStaticField(AdvIBdr ib)
    {
        getStatic(ib);
    }

    public void putStatic(InsnBuilder ib)
    {
        ib.putStatic(this);
    }

    public void putStatic(AdvIBdr ib)
    {
        putStatic(ib.rawBuilder());
    }

    public void writeStaticField(AdvIBdr ib)
    {
        putStatic(ib);
    }
}
