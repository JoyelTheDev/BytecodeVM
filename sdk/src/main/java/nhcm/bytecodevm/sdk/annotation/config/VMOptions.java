package nhcm.bytecodevm.sdk.annotation.config;

import nhcm.bytecodevm.sdk.enums.Toggle;
import nhcm.bytecodevm.sdk.enums.VMStructure;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** VM overrides embedded in {@code @Virtualize}. */
@Retention(RetentionPolicy.CLASS)
@Target({})
public @interface VMOptions
{
    /** Selects a concrete VM structure or an automatic strength tier. */
    VMStructure structure() default VMStructure.CONFIG;

    Toggle protectCodePool() default Toggle.CONFIG;

    Toggle virtualizeInstructionAddresses() default Toggle.CONFIG;

    Toggle encryptOperands() default Toggle.CONFIG;

    Toggle perMethodOpcodeMap() default Toggle.CONFIG;

    Toggle shuffleConstants() default Toggle.CONFIG;

    Toggle bindConstantsToOperands() default Toggle.CONFIG;

    Toggle splitCodeStreams() default Toggle.CONFIG;

    Toggle shuffleInstructionBlocks() default Toggle.CONFIG;

    Toggle obfuscateDispatch() default Toggle.CONFIG;

    Toggle dynamicCodePoolBuild() default Toggle.CONFIG;

    Toggle dynamicStateKey() default Toggle.CONFIG;

    Toggle virtualControlFlowGraph() default Toggle.CONFIG;
}
