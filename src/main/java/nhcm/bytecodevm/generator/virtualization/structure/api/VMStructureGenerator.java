package nhcm.bytecodevm.generator.virtualization.structure.api;

import nhcm.bytecodevm.advInsn.AdvIBdr;
import nhcm.bytecodevm.enums.VMStructure;
import nhcm.bytecodevm.generator.virtualization.vminterpret.InterpretContext;

public interface VMStructureGenerator
{
    VMStructure structure();

    VMKernelShape kernelShape();

    default int stepBatchSize()
    {
        return 1;
    }

    void emitScheduler(
            VMStructureGenerationContext generation,
            AdvIBdr instructions,
            InterpretContext runtime);
}
