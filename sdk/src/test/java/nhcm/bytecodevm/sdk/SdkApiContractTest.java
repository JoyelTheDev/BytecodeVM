package nhcm.bytecodevm.sdk;

import nhcm.bytecodevm.sdk.annotation.DoNotVirtualize;
import nhcm.bytecodevm.sdk.annotation.ProtectClass;
import nhcm.bytecodevm.sdk.annotation.Virtualize;
import nhcm.bytecodevm.sdk.annotation.config.SuperInstructionOptions;
import nhcm.bytecodevm.sdk.annotation.config.VMOptions;
import nhcm.bytecodevm.sdk.enums.CallPolicy;
import nhcm.bytecodevm.sdk.enums.SuperInstructionMode;
import nhcm.bytecodevm.sdk.enums.Toggle;
import nhcm.bytecodevm.sdk.enums.VMStructure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;

/** Dependency-free API contract checks executed by the SDK build. */
public final class SdkApiContractTest
{
    private SdkApiContractTest()
    {
    }

    public static void main(String[] args) throws Exception
    {
        verifyTarget(Virtualize.class, ElementType.TYPE, ElementType.METHOD);
        verifyTarget(DoNotVirtualize.class, ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR);
        verifyTarget(ProtectClass.class, ElementType.TYPE);
        verifyNested(VMOptions.class);
        verifyNested(SuperInstructionOptions.class);

        require(defaultValue(Virtualize.class, "enabled") == Toggle.ENABLED,
                "@Virtualize must select its target by default");
        require(defaultValue(Virtualize.class, "integrityCheck") == Toggle.CONFIG,
                "integrityCheck must inherit YAML by default");
        require(defaultValue(Virtualize.class, "calls") == CallPolicy.CONFIG,
                "calls must inherit YAML by default");
        require(defaultValue(ProtectClass.class, "constantFix") == Toggle.CONFIG,
                "ProtectClass.constantFix must inherit YAML by default");

        VMOptions vm = (VMOptions) defaultValue(Virtualize.class, "vm");
        require(vm.structure() == VMStructure.CONFIG,
                "VM structure must inherit YAML by default");
        require(vm.protectCodePool() == Toggle.CONFIG,
                "VM toggles must inherit YAML by default");

        SuperInstructionOptions superInstructions =
                (SuperInstructionOptions) defaultValue(Virtualize.class, "superInstructions");
        require(superInstructions.enabled() == Toggle.CONFIG,
                "SuperInstruction enablement must inherit YAML by default");
        require(superInstructions.mode() == SuperInstructionMode.CONFIG,
                "SuperInstruction mode must inherit YAML by default");
        require(superInstructions.combineMin() == SuperInstructionOptions.CONFIG,
                "SuperInstruction numeric values must inherit YAML by default");
    }

    private static void verifyTarget(Class<?> type, ElementType... expected)
    {
        Retention retention = type.getAnnotation(Retention.class);
        require(retention != null && retention.value() == RetentionPolicy.CLASS,
                type.getSimpleName() + " must use CLASS retention");
        Target target = type.getAnnotation(Target.class);
        require(target != null, type.getSimpleName() + " must declare @Target");
        require(EnumSet.copyOf(Arrays.asList(target.value())).equals(
                        EnumSet.copyOf(Arrays.asList(expected))),
                type.getSimpleName() + " has an unexpected target set");
    }

    private static void verifyNested(Class<?> type)
    {
        Retention retention = type.getAnnotation(Retention.class);
        Target target = type.getAnnotation(Target.class);
        require(retention != null && retention.value() == RetentionPolicy.CLASS,
                type.getSimpleName() + " must use CLASS retention");
        require(target != null && target.value().length == 0,
                type.getSimpleName() + " must only be used as a nested annotation value");
    }

    private static Object defaultValue(Class<?> owner, String name) throws Exception
    {
        Method method = owner.getMethod(name);
        return method.getDefaultValue();
    }

    private static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new AssertionError(message);
        }
    }
}
