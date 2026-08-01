package nhcm.bytecodevm.config.sdk;

import nhcm.bytecodevm.config.BytecodeVMConfig;
import nhcm.bytecodevm.enums.VMStructure;

/** Nullable SDK overrides; null means use the YAML-derived value. */
public record SdkAnnotationOptions(
        boolean present,
        Boolean enabled,
        VMStructure vmStructure,
        Boolean protectCodePool,
        Boolean virtualizeInstructionAddresses,
        Boolean encryptOperands,
        Boolean perMethodOpcodeMap,
        Boolean shuffleConstants,
        Boolean bindConstantsToOperands,
        Boolean splitCodeStreams,
        Boolean shuffleInstructionBlocks,
        Boolean obfuscateDispatch,
        Boolean dynamicCodePoolBuild,
        Boolean dynamicStateKey,
        Boolean virtualControlFlowGraph,
        Boolean integrityCheck,
        SdkCallPolicy callPolicy,
        Boolean constantFix,
        Boolean superInstruction,
        BytecodeVMConfig.SuperInstructionMode superInstructionMode,
        Integer superInstructionCombineMin,
        Integer superInstructionCombineMax,
        Integer superInstructionMaxHandlers,
        Integer superInstructionMinFrequency)
{
    public static SdkAnnotationOptions empty()
    {
        return new SdkAnnotationOptions(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /** Applies explicitly supplied child values over enclosing class values. */
    public SdkAnnotationOptions overlay(SdkAnnotationOptions child)
    {
        if (!child.present)
        {
            return this;
        }
        return new SdkAnnotationOptions(
                present || child.present,
                choose(enabled, child.enabled),
                choose(vmStructure, child.vmStructure),
                choose(protectCodePool, child.protectCodePool),
                choose(virtualizeInstructionAddresses, child.virtualizeInstructionAddresses),
                choose(encryptOperands, child.encryptOperands),
                choose(perMethodOpcodeMap, child.perMethodOpcodeMap),
                choose(shuffleConstants, child.shuffleConstants),
                choose(bindConstantsToOperands, child.bindConstantsToOperands),
                choose(splitCodeStreams, child.splitCodeStreams),
                choose(shuffleInstructionBlocks, child.shuffleInstructionBlocks),
                choose(obfuscateDispatch, child.obfuscateDispatch),
                choose(dynamicCodePoolBuild, child.dynamicCodePoolBuild),
                choose(dynamicStateKey, child.dynamicStateKey),
                choose(virtualControlFlowGraph, child.virtualControlFlowGraph),
                choose(integrityCheck, child.integrityCheck),
                choose(callPolicy, child.callPolicy),
                choose(constantFix, child.constantFix),
                choose(superInstruction, child.superInstruction),
                choose(superInstructionMode, child.superInstructionMode),
                choose(superInstructionCombineMin, child.superInstructionCombineMin),
                choose(superInstructionCombineMax, child.superInstructionCombineMax),
                choose(superInstructionMaxHandlers, child.superInstructionMaxHandlers),
                choose(superInstructionMinFrequency, child.superInstructionMinFrequency));
    }

    private static <T> T choose(T parent, T child)
    {
        return child == null ? parent : child;
    }

    public enum SdkCallPolicy
    {
        NONE,
        INCLUDE,
        EXCLUDE
    }
}
