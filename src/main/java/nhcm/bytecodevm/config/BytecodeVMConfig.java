package nhcm.bytecodevm.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Builder;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Builder
public class BytecodeVMConfig
{
    public final Path inputFile;
    public final Path outputFile;
    public final VMCreateMode createMode;
    public final VMLocation location;
    public final MutateMode mutateMode;
    public final RenameMode renameMode;
    public final InterpretMode interpretMode;
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

    public enum MutateMode
    {
        ALL_RANDOM_INT,
        ALL_RESORT,
        ALL_AUTO_CHOOSE,
        NO_CHANGE
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
        JsonObject json = new Gson().fromJson(Files.newBufferedReader(file), JsonObject.class);
        MatchRules matchRules = MatchRules.parse(json);
        String[] includes = matchRules.includes("all");
        String[] exclusions = matchRules.exclusions("all");
        int[] superInstructionRange = optionalIntRange(
                json,
                "superInstructionCombineRange",
                "superinstrcutioncombinerange",
                2,
                5,
                2,
                32);
        return BytecodeVMConfig
                .builder()
                .inputFile(Path.of(requiredString(json, "input")))
                .outputFile(Path.of(requiredString(json, "output")))
                .createMode(VMCreateMode.valueOf(requiredString(json, "createMode")))
                .location(VMLocation.valueOf(requiredString(json, "location")))
                .mutateMode(MutateMode.valueOf(requiredString(json, "mutateMode")))
                .interpretMode(InterpretMode.valueOf(requiredString(json, "interpretMode")))
                .renameMode(RenameMode.valueOf(requiredString(json, "renameMode")))
                .protectCodePool(optionalBoolean(json, "protectCodePool", true))
                .virtualizeInstructionAddresses(optionalBoolean(json, "virtualizeInstructionAddresses", true))
                .encryptOperands(optionalBoolean(json, "encryptOperands", true))
                .perMethodOpcodeMap(optionalBoolean(json, "perMethodOpcodeMap", true))
                .shuffleConstants(optionalBoolean(json, "shuffleConstants", true))
                .bindConstantsToOperands(optionalBoolean(json, "bindConstantsToOperands", true))
                .splitCodeStreams(optionalBoolean(json, "splitCodeStreams", true))
                .shuffleInstructionBlocks(optionalBoolean(json, "shuffleInstructionBlocks", true))
                .obfuscateDispatch(optionalBoolean(json, "obfuscateDispatch", true))
                .dynamicCodePoolBuild(optionalBoolean(json, "dynamicCodePoolBuild", true))
                .dynamicStateKey(optionalBoolean(json, "dynamicStateKey", true))
                .virtualControlFlowGraph(optionalBoolean(json, "virtualControlFlowGraph", true))
                .superInstruction(optionalBoolean(json, "superInstruction", false, "superinstrcution"))
                .superInstructionCombineMin(superInstructionRange[0])
                .superInstructionCombineMax(superInstructionRange[1])
                .superInstructionMode(optionalEnum(json, "superInstructionMode", SuperInstructionMode.HYBRID))
                .superInstructionMaxHandlers(optionalInt(json, "superInstructionMaxHandlers", 128, 1, 4096))
                .superInstructionMinFrequency(optionalInt(json, "superInstructionMinFrequency", 2, 1, 1_000_000))
                .vmCount(optionalInt(json, "vmCount", 1, 1, 1024))
                .includes(includes)
                .exclusions(exclusions)
                .matchRules(matchRules)
                .build();
    }

    public BytecodeVMConfig forMethod(ClassNode owner, MethodNode method)
    {
        return BytecodeVMConfig
                .builder()
                .inputFile(inputFile)
                .outputFile(outputFile)
                .createMode(createMode)
                .location(location)
                .mutateMode(mutateMode)
                .interpretMode(interpretMode)
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
    }

    private boolean statementEnabled(String key, boolean baseValue, ClassNode owner, MethodNode method)
    {
        return baseValue && matchRules.statementMatches(key, owner, method);
    }

    private static boolean optionalBoolean(JsonObject json, String key, boolean defaultValue)
    {
        return optionalBoolean(json, key, defaultValue, new String[0]);
    }

    private static boolean optionalBoolean(JsonObject json, String key, boolean defaultValue, String... aliases)
    {
        JsonElement value = json.get(key);
        if(value == null || value.isJsonNull())
        {
            for (String alias : aliases)
            {
                value = json.get(alias);
                if(value != null && !value.isJsonNull())
                {
                    break;
                }
            }
        }
        if(value == null || value.isJsonNull())
        {
            return defaultValue;
        }
        return value.getAsBoolean();
    }

    private static int optionalInt(JsonObject json, String key, int defaultValue, int minValue, int maxValue)
    {
        JsonElement value = json.get(key);
        if(value == null || value.isJsonNull())
        {
            return defaultValue;
        }
        int result = value.getAsInt();
        if(result < minValue || result > maxValue)
        {
            throw new IllegalArgumentException(
                    "Config value " + key + " must be between " + minValue + " and " + maxValue);
        }
        return result;
    }

    private static <T extends Enum<T>> T optionalEnum(JsonObject json, String key, T defaultValue)
    {
        JsonElement value = json.get(key);
        if(value == null || value.isJsonNull())
        {
            return defaultValue;
        }
        return Enum.valueOf(defaultValue.getDeclaringClass(), value.getAsString());
    }

    private static int[] optionalIntRange(
            JsonObject json,
            String key,
            String alias,
            int defaultMin,
            int defaultMax,
            int minValue,
            int maxValue)
    {
        JsonElement value = json.get(key);
        if(value == null || value.isJsonNull())
        {
            value = json.get(alias);
        }
        if(value == null || value.isJsonNull())
        {
            return new int[]{defaultMin, defaultMax};
        }
        if(!value.isJsonArray() || value.getAsJsonArray().size() != 2)
        {
            throw new IllegalArgumentException("Config value must be a two-item array: " + key);
        }
        int min = value.getAsJsonArray().get(0).getAsInt();
        int max = value.getAsJsonArray().get(1).getAsInt();
        if(min < minValue || max > maxValue || min > max)
        {
            throw new IllegalArgumentException(
                    "Config value " + key + " must be between " + minValue + " and " + maxValue + " with min <= max");
        }
        return new int[]{min, max};
    }

    private static String requiredString(JsonObject json, String key)
    {
        JsonElement value = json.get(key);
        if(value == null || value.isJsonNull())
        {
            throw new IllegalArgumentException("Missing required config value: " + key);
        }
        return value.getAsString();
    }

    private static JsonArray requiredArray(JsonObject json, String key)
    {
        JsonElement value = json.get(key);
        if(value == null || value.isJsonNull())
        {
            throw new IllegalArgumentException("Missing required config array: " + key);
        }
        if(!value.isJsonArray())
        {
            throw new IllegalArgumentException("Config value must be an array: " + key);
        }
        return value.getAsJsonArray();
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

        private static MatchRules parse(JsonObject json)
        {
            return new MatchRules(
                    parseRuleGroups(json, "includes"),
                    parseRuleGroups(json, "exclusions"));
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

        private static Map<String, String[]> parseRuleGroups(JsonObject json, String key)
        {
            JsonElement value = json.get(key);
            if(value == null || value.isJsonNull())
            {
                throw new IllegalArgumentException("Missing required match rules: " + key);
            }
            Map<String, String[]> result = new HashMap<>();
            if(value.isJsonArray())
            {
                result.put("all", readRuleArray(value.getAsJsonArray()));
                return Map.copyOf(result);
            }
            if(!value.isJsonObject())
            {
                throw new IllegalArgumentException("Config value must be an array or object: " + key);
            }
            JsonObject groups = value.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : groups.entrySet())
            {
                if(!entry.getValue().isJsonArray())
                {
                    throw new IllegalArgumentException("Match group must be an array: " + key + "." + entry.getKey());
                }
                result.put(entry.getKey(), readRuleArray(entry.getValue().getAsJsonArray()));
            }
            result.putIfAbsent("all", new String[0]);
            return Map.copyOf(result);
        }

        private static String[] readRuleArray(JsonArray array)
        {
            String[] values = new String[array.size()];
            for(int i = 0; i < array.size(); i++)
            {
                values[i] = array.get(i).getAsString();
            }
            return values;
        }
    }
}
