package nhcm.bytecodevm.config.sdk;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.enums.VMStructure;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static nhcm.bytecodevm.config.sdk.SdkAnnotationOptions.SdkCallPolicy;

/** Reads BytecodeVM SDK annotations without introducing a runtime SDK dependency. */
public final class SdkAnnotationReader
{
    public static final String SDK_PREFIX = "Lnhcm/bytecodevm/sdk/";
    public static final String VIRTUALIZE = SDK_PREFIX + "annotation/Virtualize;";
    public static final String PROTECT_CLASS = SDK_PREFIX + "annotation/ProtectClass;";
    public static final String DO_NOT_VIRTUALIZE = SDK_PREFIX + "annotation/DoNotVirtualize;";

    private SdkAnnotationReader()
    {
    }

    public static ClassDirectives classDirectives(ClassNode owner)
    {
        AnnotationNode virtualize = find(owner.visibleAnnotations, owner.invisibleAnnotations, VIRTUALIZE);
        AnnotationNode protectClass = find(owner.visibleAnnotations, owner.invisibleAnnotations, PROTECT_CLASS);
        boolean excluded = find(
                owner.visibleAnnotations,
                owner.invisibleAnnotations,
                DO_NOT_VIRTUALIZE) != null;
        SdkAnnotationOptions options = parseVirtualize(virtualize, owner.name);
        Boolean protectConstantFix = protectClass == null
                ? null
                : toggle(values(protectClass).get("constantFix"), null, owner.name + " @ProtectClass.constantFix");
        Boolean constantFix = options.constantFix() != null
                ? options.constantFix()
                : protectConstantFix;
        return new ClassDirectives(protectClass != null, excluded, options, constantFix);
    }

    public static MethodDirectives methodDirectives(ClassNode owner, MethodNode method)
    {
        ClassDirectives classDirectives = classDirectives(owner);
        AnnotationNode annotation = find(
                method.visibleAnnotations,
                method.invisibleAnnotations,
                VIRTUALIZE);
        boolean methodExcluded = find(
                method.visibleAnnotations,
                method.invisibleAnnotations,
                DO_NOT_VIRTUALIZE) != null;
        SdkAnnotationOptions methodOptions = parseVirtualize(
                annotation,
                owner.name + '.' + method.name + method.desc);
        SdkAnnotationOptions effective = classDirectives.options.overlay(methodOptions);
        boolean excluded = classDirectives.excluded || methodExcluded || Boolean.FALSE.equals(effective.enabled());
        boolean selected = !excluded && Boolean.TRUE.equals(effective.enabled());
        return new MethodDirectives(
                annotation != null,
                selected,
                excluded,
                effective);
    }

    public static BytecodeVMConfig applyMethodOverrides(
            BytecodeVMConfig yamlConfig,
            ClassNode owner,
            MethodNode method)
    {
        SdkAnnotationOptions options = methodDirectives(owner, method).options;
        BytecodeVMConfig.BytecodeVMConfigBuilder builder = yamlConfig.toBuilder();
        if (options.vmStructure() != null)
        {
            builder.vmStructure(options.vmStructure());
        }
        if (options.protectCodePool() != null)
        {
            builder.protectCodePool(options.protectCodePool());
        }
        if (options.virtualizeInstructionAddresses() != null)
        {
            builder.virtualizeInstructionAddresses(options.virtualizeInstructionAddresses());
        }
        if (options.encryptOperands() != null)
        {
            builder.encryptOperands(options.encryptOperands());
        }
        if (options.perMethodOpcodeMap() != null)
        {
            builder.perMethodOpcodeMap(options.perMethodOpcodeMap());
        }
        if (options.shuffleConstants() != null)
        {
            builder.shuffleConstants(options.shuffleConstants());
        }
        if (options.bindConstantsToOperands() != null)
        {
            builder.bindConstantsToOperands(options.bindConstantsToOperands());
        }
        if (options.splitCodeStreams() != null)
        {
            builder.splitCodeStreams(options.splitCodeStreams());
        }
        if (options.shuffleInstructionBlocks() != null)
        {
            builder.shuffleInstructionBlocks(options.shuffleInstructionBlocks());
        }
        if (options.obfuscateDispatch() != null)
        {
            builder.obfuscateDispatch(options.obfuscateDispatch());
        }
        if (options.dynamicCodePoolBuild() != null)
        {
            builder.dynamicCodePoolBuild(options.dynamicCodePoolBuild());
        }
        if (options.dynamicStateKey() != null)
        {
            builder.dynamicStateKey(options.dynamicStateKey());
        }
        if (options.virtualControlFlowGraph() != null)
        {
            builder.virtualControlFlowGraph(options.virtualControlFlowGraph());
        }
        if (options.integrityCheck() != null)
        {
            builder.vmIntegrityCheck(options.integrityCheck());
        }
        if (options.callPolicy() != null)
        {
            builder.includeMethodsCalledWithin(options.callPolicy() == SdkCallPolicy.INCLUDE);
            builder.excludeMethodsCalledWithin(options.callPolicy() == SdkCallPolicy.EXCLUDE);
        }
        if (options.superInstruction() != null)
        {
            builder.superInstruction(options.superInstruction());
        }
        if (options.superInstructionMode() != null)
        {
            builder.superInstructionMode(options.superInstructionMode());
        }
        if (options.superInstructionCombineMin() != null)
        {
            builder.superInstructionCombineMin(options.superInstructionCombineMin());
        }
        if (options.superInstructionCombineMax() != null)
        {
            builder.superInstructionCombineMax(options.superInstructionCombineMax());
        }
        if (options.superInstructionMaxHandlers() != null)
        {
            builder.superInstructionMaxHandlers(options.superInstructionMaxHandlers());
        }
        if (options.superInstructionMinFrequency() != null)
        {
            builder.superInstructionMinFrequency(options.superInstructionMinFrequency());
        }
        BytecodeVMConfig result = builder.build();
        validate(result, owner.name + '.' + method.name + method.desc);
        return result;
    }

    private static SdkAnnotationOptions parseVirtualize(AnnotationNode annotation, String target)
    {
        if (annotation == null)
        {
            return SdkAnnotationOptions.empty();
        }
        Map<String, Object> root = values(annotation);
        AnnotationNode vm = nested(root.get("vm"), target + " @Virtualize.vm");
        AnnotationNode superInstructions = nested(
                root.get("superInstructions"),
                target + " @Virtualize.superInstructions");
        Map<String, Object> vmValues = values(vm);
        Map<String, Object> superValues = values(superInstructions);

        return new SdkAnnotationOptions(
                true,
                toggle(root.get("enabled"), true, target + " @Virtualize.enabled"),
                vmStructure(vmValues.get("structure"), target),
                toggle(vmValues.get("protectCodePool"), null, target),
                toggle(vmValues.get("virtualizeInstructionAddresses"), null, target),
                toggle(vmValues.get("encryptOperands"), null, target),
                toggle(vmValues.get("perMethodOpcodeMap"), null, target),
                toggle(vmValues.get("shuffleConstants"), null, target),
                toggle(vmValues.get("bindConstantsToOperands"), null, target),
                toggle(vmValues.get("splitCodeStreams"), null, target),
                toggle(vmValues.get("shuffleInstructionBlocks"), null, target),
                toggle(vmValues.get("obfuscateDispatch"), null, target),
                toggle(vmValues.get("dynamicCodePoolBuild"), null, target),
                toggle(vmValues.get("dynamicStateKey"), null, target),
                toggle(vmValues.get("virtualControlFlowGraph"), null, target),
                toggle(root.get("integrityCheck"), null, target + " @Virtualize.integrityCheck"),
                callPolicy(root.get("calls"), target),
                toggle(root.get("constantFix"), null, target + " @Virtualize.constantFix"),
                toggle(superValues.get("enabled"), null, target),
                superInstructionMode(superValues.get("mode"), target),
                integer(superValues.get("combineMin"), target),
                integer(superValues.get("combineMax"), target),
                integer(superValues.get("maxHandlers"), target),
                integer(superValues.get("minFrequency"), target));
    }

    private static void validate(BytecodeVMConfig config, String target)
    {
        if (config.superInstructionCombineMin < 2 || config.superInstructionCombineMin > 32 ||
            config.superInstructionCombineMax < 2 || config.superInstructionCombineMax > 32 ||
            config.superInstructionCombineMin > config.superInstructionCombineMax)
        {
            throw new IllegalArgumentException(
                    "Invalid SDK SuperInstruction combine range on " + target +
                            ": [" + config.superInstructionCombineMin + ", " +
                            config.superInstructionCombineMax + "]");
        }
        if (config.superInstructionMaxHandlers < 1 || config.superInstructionMaxHandlers > 4096)
        {
            throw new IllegalArgumentException(
                    "Invalid SDK SuperInstruction maxHandlers on " + target +
                            ": " + config.superInstructionMaxHandlers);
        }
        if (config.superInstructionMinFrequency < 1 || config.superInstructionMinFrequency > 1_000_000)
        {
            throw new IllegalArgumentException(
                    "Invalid SDK SuperInstruction minFrequency on " + target +
                            ": " + config.superInstructionMinFrequency);
        }
    }

    private static VMStructure vmStructure(Object value, String target)
    {
        String name = enumName(value, target + " VM structure");
        return name == null || "CONFIG".equals(name) || "INHERIT".equals(name)
                ? null
                : VMStructure.parse(name);
    }

    private static BytecodeVMConfig.SuperInstructionMode superInstructionMode(Object value, String target)
    {
        String name = enumName(value, target + " SuperInstruction mode");
        return name == null || "CONFIG".equals(name) || "INHERIT".equals(name)
                ? null
                : BytecodeVMConfig.SuperInstructionMode.valueOf(name);
    }

    private static SdkCallPolicy callPolicy(Object value, String target)
    {
        String name = enumName(value, target + " call policy");
        return name == null || "CONFIG".equals(name) || "INHERIT".equals(name)
                ? null
                : SdkCallPolicy.valueOf(name);
    }

    private static Boolean toggle(Object value, Boolean absentDefault, String target)
    {
        String name = enumName(value, target);
        if (name == null)
        {
            return absentDefault;
        }
        return switch (name)
        {
            case "CONFIG", "INHERIT" -> null;
            case "ENABLE", "ENABLED" -> true;
            case "DISABLE", "DISABLED" -> false;
            default -> throw new IllegalArgumentException("Unknown SDK toggle " + name + " on " + target);
        };
    }

    private static Integer integer(Object value, String target)
    {
        if (value == null)
        {
            return null;
        }
        if (!(value instanceof Integer number))
        {
            throw new IllegalArgumentException("Expected an integer SDK option on " + target);
        }
        return number == -1 ? null : number;
    }

    private static String enumName(Object value, String target)
    {
        if (value == null)
        {
            return null;
        }
        if (!(value instanceof String[] enumValue) || enumValue.length != 2)
        {
            throw new IllegalArgumentException("Expected an enum SDK option on " + target);
        }
        return enumValue[1];
    }

    private static AnnotationNode nested(Object value, String target)
    {
        if (value == null)
        {
            return null;
        }
        if (!(value instanceof AnnotationNode annotation))
        {
            throw new IllegalArgumentException("Expected a nested SDK annotation on " + target);
        }
        return annotation;
    }

    private static Map<String, Object> values(AnnotationNode annotation)
    {
        Map<String, Object> result = new HashMap<>();
        if (annotation == null || annotation.values == null)
        {
            return result;
        }
        for (int index = 0; index < annotation.values.size(); index += 2)
        {
            result.put((String) annotation.values.get(index), annotation.values.get(index + 1));
        }
        return result;
    }

    private static AnnotationNode find(
            List<AnnotationNode> visible,
            List<AnnotationNode> invisible,
            String descriptor)
    {
        AnnotationNode found = find(visible, descriptor);
        return found != null ? found : find(invisible, descriptor);
    }

    private static AnnotationNode find(List<AnnotationNode> annotations, String descriptor)
    {
        if (annotations == null)
        {
            return null;
        }
        for (AnnotationNode annotation : annotations)
        {
            if (descriptor.equals(annotation.desc))
            {
                return annotation;
            }
        }
        return null;
    }

    public record ClassDirectives(
            boolean protectClass,
            boolean excluded,
            SdkAnnotationOptions options,
            Boolean constantFix)
    {
        public boolean included()
        {
            return protectClass || Boolean.TRUE.equals(options.enabled());
        }
    }

    public record MethodDirectives(
            boolean methodAnnotation,
            boolean selected,
            boolean excluded,
            SdkAnnotationOptions options)
    {
    }
}
