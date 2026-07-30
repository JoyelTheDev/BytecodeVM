# BytecodeVM

(Credit to GPT 5.5)

BytecodeVM is a Java bytecode virtualizing obfuscator.
It rewrites selected Java methods into a compact virtual bytecode program, injects a generated VM, and executes the protected logic through that VM at runtime.
All process does not require compilation of native codes or dynamic library, the VM is written in bytecodes, so it is cross-platform.

This obfuscator is intended for demonstration purposes only and is not suitable for production use.
It can make your program hundreds of times slower, while the quality of its protection is not guaranteed.
This obfuscator may not even provide protection comparable to existing virtualization tools such as V\*P or The\*ida, or even basic bytecode-to-native obfuscation tools such as JN\*C.
Its purpose is to demonstrate the concept of bytecode virtualization.

## Build

This project uses JDK 21

```powershell
.\gradlew.bat build
```

The runnable fat jar is generated at:

```text
build/libs/BytecodeVM.jar
```

## Usage

Create a default config:

```powershell
java -jar build\libs\BytecodeVM.jar --defaultconfig
```

Run obfuscation:

```powershell
java -jar build\libs\BytecodeVM.jar --config defaultconfig.json
```

Set log level when debugging:

```powershell
java "-Dbytecodevm.log.level=DEBUG" -jar build\libs\BytecodeVM.jar --config defaultconfig.json
```

## Config

```jsonc
{
  "input": "./input.jar",
  "output": "./output.jar",
  "createMode": "ONE_FOR_ALL", // ONE_FOR_ALL, PER_METHOD, PER_CLASS, PER_PACKAGE
  "location": "ONE_PACKAGE", // SAME_PACKAGE_AS_TARGET, NEW_PACKAGE, ONE_PACKAGE
  "mutateMode": "ALL_RANDOM_INT", // ALL_RANDOM_INT, ALL_RESORT, ALL_AUTO_CHOOSE, NO_CHANGE
  "renameMode": "DISABLE", // ENABLE, DISABLE
  "interpretMode": "SAVE_ONLY_REQUIRED_INSTRUCTION", // SAVE_ALL_INSTRUCTION, SAVE_ONLY_REQUIRED_INSTRUCTION
  "protectCodePool": true,
  "virtualizeInstructionAddresses": true,
  "encryptOperands": true,
  "perMethodOpcodeMap": true,
  "shuffleConstants": true,
  "bindConstantsToOperands": true,
  "splitCodeStreams": true,
  "shuffleInstructionBlocks": true,
  "obfuscateDispatch": true,
  "dynamicCodePoolBuild": true,
  "dynamicStateKey": true,
  "virtualControlFlowGraph": true,
  "constantFix": false,
  "includeMethodsCalledWithin": false,
  "excludeMethodsCalledWithin": false,
  "superInstruction": true,
  "superInstructionCombineRange": [2, 5],
  "superInstructionMode": "HYBRID",
  "superInstructionMaxHandlers": 128,
  "superInstructionMinFrequency": 2,
  "vmCount": 4,
  "includes": {
    "all": ["*", "* *(*)*"],
    "protectCodePool": ["* @Sensitive *(*)*"],
    "encryptOperands": ["com.example.secure.* *(*)*"],
    "obfuscateDispatch": ["* *(*)*"],
    "constantFix": ["com.example.secure.* *"],
    "superInstruction": ["com.example.hot.* *(*)*"]
  },
  "exclusions": {
    "all": ["* <init>(*)V", "* <clinit>()V"],
    "dynamicStateKey": ["* fastPath(*)*"]
  }
}
```

### Options

`input`, `output`, `createMode`, `location`, `mutateMode`, `renameMode`, `interpretMode`, `includes`, and `exclusions` are required. The boolean protection fields are optional and default to `true` when omitted, except `constantFix` and `superInstruction`, which default to `false`.

| Field | Values | Default  | Description |
|---|---|----------|---|
| `input` | Path | Required | Input jar to transform. |
| `output` | Path | Required | Output jar path. |
| `createMode` | `ONE_FOR_ALL`, `PER_METHOD`, `PER_CLASS`, `PER_PACKAGE` | Required | Controls how VM classes are grouped. |
| `location` | `SAME_PACKAGE_AS_TARGET`, `NEW_PACKAGE`, `ONE_PACKAGE` | Required | Controls where generated VM classes are placed. |
| `mutateMode` | `ALL_RANDOM_INT`, `ALL_RESORT`, `ALL_AUTO_CHOOSE`, `NO_CHANGE` | Required | Controls opcode mutation strategy. |
| `renameMode` | `ENABLE`, `DISABLE` | Required | Controls renaming behavior. |
| `interpretMode` | `SAVE_ALL_INSTRUCTION`, `SAVE_ONLY_REQUIRED_INSTRUCTION` | Required | Controls how many interpreter branches are emitted. |
| `protectCodePool` | `true`, `false` | `true`   | Enables code-pool protection. When disabled, most protection sub-options below have no effect. |
| `virtualizeInstructionAddresses` | `true`, `false` | `true`   | Encodes virtual instruction addresses instead of using direct layout addresses. |
| `encryptOperands` | `true`, `false` | `true`   | Encrypts virtual instruction operands. |
| `perMethodOpcodeMap` | `true`, `false` | `true`   | Uses method-specific opcode mapping and decoding. |
| `shuffleConstants` | `true`, `false` | `true`   | Shuffles constants stored in generated VM programs. |
| `bindConstantsToOperands` | `true`, `false` | `true`   | Binds constant references to operand data so constant indexes are not stored plainly. |
| `splitCodeStreams` | `true`, `false` | `true`   | Splits VM program data into separate code/layout/operand streams. |
| `shuffleInstructionBlocks` | `true`, `false` | `true`   | Shuffles virtual instruction blocks before writing code-pool data. |
| `obfuscateDispatch` | `true`, `false` | `true`   | Obfuscates interpreter dispatch selection. |
| `dynamicCodePoolBuild` | `true`, `false` | `true`   | Builds code-pool program data dynamically in generated bytecode instead of storing everything plainly. |
| `dynamicStateKey` | `true`, `false` | `true`   | Adds per-instruction runtime state keys used by opcode, layout, and operand decoding. |
| `virtualControlFlowGraph` | `true`, `false` | `true`   | Stores methods as shuffled virtual basic blocks and resolves instruction indexes through block-local lookup. |
| `constantFix` | `true`, `false` | `false`  | Moves `ConstantValue` data from static final fields into `<clinit>` assignments and clears the field value attribute. |
| `includeMethodsCalledWithin` | `true`, `false` | `false`  | Recursively includes target-jar methods called from explicitly included methods. |
| `excludeMethodsCalledWithin` | `true`, `false` | `false`  | Recursively excludes target-jar methods called from explicitly included methods. |
| `superInstruction` | `true`, `false` | `false`  | Fuses safe VM instruction sequences into synthetic super instructions with generated handlers. |
| `superInstructionCombineRange` | `[min, max]` | `[2, 5]` | Minimum and maximum VM instruction count to fuse into one super instruction. |
| `superInstructionMode` | `RANDOM`, `PATTERN`, `HYBRID` | `HYBRID` | Chooses random ranges, frequent opcode patterns, or both. |
| `superInstructionMaxHandlers` | `1` to `4096` | `128`    | Caps generated super-instruction recipes per VM set. |
| `superInstructionMinFrequency` | Positive integer | `2`      | Minimum pattern frequency before `PATTERN` or `HYBRID` pre-registers a recipe. |
| `vmCount` | `1` to `1024` | `4`      | Expands each non-`PER_METHOD` VM grouping into up to this many randomized VM sets and distributes matched methods among them. |
| `includes` | Array or object of match expressions | Required | Methods/classes to virtualize, plus optional per-boolean include groups. |
| `exclusions` | Array or object of match expressions | Required | Methods/classes to skip, plus optional per-boolean exclude groups. Exclusions win over includes. |

## Super Instructions

When `superInstruction` is enabled, the generator scans VM instructions inside safe basic-block regions and replaces selected instruction sequences with one synthetic `SUPER_INSTRUCTION`. The synthetic instruction stores a generated recipe id followed by the flattened operands of the fused instructions. At runtime the VM dispatches once, reads the recipe id, and expands the original interpreter branch bodies inside a generated super handler.

`superInstructionMode` controls selection:

| Mode | Behavior |
|---|---|
| `RANDOM` | Randomly chooses fusable ranges within `superInstructionCombineRange`. |
| `PATTERN` | Registers frequent opcode sequences and fuses only matching patterns. |
| `HYBRID` | Uses frequent patterns first, then randomly fuses remaining safe ranges. |

The first implementation intentionally avoids crossing jump targets, try/catch boundaries, returns, throws, switches, monitor instructions, field/invoke/object/array operations, division, and remainder. That keeps exception mapping and virtual control flow stable while still reducing dispatch overhead for common stack/local/math sequences.

## Constant Fix

When `constantFix` is enabled, fields with a `ConstantValue` attribute are rewritten from field metadata into bytecode initialization:

```text
static final int VALUE = 123; // ConstantValue attribute
```

becomes an assignment emitted before the first `RETURN` in `<clinit>`:

```text
ldc/const 123
putstatic Owner.VALUE : I
```

Then the field value attribute is cleared. This currently applies only to `static final` fields whose constants are valid JVM `ConstantValue` types: primitive values and `String`. It can be controlled with `includes.constantFix` and `exclusions.constantFix`; class and field match rules are both supported.

## Called Method Expansion

`includeMethodsCalledWithin` and `excludeMethodsCalledWithin` expand method selection through calls found inside explicitly included methods. A root method is one that matches `includes.all`, matches a method rule, is not excluded by `exclusions.all`, and is otherwise eligible for virtualization.

When `includeMethodsCalledWithin` is enabled, the scanner recursively follows `INVOKE*` instructions from those root methods. If the called method exists in the input jar and is eligible for virtualization, it is added even if it did not match the original include method pattern.

When `excludeMethodsCalledWithin` is enabled, the same discovered called methods are removed from virtualization. If both include and exclude call expansion affect a method, exclusion wins. Explicit exclusions still win over call expansion.

## Include / Exclude Match Expressions

`includes` and `exclusions` use the same matcher syntax. A target is selected when it matches an include rule and does not match an exclusion rule. Exclusions always win.

The legacy array form is still supported:

```jsonc
{
  "includes": ["*", "* *(*)*"],
  "exclusions": ["* <init>(*)V", "* <clinit>()V"]
}
```

You can also use grouped object form:

```jsonc
{
  "includes": {
    "all": ["*", "* *(*)*"],
    "protectCodePool": ["* @Sensitive *(*)*"],
    "encryptOperands": ["com.example.secure.* *(*)*"]
  },
  "exclusions": {
    "all": ["* <init>(*)V", "* <clinit>()V"],
    "dynamicStateKey": ["* hotLoop(*)*"]
  }
}
```

`all` controls which classes and methods are virtualized. Boolean option groups only control that option for matched methods. For example, if `encryptOperands` is globally `true` and `includes.encryptOperands` is present, operand encryption is enabled only for methods matching that group. If a boolean option is globally `false`, its include group does not turn it on.

Supported boolean group names are:

`protectCodePool`, `virtualizeInstructionAddresses`, `encryptOperands`, `perMethodOpcodeMap`, `shuffleConstants`, `bindConstantsToOperands`, `splitCodeStreams`, `shuffleInstructionBlocks`, `obfuscateDispatch`, `dynamicCodePoolBuild`, `dynamicStateKey`, `virtualControlFlowGraph`, `constantFix`, and `superInstruction`.

Only included classes and then methods will be processed.

Wildcards are supported with `*`. Class names use dot form in config rules, while method descriptors use JVM descriptor syntax.

### Class Rules

| Expression | Effect |
|---|---|
| `*` | Match all classes. |
| `package.*` | Match classes in `package` and its subpackages. |
| `@Virtualized *` | Match classes annotated with `@Virtualized`. |
| `@com.example.Virtualized com.example.*` | Match classes in `com.example` annotated with `@com.example.Virtualized`. |
| `@Lcom/example/Virtualized; *` | Match classes annotated with descriptor form `Lcom/example/Virtualized;`. |

### Field Rules

| Expression | Effect |
|---|---|
| `* *` | Match all fields. |
| `* exclude*` | Match fields whose names start with `exclude`. |
| `com.example.* token` | Match field `token` in `com.example` classes. |
| `* @Sensitive *` | Match fields annotated with `@Sensitive`. |
| `com.example.* @Sensitive secret*` | Match annotated fields whose names start with `secret`. |

### Method Rules

| Expression | Effect |
|---|---|
| `* *(*)*` | Match all methods with any signature. |
| `* main(*)*` | Match methods named `main`. |
| `* main([Ljava/lang/String;)V` | Match `void main(String[])`. |
| `* @Virtualize *(*)*` | Match methods annotated with `@Virtualize`. |
| `com.example.* @Virtualize run(*)*` | Match annotated `run` methods in `com.example` classes. |
| `* <init>(*)V` | Match constructors. |
| `* <clinit>()V` | Match static initializers. |

Annotation matching checks both runtime-visible and runtime-invisible annotations. You may use a simple annotation name, a full class name, or JVM descriptor form:

```jsonc
{
  "includes": [
    "@Virtualized *",
    "* @Virtualize *(*)*",
    "com.example.* @com.example.Protect secret(*)*"
  ],
  "exclusions": [
    "* <init>(*)V",
    "* <clinit>()V",
    "* @DoNotVirtualize *(*)*"
  ]
}
```
