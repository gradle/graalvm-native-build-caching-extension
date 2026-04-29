> _This repository is maintained by the Develocity Solutions team, as one of several publicly available repositories:_
> - _[Android Cache Fix Gradle Plugin][android-cache-fix-plugin]_
> - _[Common Custom User Data Gradle Plugin][ccud-gradle-plugin]_
> - _[Common Custom User Data Maven Extension][ccud-maven-extension]_
> - _[Develocity Build Configuration Samples][develocity-build-config-samples]_
> - _[Develocity Build Validation Scripts][develocity-build-validation-scripts]_
> - _[Develocity Open Source Projects][develocity-oss-projects]_
> - _[GraalVM Native Build Caching Extension][graalvm-native-build-caching-extension]  (this repository)_

# Custom Maven Extension to make GraalVM native compilation goal cacheable
This Maven extension allows making the GraalVM native compilation cacheable.
The native GraalVM compilation can be performed through different Maven plugins:
- [Quarkus Maven Plugin](https://quarkus.io/guides/maven-tooling#quarkus-maven-plugin)
- [GraalVM Maven Plugin](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html)

This project performs programmatic configuration of the [Develocity Build Cache](https://docs.gradle.com/develocity/maven-extension/current/#using_the_build_cache) through a Maven extension. See [here](https://docs.gradle.com/develocity/maven-extension/current/#custom_extension) for more details.

See the [Release notes](release/changes.md)

# Impact and benefits
The native compilation can be a long operation (several minutes) depending on the project size, build strategy and the target platform. This is why making it cacheable can bring significant build time improvement.
The native compilation is usually the last step of the build, it means that most changes will impact it and prevent cache hits. However, there are still some scenarios where a cache hit can occur (re-run build, documentation change, static resources change, ABI-compatible dependency change...).

It is also important to understand that a native executable can be a large file (hundreds of MB). Copying it from/to the local cache, or transferring it from/to the remote cache can be an expensive operation that has to be balanced with the duration of the work being avoided. 
Eventually, The cache node size has to be configured accordingly to make sure enough space is available.

# Principles
The extension configures the inputs from the [GraalVM Native Image Bundle](https://www.graalvm.org/latest/reference-manual/native-image/overview/Bundles/) as cache key of the Maven goal performing the native compilation.
Some transformations are applied to the bundle inputs to rely on reproducible data (sorting of entries, removal of absolute path prefixes, removal of timestamps...).

The bundle is generated as a previous step of the native compilation goal, with a dry run execution. This execution usually takes several seconds, but this is still an overhead to consider when enabling the caching feature.
The bundle generation can be made cacheable to optimize the process, see more details in [this section](#fast-mode). 

# Supported plugins and configuration

The extension currently supports the following Maven plugins:
- Quarkus Maven Plugin
- GraalVM Maven Plugin
- Spring boot Maven Plugin

The first step is to [declare the Maven extension](#extension-declaration), then configure the respective plugin as described below.

You can find more advanced configuration samples in the [integration tests](https://github.com/gradle/graalvm-native-build-caching-extension/tree/main/src/it) of this project:
- Quarkus in-container build
- Quarkus with JIB extension

---

## Extension declaration
Reference the extension in `.mvn/extensions.xml` (this extension requires the develocity-maven-extension):

```xml
<extensions>
    <extension>
        <groupId>com.gradle</groupId>
        <artifactId>develocity-maven-extension</artifactId>
        <version>2.3</version>
    </extension>
    <extension>
        <groupId>com.gradle</groupId>
        <artifactId>graalvm-native-build-caching-extension</artifactId>
        <version>0.2</version>
    </extension>
</extensions>
```

---

## Quarkus Maven Plugin
This extension makes the `build` goal of the [Quarkus Maven Plugin](https://quarkus.io/guides/maven-tooling#quarkus-maven-plugin) cacheable when the native packaging is used (`quarkus.native.enabled=true`).

Although unrelated to caching, You may consider configuring additional test inputs as described in the [Quarkus Test goals](#quarkus-test-goals) section.

#### Requirements
Quarkus 3.32 and above which enables Native Image Bundle generation.

#### Configuration
An additional execution of the `build` goal has to be configured

```xml
<plugin>
    <groupId>${quarkus.platform.group-id}</groupId>
    <artifactId>quarkus-maven-plugin</artifactId>
    <version>${quarkus.platform.version}</version>
    <extensions>true</extensions>
    <executions>
        <execution>
            <id>prepare-cache</id>
            <goals>
                <goal>build</goal>
            </goals>
            <phase>prepare-package</phase>
            <configuration>
                <properties>
                    <quarkus.package.jar.enabled>false</quarkus.package.jar.enabled>
                    <quarkus.native.bundle.dry-run>true</quarkus.native.bundle.dry-run>
                    <quarkus.native.bundle.name>bundle.nib</quarkus.native.bundle.name>
                </properties>
            </configuration>
        </execution>
    </executions>
</plugin>
```

#### Goal Outputs
Here are the files added as output:
- `target/<project.build.finalName>-runner`
- `target/<project.build.finalName>.jar`
- `target/<project.build.finalName>-runner.jar`
- `target/quarkus-artifact.properties`
- `target/quarkus-app/`

> [!NOTE]
> Some additional outputs can be configured. See the [configuration section](#extra-outputs) for more details.

> [!NOTE]
> In IOPS-constrained environments (e.g., Podman on Windows), setting quarkus.native.remote-container-build=true may improve build performance.

---

## GraalVM Maven Plugin

This extension makes the `compile-no-fork` goal of the [GraalVM Maven Plugin](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html) cacheable.

#### Configuration
An additional execution of the `compile-no-fork` goal has to be configured

```xml
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
    <version>0.9.28</version>
    <extensions>true</extensions>
    <executions>
        <execution>
            <id>prepare-cache</id>
            <goals>
                <goal>compile-no-fork</goal>
            </goals>
            <configuration>
                <buildArgs>
                    <buildArg>--bundle-create=${project.build.directory}/bundle.nib,dry-run</buildArg>
                </buildArgs>
            </configuration>
            <phase>package</phase>
        </execution>
    </executions>
</plugin>
```

#### Goal Outputs
Here are the files added as output:
- `target/<project.build.finalName>`
- `target/<project.build.finalName>.jar`

> [!NOTE]
> Some additional outputs can be configured. See the [configuration section](#extra-outputs) for more details.

---

## Spring-boot Maven Plugin

The [Spring boot Maven plugin](https://docs.spring.io/spring-boot/maven-plugin/index.html) can be used in combination with the [GraalVM Maven plugin](https://graalvm.github.io/native-build-tools/latest/maven-plugin.html) to generate a native executable.

#### Configuration
An additional execution of the `compile-no-fork` goal has to be configured

```xml
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>prepare-cache</id>
            <goals>
                <goal>compile-no-fork</goal>
            </goals>
            <phase>package</phase>
            <configuration>
                <buildArgs>
                    <buildArg>--bundle-create=${project.build.directory}/bundle.nib,dry-run</buildArg>
                </buildArgs>
            </configuration>
        </execution>
    </executions>
</plugin>
```

#### Goal Outputs
Here are the files added as output:
- `target/<project.build.finalName>`
- `target/<project.build.finalName>.jar`

> [!NOTE]
> Some additional outputs can be configured. See the [configuration section](#extra-outputs) for more details.

---

## Additional configuration of the extension

Configuration can be set with (listed in order of precedence, highest first):
- [Configuration file](#configuration-file)
- [Maven properties](#maven-properties) (including `-D` system properties)
- [Environment variables](#environment-variables)

| Name                                        | Description                                 | Default                                                  |
|---------------------------------------------|---------------------------------------------|----------------------------------------------------------|
| `DEVELOCITY_NATIVE_BUILD_DIR`               | Project build directory                     | `project.getBuild().getDirectory()`                      |
| `DEVELOCITY_NATIVE_BUNDLE_FILE`             | Native Image Bundle file name               | `bundle.nib`                                             |
| `DEVELOCITY_NATIVE_CACHE_ENABLED`           | Feature toggle                              | `true`                                                   |
| `DEVELOCITY_NATIVE_CACHE_FAST_MODE_ENABLED` | Fast mode (caches the bundle creation step) | `false`                                                  |
| `DEVELOCITY_NATIVE_CONFIG_FILE`             | Extension configuration file                | `''`                                                     |
| `DEVELOCITY_NATIVE_GRAALVM_IMAGE_NAME`      | GraalVM native image name                   | `project.getArtifactId()`                                |
| `DEVELOCITY_NATIVE_QUARKUS_BUILD_PROFILE`   | Quarkus build profile                       | `prod`                                                   |
| `DEVELOCITY_NATIVE_QUARKUS_CONFIG_PREFIX`   | Quarkus configuration prefix                | `quarkus`                                                |
| `DEVELOCITY_NATIVE_QUARKUS_FINAL_NAME`      | Quarkus native image name                   | `project.getBuild().getFinalName()`                      |
| `DEVELOCITY_NATIVE_EXTRA_OUTPUT_DIRS`       | Goal additional output dirs                 | `''`                                                     |
| `DEVELOCITY_NATIVE_EXTRA_OUTPUT_FILES`      | Goal additional output files                | `''`                                                     |

## Fast mode

By default, the `prepare-cache` execution (which creates the Native Image Bundle) runs on every build. This can take significant time, especially with container-based builds.

When fast mode is enabled (`DEVELOCITY_NATIVE_CACHE_FAST_MODE_ENABLED`), the `prepare-cache` execution is also made cacheable. On subsequent builds with unchanged inputs, the bundle is restored from cache instead of being regenerated, skipping the bundle creation entirely.

Fast mode can be enabled via a Maven property:

```shell
mvn clean package -Pnative -Ddevelocity.native.cache.fast.mode.enabled=true
```

> [!NOTE]
> Fast mode uses the compile classpath, mojo properties and some host-specific information as cache key inputs for the bundle creation step. This is a less precise cache key than the one used for the native compilation goal (which uses the bundle contents). As a result, there is a small risk of false cache hits. This is why fast mode is disabled by default and must be explicitly opted into.

---

## Troubleshooting
Debug logging on the extension can be configured with the following property

```shell
mvn clean package -Dorg.slf4j.simpleLogger.log.com.gradle=debug
```

## Extra outputs

Some additional outputs can be configured if necessary (when using the [quarkus-helm](https://quarkus.io/blog/quarkus-helm/#getting-started-with-the-quarkus-helm-extension) extension for instance). The paths are relative to the `target` folder.

```xml
<properties>
    <!-- Optional declaration of extra output dir -->
    <develocity.native.extra.output.dirs>helm</develocity.native.extra.output.dirs>
    <!-- Optional declaration of extra output files -->
    <develocity.native.extra.output.files>helm/kubernetes/${project.artifactId}/Chart.yaml,helm/kubernetes/${project.artifactId}/values.yaml</develocity.native.extra.output.files>
</properties>
```

---

## Quarkus Test goals

When the test goals (`maven-surefire-plugin` and `maven-failsafe-plugin`) are running some `@QuarkusTest` or `@QuarkusIntegrationTest`,
it is important for consistency to add Quarkus' implicit dependencies as [additional input](https://docs.gradle.com/develocity/maven-extension/current/#declaring_additional_inputs).

In details, Quarkus dynamically adds some dependencies to the build which will be listed in the `target/quarkus-prod-dependencies.txt` file.
This file is created by the Quarkus `track-config-changes` goal and contains the absolute path to each dependency (one dependency per line).
This fileset is added as goal input with a `RUNTIME_CLASSPATH` normalization strategy.
Specifically for `maven-failsafe-plugin`, the Quarkus artifact descriptor `quarkus-artifact.properties` also needs to be added.

This can be achieved by declaring a property `addQuarkusInputs` on the test goal:

```xml
<properties>
    <quarkus.config-tracking.enabled>true</quarkus.config-tracking.enabled>
</properties>

<plugins>
    <plugin>
        <groupId>${quarkus.platform.group-id}</groupId>
        <artifactId>quarkus-maven-plugin</artifactId>
        <version>${quarkus.platform.version}</version>
        <extensions>true</extensions>
        <executions>
            <execution>
                <id>track-prod-config-changes</id>
                <phase>process-resources</phase>
                <goals>
                    <goal>track-config-changes</goal>
                </goals>
            </execution>
        </executions>
    </plugin>
    <plugin>
        <artifactId>maven-surefire-plugin</artifactId>
        <configuration>
            <properties>
                <addQuarkusInputs>true</addQuarkusInputs>>
            </properties>
        </configuration>
    </plugin>
    <plugin>
        <artifactId>maven-failsafe-plugin</artifactId>
        <configuration>
            <properties>
                <addQuarkusInputs>true</addQuarkusInputs>>
            </properties>
        </configuration>
    </plugin>
</plugins>
```

[android-cache-fix-plugin]: https://github.com/gradle/android-cache-fix-gradle-plugin
[ccud-gradle-plugin]: https://github.com/gradle/common-custom-user-data-gradle-plugin
[ccud-maven-extension]: https://github.com/gradle/common-custom-user-data-maven-extension
[ccud-sbt-plugin]: https://github.com/gradle/common-custom-user-data-sbt-plugin
[develocity-build-config-samples]: https://github.com/gradle/develocity-build-config-samples
[develocity-build-validation-scripts]: https://github.com/gradle/develocity-build-validation-scripts
[develocity-oss-projects]: https://github.com/gradle/develocity-oss-projects
[graalvm-native-build-caching-extension]: https://github.com/gradle/graalvm-native-build-caching-extension
