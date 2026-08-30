package nhcm.bytecodevm.sdk.watermark;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable metadata extracted from a BytecodeVM-protected JAR. */
public final class WatermarkInfo
{
    private final Map<String, String> values;

    WatermarkInfo(Map<String, String> values)
    {
        this.values = Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
    }

    public Map<String, String> values()
    {
        return values;
    }

    public String get(String key)
    {
        return values.get(key);
    }

    public String artifactId()
    {
        return get("bytecodevm.artifactId");
    }

    public String protectedAt()
    {
        return get("bytecodevm.protectedAt");
    }

    public String toolVersion()
    {
        return get("bytecodevm.version");
    }

    public String inputSha256()
    {
        return get("bytecodevm.inputSha256");
    }

    public Map<String, String> userValues()
    {
        Map<String, String> result = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> entry : values.entrySet())
        {
            if (entry.getKey().startsWith("user."))
            {
                result.put(entry.getKey().substring("user.".length()), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
