package nhcm.bytecodevm.generator.virtualization;

import nhcm.bytecodevm.utils.RandomUtils;

public class VMObfProfile
{
    public final int mixSeed;
    public final int mixRoundA;
    public final int mixRoundB;
    public final int mixRoundC;
    public final int mixMulA;
    public final int mixMulB;
    public final int saltLayout;
    public final int saltOpcode;
    public final int saltOpcodeMap;
    public final int saltOperand;
    public final int saltConstant;
    public final int saltHandler;
    public final int saltArray;
    public final int saltString;
    public final int saltState;
    public final int saltBlock;
    public final int dispatchSalt;

    private VMObfProfile(
            int mixSeed,
            int mixRoundA,
            int mixRoundB,
            int mixRoundC,
            int mixMulA,
            int mixMulB,
            int saltLayout,
            int saltOpcode,
            int saltOpcodeMap,
            int saltOperand,
            int saltConstant,
            int saltHandler,
            int saltArray,
            int saltString,
            int saltState,
            int saltBlock,
            int dispatchSalt)
    {
        this.mixSeed = mixSeed;
        this.mixRoundA = mixRoundA;
        this.mixRoundB = mixRoundB;
        this.mixRoundC = mixRoundC;
        this.mixMulA = mixMulA | 1;
        this.mixMulB = mixMulB | 1;
        this.saltLayout = saltLayout;
        this.saltOpcode = saltOpcode;
        this.saltOpcodeMap = saltOpcodeMap;
        this.saltOperand = saltOperand;
        this.saltConstant = saltConstant;
        this.saltHandler = saltHandler;
        this.saltArray = saltArray;
        this.saltString = saltString;
        this.saltState = saltState;
        this.saltBlock = saltBlock;
        this.dispatchSalt = dispatchSalt;
    }

    public static VMObfProfile random()
    {
        return new VMObfProfile(
                nonZeroRandom(),
                nonZeroRandom(),
                nonZeroRandom(),
                nonZeroRandom(),
                nonZeroRandom(),
                nonZeroRandom(),
                nonZeroRandom(),
                nonZeroRandom(),
                nonZeroRandom(),
                nonZeroRandom(),
                nonZeroRandom(),
                nonZeroRandom(),
                nonZeroRandom(),
                nonZeroRandom(),
                nonZeroRandom(),
                nonZeroRandom(),
                nonZeroRandom());
    }

    public int mix(int key, int a, int b, int c)
    {
        int x = key ^ mixSeed;
        x ^= a + mixRoundA + (x << 6) + (x >>> 2);
        x ^= b + mixRoundB + (x << 6) + (x >>> 2);
        x ^= c + mixRoundC + (x << 6) + (x >>> 2);
        x ^= x >>> 16;
        x *= mixMulA;
        x ^= x >>> 15;
        x *= mixMulB;
        x ^= x >>> 16;
        return x;
    }

    public int layoutMix(int methodKey, int stateKey, int slot, int field)
    {
        return mix(methodKey ^ stateKey, slot, field, saltLayout);
    }

    public int stateMix(int methodKey, int slot)
    {
        return mix(methodKey, slot, saltState, 0);
    }

    public int blockMix(int methodKey, int blockIndex, int field)
    {
        return mix(methodKey, blockIndex, field, saltBlock);
    }

    public int opcodeMix(int methodKey, int stateKey, int virtualPc, int slot)
    {
        return mix(methodKey ^ stateKey, virtualPc, slot, saltOpcode);
    }

    public int opcodeMapMix(int methodKey, int stateKey, int virtualOpcode)
    {
        return mix(methodKey ^ stateKey, virtualOpcode, saltOpcodeMap, 0);
    }

    public int operandMix(int methodKey, int stateKey, int opcode, int virtualPc, int operandIndex, int operandPosition)
    {
        return mix((methodKey ^ stateKey) ^ opcode, virtualPc, operandIndex, saltOperand ^ operandPosition);
    }

    public int constantMix(int methodKey, int stateKey, int opcode, int virtualPc, int operandIndex)
    {
        return mix((methodKey ^ stateKey) ^ opcode, virtualPc, operandIndex, saltConstant);
    }

    public int handlerMix(int methodKey, int handlerSlot, int field)
    {
        return mix(methodKey, handlerSlot, field, saltHandler);
    }

    public int arrayMix(int key, int index)
    {
        return mix(key, index, saltArray, 0);
    }

    public int stringMix(int key, int index)
    {
        return mix(key, index, saltString, 0);
    }

    public int dispatchKey(int opcode)
    {
        return mix(dispatchSalt, opcode, saltOpcode, 0);
    }

    private static int nonZeroRandom()
    {
        int value;
        do
        {
            value = RandomUtils.randomInt();
        } while (value == 0);
        return value;
    }
}
