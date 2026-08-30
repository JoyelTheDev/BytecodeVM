package nhcm.bytecodevm.generator.watermark;

import org.objectweb.asm.tree.ClassNode;

import java.util.Map;

public record WatermarkPlan(
        String runtimeClass,
        String guardMethod,
        String capsule,
        Map<String, String> metadata,
        ClassNode runtimeClassNode)
{
    public WatermarkPlan
    {
        metadata = Map.copyOf(metadata);
    }
}
