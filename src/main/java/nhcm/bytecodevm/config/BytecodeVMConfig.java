package nhcm.bytecodevm.config;

import lombok.Builder;
import nhcm.bytecodevm.config.sdk.SdkAnnotationReader;
import nhcm.bytecodevm.enums.VMStructure;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Builder(toBuilder = true)
public class BytecodeVMConfig
{
    public final Path inputFile;
    public final Path outputFile;
    public final VMCreateMode createMode;
    public final VMLocation location;
    public final RenameMode renameMode;
    public final InterpretMode interpretMode;
    public final VMStructure vmStructure;
    public final boolean protectCodePool;
    public final boolean virtualizeInstructionAddresses;
    public final boolean encryptOperands;
    public final boolean perMethodOpcodeMap;
    public final boolean shuffleConstants;
    public final boolean bindConstantsToOperands;
    public final boolean splitCodeStreams;
    public final boolean shuffleInstructionBlocks;
    public final boolean obfuscateDispatch;
    public final boolean dynamicCodePoolBuild;
    public final boolean dynamicStateKey;
    public final boolean virtualControlFlowGraph;
    public final boolean constantFix;
    public final boolean removeAnnotations;
    public final boolean includeMethodsCalledWithin;
    public final boolean excludeMethodsCalledWithin;
    public final boolean virtualizeInvocationBridges;
    public final boolean vmIntegrityCheck;
    public final double vmIntegrityCheckRatio;
    public final int vmIntegrityRecheckInterval;
    public final boolean superInstruction;
    public final int superInstructionCombineMin;
    public final int superInstructionCombineMax;
    public final SuperInstructionMode superInstructionMode;
    public final int superInstructionMaxHandlers;
    public final int superInstructionMinFrequency;
    public final int vmCount;
    public final String[] includes;
    public final String[] exclusions;
    public final MatchRules matchRules;

    public enum VMCreateMode
    {
        ONE_FOR_ALL,
        PER_METHOD,
        PER_CLASS,
        PER_PACKAGE
    }

    public enum VMLocation
    {
        SAME_PACKAGE_AS_TARGET,
        NEW_PACKAGE,
        ONE_PACKAGE
    }

    public enum RenameMode
    {
        ENABLE,
        DISABLE
    }

    public enum InterpretMode
    {
        SAVE_ALL_INSTRUCTION,
        SAVE_ONLY_REQUIRED_INSTRUCTION
    }

    public enum SuperInstructionMode
    {
        RANDOM,
        PATTERN,
        HYBRID
    }

    public static BytecodeVMConfig parse(Path file) throws IOException
    {
        String fileStr = Files.readString(file);
        Map<String, Object> yaml = ConfigDocumentParser.parse(fileStr, file);
        return parse(yaml, requiredString(yaml, "input"), requiredString(yaml, "output"));
    }

    public static BytecodeVMConfig parse(String config)
    {
        Map<String, Object> yaml = ConfigDocumentParser.parse(config);
        return parse(yaml, requiredString(yaml, "input"), requiredString(yaml, "output"));
    }

    public static BytecodeVMConfig parse(String config, String input, String output)
    {
        return parse(ConfigDocumentParser.parse(config), input, output);
    }

    private static BytecodeVMConfig parse(Map<String, Object> yaml, String input, String output)
    {
        MatchRules matchRules = MatchRules.parse(yaml);
        String[] includes = matchRules.includes("all");
        String[] exclusions = matchRules.exclusions("all");
        int[] superInstructionRange = optionalIntRange(
                yaml,
                "superInstructionCombineRange",
                "superinstrcutioncombinerange",
                2,
                5,
                2,
                32);
        return BytecodeVMConfig
                .builder()
                .inputFile(Path.of(input))
                .outputFile(Path.of(output))
                .createMode(VMCreateMode.valueOf(requiredString(yaml, "createMode")))
                .location(VMLocation.valueOf(requiredString(yaml, "location")))
                .interpretMode(InterpretMode.valueOf(requiredString(yaml, "interpretMode")))
                .vmStructure(optionalVMStructure(yaml, "vmStructure", VMStructure.MEDIUM))
                .renameMode(RenameMode.valueOf(requiredString(yaml, "renameMode")))
                .protectCodePool(optionalBoolean(yaml, "protectCodePool", true))
                .virtualizeInstructionAddresses(optionalBoolean(yaml, "virtualizeInstructionAddresses", true))
                .encryptOperands(optionalBoolean(yaml, "encryptOperands", true))
                .perMethodOpcodeMap(optionalBoolean(yaml, "perMethodOpcodeMap", true))
                .shuffleConstants(optionalBoolean(yaml, "shuffleConstants", true))
                .bindConstantsToOperands(optionalBoolean(yaml, "bindConstantsToOperands", true))
                .splitCodeStreams(optionalBoolean(yaml, "splitCodeStreams", true))
                .shuffleInstructionBlocks(optionalBoolean(yaml, "shuffleInstructionBlocks", true))
                .obfuscateDispatch(optionalBoolean(yaml, "obfuscateDispatch", true))
                .dynamicCodePoolBuild(optionalBoolean(yaml, "dynamicCodePoolBuild", true))
                .dynamicStateKey(optionalBoolean(yaml, "dynamicStateKey", true))
                .virtualControlFlowGraph(optionalBoolean(yaml, "virtualControlFlowGraph", true))
                .constantFix(optionalBoolean(yaml, "constantFix", false, "fixConstants"))
                .removeAnnotations(optionalBoolean(yaml, "removeAnnotations", true))
                .includeMethodsCalledWithin(optionalBoolean(yaml, "includeMethodsCalledWithin", false))
                .excludeMethodsCalledWithin(optionalBoolean(yaml, "excludeMethodsCalledWithin", false))
                .virtualizeInvocationBridges(optionalBoolean(yaml, "virtualizeInvocationBridges", false))
                .vmIntegrityCheck(optionalBoolean(yaml, "vmIntegrityCheck", false))
                .vmIntegrityCheckRatio(optionalDouble(yaml, "vmIntegrityCheckRatio", 1.0D, 0.0D, 1.0D))
                .vmIntegrityRecheckInterval(optionalInt(
                        yaml,
                        "vmIntegrityRecheckInterval",
                        65_536,
                        0,
                        16_777_216))
                .superInstruction(optionalBoolean(yaml, "superInstruction", false, "superinstrcution"))
                .superInstructionCombineMin(superInstructionRange[0])
                .superInstructionCombineMax(superInstructionRange[1])
                .superInstructionMode(optionalEnum(yaml, "superInstructionMode", SuperInstructionMode.HYBRID))
                .superInstructionMaxHandlers(optionalInt(yaml, "superInstructionMaxHandlers", 128, 1, 4096))
                .superInstructionMinFrequency(optionalInt(yaml, "superInstructionMinFrequency", 2, 1, 1_000_000))
                .vmCount(optionalInt(yaml, "vmCount", 1, 1, 1024))
                .includes(includes)
                .exclusions(exclusions)
                .matchRules(matchRules)
                .build();
    }

    public BytecodeVMConfig forMethod(ClassNode owner, MethodNode method)
    {
        BytecodeVMConfig yamlConfig = BytecodeVMConfig
                .builder()
                .inputFile(inputFile)
                .outputFile(outputFile)
                .createMode(createMode)
                .location(location)
                .interpretMode(interpretMode)
                .vmStructure(vmStructure)
                .renameMode(renameMode)
                .protectCodePool(statementEnabled("protectCodePool", protectCodePool, owner, method))
                .virtualizeInstructionAddresses(statementEnabled("virtualizeInstructionAddresses", virtualizeInstructionAddresses, owner, method))
                .encryptOperands(statementEnabled("encryptOperands", encryptOperands, owner, method))
                .perMethodOpcodeMap(statementEnabled("perMethodOpcodeMap", perMethodOpcodeMap, owner, method))
                .shuffleConstants(statementEnabled("shuffleConstants", shuffleConstants, owner, method))
                .bindConstantsToOperands(statementEnabled("bindConstantsToOperands", bindConstantsToOperands, owner, method))
                .splitCodeStreams(statementEnabled("splitCodeStreams", splitCodeStreams, owner, method))
                .shuffleInstructionBlocks(statementEnabled("shuffleInstructionBlocks", shuffleInstructionBlocks, owner, method))
                .obfuscateDispatch(statementEnabled("obfuscateDispatch", obfuscateDispatch, owner, method))
                .dynamicCodePoolBuild(statementEnabled("dynamicCodePoolBuild", dynamicCodePoolBuild, owner, method))
                .dynamicStateKey(statementEnabled("dynamicStateKey", dynamicStateKey, owner, method))
                .virtualControlFlowGraph(statementEnabled("virtualControlFlowGraph", virtualControlFlowGraph, owner, method))
                .constantFix(constantFix)
                .removeAnnotations(removeAnnotations)
                .includeMethodsCalledWithin(includeMethodsCalledWithin)
                .excludeMethodsCalledWithin(excludeMethodsCalledWithin)
                .virtualizeInvocationBridges(virtualizeInvocationBridges)
                .vmIntegrityCheck(vmIntegrityCheck)
                .vmIntegrityCheckRatio(vmIntegrityCheckRatio)
                .vmIntegrityRecheckInterval(vmIntegrityRecheckInterval)
                .superInstruction(statementEnabled("superInstruction", superInstruction, owner, method))
                .superInstructionCombineMin(superInstructionCombineMin)
                .superInstructionCombineMax(superInstructionCombineMax)
                .superInstructionMode(superInstructionMode)
                .superInstructionMaxHandlers(superInstructionMaxHandlers)
                .superInstructionMinFrequency(superInstructionMinFrequency)
                .vmCount(vmCount)
                .includes(includes)
                .exclusions(exclusions)
                .matchRules(matchRules)
                .build();
        return SdkAnnotationReader.applyMethodOverrides(yamlConfig, owner, method);
    }

    public BytecodeVMConfig integrityConfig()
    {
        return BytecodeVMConfig
                .builder()
                .inputFile(inputFile)
                .outputFile(outputFile)
                .createMode(VMCreateMode.PER_METHOD)
                .location(location)
                .interpretMode(InterpretMode.SAVE_ALL_INSTRUCTION)
                .vmStructure(VMStructure.HIGH)
                .renameMode(renameMode)
                .protectCodePool(true)
                .virtualizeInstructionAddresses(true)
                .encryptOperands(true)
                .perMethodOpcodeMap(true)
                .shuffleConstants(true)
                .bindConstantsToOperands(true)
                .splitCodeStreams(true)
                .shuffleInstructionBlocks(true)
                .obfuscateDispatch(true)
                .dynamicCodePoolBuild(true)
                .dynamicStateKey(true)
                .virtualControlFlowGraph(true)
                .constantFix(false)
                .removeAnnotations(removeAnnotations)
                .includeMethodsCalledWithin(false)
                .excludeMethodsCalledWithin(false)
                .virtualizeInvocationBridges(false)
                .vmIntegrityCheck(false)
                .vmIntegrityCheckRatio(0.0D)
                .vmIntegrityRecheckInterval(0)
                .superInstruction(superInstruction)
                .superInstructionCombineMin(superInstructionCombineMin)
                .superInstructionCombineMax(superInstructionCombineMax)
                .superInstructionMode(superInstructionMode)
                .superInstructionMaxHandlers(superInstructionMaxHandlers)
                .superInstructionMinFrequency(superInstructionMinFrequency)
                .vmCount(1)
                .includes(new String[]{"*"})
                .exclusions(new String[0])
                .matchRules(MatchRules.empty())
                .build();
    }

    public BytecodeVMConfig resolveVMStructure()
    {
        return vmStructure.isAutomatic()
                ? toBuilder().vmStructure(vmStructure.resolveAuto()).build()
                : this;
    }

    private boolean statementEnabled(String key, boolean baseValue, ClassNode owner, MethodNode method)
    {
        return baseValue && matchRules.statementMatches(key, owner, method);
    }

    private static boolean optionalBoolean(Map<String, Object> yaml, String key, boolean defaultValue)
    {
        return optionalBoolean(yaml, key, defaultValue, new String[0]);
    }

    private static boolean optionalBoolean(
            Map<String, Object> yaml,
            String key,
            boolean defaultValue,
            String... aliases)
    {
        Object value = yaml.get(key);
        if (value == null)
        {
            for (String alias : aliases)
            {
                value = yaml.get(alias);
                if (value != null)
                {
                    break;
                }
            }
        }
        if (value == null)
        {
            return defaultValue;
        }
        if (!(value instanceof Boolean result))
        {
            throw typeError(key, "a boolean");
        }
        return result;
    }

    private static int optionalInt(
            Map<String, Object> yaml,
            String key,
            int defaultValue,
            int minValue,
            int maxValue)
    {
        Object value = yaml.get(key);
        if (value == null)
        {
            return defaultValue;
        }
        int result = integer(value, key);
        if(result < minValue || result > maxValue)
        {
            throw new IllegalArgumentException(
                    "Config value " + key + " must be between " + minValue + " and " + maxValue);
        }
        return result;
    }

    private static double optionalDouble(
            Map<String, Object> yaml,
            String key,
            double defaultValue,
            double minValue,
            double maxValue)
    {
        Object value = yaml.get(key);
        if (value == null)
        {
            return defaultValue;
        }
        if (!(value instanceof Number number))
        {
            throw typeError(key, "a number");
        }
        double result = number.doubleValue();
        if(result < minValue || result > maxValue)
        {
            throw new IllegalArgumentException(
                    "Config value " + key + " must be between " + minValue + " and " + maxValue);
        }
        return result;
    }

    private static <T extends Enum<T>> T optionalEnum(
            Map<String, Object> yaml,
            String key,
            T defaultValue)
    {
        Object value = yaml.get(key);
        if (value == null)
        {
            return defaultValue;
        }
        if (!(value instanceof String text))
        {
            throw typeError(key, "a string");
        }
        return Enum.valueOf(defaultValue.getDeclaringClass(), text);
    }

    private static VMStructure optionalVMStructure(
            Map<String, Object> yaml,
            String key,
            VMStructure defaultValue)
    {
        Object value = yaml.get(key);
        if (value == null)
        {
            return defaultValue;
        }
        if (!(value instanceof String text))
        {
            throw typeError(key, "a string");
        }
        return VMStructure.parse(text);
    }

    private static int[] optionalIntRange(
            Map<String, Object> yaml,
            String key,
            String alias,
            int defaultMin,
            int defaultMax,
            int minValue,
            int maxValue)
    {
        Object value = yaml.get(key);
        if (value == null)
        {
            value = yaml.get(alias);
        }
        if (value == null)
        {
            return new int[]{defaultMin, defaultMax};
        }
        if (!(value instanceof List<?> range) || range.size() != 2)
        {
            throw new IllegalArgumentException("Config value must be a two-item array: " + key);
        }
        int min = integer(range.get(0), key + "[0]");
        int max = integer(range.get(1), key + "[1]");
        if(min < minValue || max > maxValue || min > max)
        {
            throw new IllegalArgumentException(
                    "Config value " + key + " must be between " + minValue + " and " + maxValue + " with min <= max");
        }
        return new int[]{min, max};
    }

    private static String requiredString(Map<String, Object> yaml, String key)
    {
        Object value = yaml.get(key);
        if (value == null)
        {
            throw new IllegalArgumentException("Missing required config value: " + key);
        }
        if (!(value instanceof String result))
        {
            throw typeError(key, "a string");
        }
        return result;
    }

    private static int integer(Object value, String key)
    {
        if (!(value instanceof Number number))
        {
            throw typeError(key, "an integer");
        }
        long result = number.longValue();
        if (number.doubleValue() != result || result < Integer.MIN_VALUE || result > Integer.MAX_VALUE)
        {
            throw typeError(key, "a 32-bit integer");
        }
        return (int) result;
    }

    private static IllegalArgumentException typeError(String key, String expected)
    {
        return new IllegalArgumentException("Config value " + key + " must be " + expected);
    }

    public static final class MatchRules
    {
        private final Map<String, String[]> includes;
        private final Map<String, String[]> exclusions;
        private final Map<String, TargetMatcher> includeMatchers;
        private final Map<String, TargetMatcher> excludeMatchers;

        private MatchRules(Map<String, String[]> includes, Map<String, String[]> exclusions)
        {
            this.includes = includes;
            this.exclusions = exclusions;
            this.includeMatchers = createMatchers(includes);
            this.excludeMatchers = createMatchers(exclusions);
        }

        private static MatchRules parse(Map<String, Object> yaml)
        {
            return new MatchRules(
                    parseRuleGroups(yaml, "includes"),
                    parseRuleGroups(yaml, "exclusions"));
        }

        private static MatchRules empty()
        {
            return new MatchRules(Map.of(), Map.of());
        }

        public String[] includes(String key)
        {
            return includes.getOrDefault(key, new String[0]);
        }

        public String[] exclusions(String key)
        {
            return exclusions.getOrDefault(key, new String[0]);
        }

        public boolean statementMatches(String key, ClassNode owner, MethodNode method)
        {
            TargetMatcher include = includeMatchers.get(key);
            boolean included = include == null || include.isMethodMatched(owner, method);
            TargetMatcher exclude = excludeMatchers.get(key);
            boolean excluded = exclude != null && exclude.isMethodMatched(owner, method);
            return included && !excluded;
        }

        private static Map<String, TargetMatcher> createMatchers(Map<String, String[]> groups)
        {
            Map<String, TargetMatcher> result = new HashMap<>();
            for (Map.Entry<String, String[]> entry : groups.entrySet())
            {
                if (entry.getValue().length == 0)
                {
                    continue;
                }
                TargetMatcher matcher = new TargetMatcher();
                for (String rule : entry.getValue())
                {
                    matcher.add(rule);
                }
                result.put(entry.getKey(), matcher);
            }
            return Map.copyOf(result);
        }

        private static Map<String, String[]> parseRuleGroups(Map<String, Object> yaml, String key)
        {
            Object value = yaml.get(key);
            if (value == null)
            {
                throw new IllegalArgumentException("Missing required match rules: " + key);
            }
            Map<String, String[]> result = new HashMap<>();
            if (value instanceof List<?> rules)
            {
                result.put("all", readRuleArray(rules, key));
                return Map.copyOf(result);
            }
            if (!(value instanceof Map<?, ?> groups))
            {
                throw new IllegalArgumentException("Config value must be a list or map: " + key);
            }
            for (Map.Entry<?, ?> entry : groups.entrySet())
            {
                if (!(entry.getKey() instanceof String groupName))
                {
                    throw new IllegalArgumentException("Match group names must be strings: " + key);
                }
                if (!(entry.getValue() instanceof List<?> groupRules))
                {
                    throw new IllegalArgumentException("Match group must be a list: " + key + "." + groupName);
                }
                result.put(groupName, readRuleArray(groupRules, key + "." + groupName));
            }
            result.putIfAbsent("all", new String[0]);
            return Map.copyOf(result);
        }

        private static String[] readRuleArray(List<?> rules, String key)
        {
            String[] values = new String[rules.size()];
            for(int index = 0; index < rules.size(); index++)
            {
                Object value = rules.get(index);
                if (!(value instanceof String rule))
                {
                    throw typeError(key + '[' + index + ']', "a string");
                }
                values[index] = rule;
            }
            return values;
        }
    }
}
