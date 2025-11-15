# Luau (for Java)

[![license](https://img.shields.io/github/license/hollow-cube/luau-java.svg)](LICENSE)

Low level bindings for [Luau](https://luau-lang.org).

## Why Luau?

There is a lot of prior art for interacting with Lua to/from Java:

* [LuaJ](https://github.com/luaj/luaj) (and its many forks)
* [luajava](https://github.com/jasonsantos/luajava) (and its many forks)
* [LuaTruffle](https://github.com/lucasallan/LuaTruffle) for GraalVM

However, these solutions generally do not have strong sandboxing guarantees (or track record), or lose much of the
performance benefit of Lua. Enter Luau. It has been proven at Roblox to be performant, well sandboxed, and easier to
use through its introduction of progressive typing.

## Install

`luau-java` and the associated native libraries are all available
on [maven central](https://search.maven.org/search?q=g:dev.hollowcube%20AND%20a:luau-java). All projects must depend on
the main luau artifact, as well as at least one of the platform specific natives. It is valid to depend on multiple
natives artifacts at the same time, they will not conflict.

> [!IMPORTANT]  
> `luau-java` requires Java 25 or higher.

<details open>
<summary>Gradle</summary>

```groovy
dependencies {
    implementation("dev.hollowcube:luau:${version}")
    implementation("dev.hollowcube:luau-natives-${platform}:${version}")
}
```

</details>

<details>
<summary>Maven</summary>

```xml

<dependencies>
    <dependency>
        <groupId>dev.hollowcube</groupId>
        <artifactId>luau</artifactId>
        <version>${version}</version>
    </dependency>
    <dependency>
        <groupId>dev.hollowcube</groupId>
        <artifactId>luau-natives-${platform}</artifactId>
        <version>${version}</version>
    </dependency>
</dependencies>
```

</details>

Replace `${platform}` and `${version}` with one of the following entries. Note that the core library version may be
different from the native library version.

| Platform      | `${platform}` | `${version}`                                                                                                                                                      |
|---------------|---------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| -             | -             | [![](https://img.shields.io/maven-central/v/dev.hollowcube/luau)](https://mvnrepository.com/artifact/dev.hollowcube/luau)                                         |
| Windows (x64) | `windows-x64` | [![](https://img.shields.io/maven-central/v/dev.hollowcube/luau-natives-windows-x64)](https://mvnrepository.com/artifact/dev.hollowcube/luau-natives-windows-x64) |
| Linux (x64)   | `linux-x64`   | [![](https://img.shields.io/maven-central/v/dev.hollowcube/luau-natives-linux-x64)](https://mvnrepository.com/artifact/dev.hollowcube/luau-natives-linux-x64)     |
| macOS (x64)   | `macos-x64`   | [![](https://img.shields.io/maven-central/v/dev.hollowcube/luau-natives-macos-x64)](https://mvnrepository.com/artifact/dev.hollowcube/luau-natives-macos-x64)     |
| macOS (arm64) | `macos-arm64` | [![](https://img.shields.io/maven-central/v/dev.hollowcube/luau-natives-macos-arm64)](https://mvnrepository.com/artifact/dev.hollowcube/luau-natives-macos-arm64) |

> [!IMPORTANT]  
> We publish two sets of native dependencies for each platform. Without a suffix on the version is release binaries,
> with `-debug` corresponds to debug builds.

## Usage

A hello world print from Luau would look something like the following:

```java
public class HelloWorld {
    static void main(String[] args) throws LuauCompileException {
        final byte[] bytecode = LuauCompiler.DEFAULT.compile("print(\"Hello, Luau!\")");

        final LuaState state = LuaState.newState();
        try {
            state.openLibs(); // Open all libraries
            state.sandbox(); // Sandbox the global state so it cannot be edited by a script

            var thread = state.newThread();
            thread.sandboxThread(); // Create a mutable global env for scripts to use

            thread.load("helloworld.luau", bytecode); // Load the script into the VM
            thread.pcall(0, 0); // Eval the script

            state.pop(1); // Pop the thread off the stack
        } finally {
            // Always remember to close the state when you're done with it, or you will leak memory.
            state.close();
        }
    }
}
```

The test sources contain library examples, which should help you to get started.

## Error Handling

TODO: add some notes about error handling

## Building from Source

Prerequisites: JDK 25+, CMake 3.15+, [JExtract](https://jdk.java.net/jextract/) (Only required to update bindings)

```shell
git clone git@github.com:hollow-cube/luau-java.git --recurse-submodules && cd luau-java
./gradlew build
```

### Updating Bindings

Bindings are generated using [JExtract](https://jdk.java.net/jextract/). They are already included in the repository
inside of `src/generated/java`. They may need to be updated as Luau is updated.

```shell
./gradlew jextract
```

## Contributing

Contributions via PRs and issues are always welcome.

## License

This project is licensed under the [MIT License](LICENSE).
