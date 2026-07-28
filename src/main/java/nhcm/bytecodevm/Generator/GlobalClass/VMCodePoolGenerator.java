package nhcm.bytecodevm.Generator.GlobalClass;

import lombok.Getter;
import nhcm.bytecodevm.Enums.Acc;
import nhcm.bytecodevm.Generator.Abstract.ClassObj;
import nhcm.bytecodevm.Generator.GeneratedMemberNamer;
import nhcm.bytecodevm.Utils.ClassUtils;
import nhcm.bytecodevm.Utils.Builder.MethodRef;
import nhcm.bytecodevm.Utils.MethodUtils;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public class VMCodePoolGenerator extends ClassObj
{
    @Getter
    public final ClassNode classNode;
    public final VMProgramGenerator vmProgramGenerator;
    public final MethodRef find;

    public VMCodePoolGenerator(String className, VMProgramGenerator vmProgramGenerator)
    {
        this(className, vmProgramGenerator, GeneratedMemberNamer.DISABLED);
    }

    public VMCodePoolGenerator(String className, VMProgramGenerator vmProgramGenerator, GeneratedMemberNamer namer)
    {
        super(className);
        this.vmProgramGenerator = vmProgramGenerator;
        this.find = new MethodRef(className, namer.method(className, "find", "(I)" + vmProgramGenerator.descriptor()), "(I)" + vmProgramGenerator.descriptor());

        ClassNode cn = ClassUtils.newClassNode(new Acc[]{Acc.PUBLIC, Acc.INTERFACE, Acc.ABSTRACT}, className);
        MethodNode findMethod = MethodUtils.newMethodNode(new Acc[]{Acc.PUBLIC, Acc.ABSTRACT}, find.name(), find.descriptor());
        cn.methods.add(findMethod);
        this.classNode = cn;
    }
}
