package net.minecraftforge.gradle.common.runtime.extensions;

import com.google.common.collect.Maps;
import net.minecraftforge.gradle.common.runtime.CommonRuntimeDefinition;
import net.minecraftforge.gradle.common.runtime.spec.CommonRuntimeSpec;
import net.minecraftforge.gradle.dsl.common.runtime.spec.builder.CommonRuntimeSpecBuilder;
import net.minecraftforge.gradle.dsl.common.extensions.MinecraftArtifactCache;
import net.minecraftforge.gradle.dsl.common.runtime.tasks.Runtime;
import net.minecraftforge.gradle.dsl.common.tasks.WithOutput;
import net.minecraftforge.gradle.dsl.common.util.DistributionType;
import net.minecraftforge.gradle.dsl.common.util.CacheableMinecraftVersion;
import net.minecraftforge.gradle.dsl.common.util.GameArtifact;
import org.apache.commons.lang3.StringUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public abstract class CommonRuntimeExtension<S extends CommonRuntimeSpec, B extends CommonRuntimeSpecBuilder<S, B>, D extends CommonRuntimeDefinition<S>> implements net.minecraftforge.gradle.dsl.common.runtime.extensions.CommonRuntimes<S, B, D> {
    protected final Map<String, D> runtimes = Maps.newHashMap();
    private final Project project;

    protected CommonRuntimeExtension(Project project) {
        this.project = project;

        this.getDistributionType().convention(DistributionType.JOINED);
    }

    protected static void configureGameArtifactProvidingTaskWithDefaults(CommonRuntimeSpec spec, File runtimeWorkingDirectory, Map<String, File> data, Runtime mcpRuntimeTask, GameArtifact gameArtifact) {
        mcpRuntimeTask.getArguments().set(Maps.newHashMap());
        configureCommonMcpRuntimeTaskParameters(mcpRuntimeTask, data, String.format("provide%s", StringUtils.capitalize(gameArtifact.name())), spec, runtimeWorkingDirectory);
    }

    protected static void configureCommonMcpRuntimeTaskParameters(Runtime mcpRuntimeTask, Map<String, File> data, String step, CommonRuntimeSpec spec, File runtimeDirectory) {
        mcpRuntimeTask.getData().set(data);
        mcpRuntimeTask.getStepName().set(step);
        mcpRuntimeTask.getDistribution().set(spec.getSide());
        mcpRuntimeTask.getMinecraftVersion().set(CacheableMinecraftVersion.from(spec.getMinecraftVersion()));
        mcpRuntimeTask.getRuntimeDirectory().set(runtimeDirectory);
        mcpRuntimeTask.getJavaVersion().convention(spec.getConfigurationProject().getExtensions().getByType(JavaPluginExtension.class).getToolchain().getLanguageVersion());
    }

    protected static Map<GameArtifact, TaskProvider<? extends WithOutput>> buildDefaultArtifactProviderTasks(final CommonRuntimeSpec spec, final File runtimeWorkingDirectory) {
        final MinecraftArtifactCache artifactCache = spec.getConfigurationProject().getExtensions().getByType(MinecraftArtifactCache.class);
        return artifactCache.cacheGameVersionTasks(spec.getProject(), new File(runtimeWorkingDirectory, "cache"), spec.getMinecraftVersion(), spec.getSide());
    }

    @Override
    public Project getProject() {
        return project;
    }

    @Override
    public final Provider<Map<String, D>> getRuntimes() {
        return getProject().provider(() -> this.runtimes);
    }

    @Override
    @NotNull
    public final D maybeCreate(final Action<B> configurator) {
        final S spec = createSpec(configurator);
        return maybeCreate(spec);
    }

    @Override
    @NotNull
    public final D maybeCreate(final Consumer<B> configurator) {
        return maybeCreate((Action<B>) configurator::accept);
    }

    @Override
    @NotNull
    public final D maybeCreate(final S spec) {
        if (runtimes.containsKey(spec.getName()))
            return runtimes.get(spec.getName());

        return create(spec);
    }

    @Override
    @NotNull
    public final D create(final Action<B> configurator) {
        final S spec = createSpec(configurator);
        return create(spec);
    }

    @Override
    @NotNull
    public final D create(final Consumer<B> configurator) {
        return maybeCreate((Action<B>) configurator::accept);
    }

    @Override
    @NotNull
    public final D create(final S spec) {
        if (runtimes.containsKey(spec.getName()))
            throw new IllegalArgumentException(String.format("Runtime with name '%s' already exists", spec.getName()));

        final D runtime = doCreate(spec);
        runtimes.put(spec.getName(), runtime);
        return runtime;
    }

    @NotNull
    protected abstract D doCreate(final S spec);

    @NotNull
    private S createSpec(Action<B> configurator) {
        final B builder = createBuilder();
        configurator.execute(builder);
        return builder.build();
    }

    @Override
    @NotNull
    public final D getByName(final String name) {
        return this.runtimes.computeIfAbsent(name, (n) -> {
            throw new RuntimeException(String.format("Failed to find runtime with name: %s", n));
        });
    }

    @Override
    @Nullable
    public final D findByName(final String name) {
        return this.runtimes.get(name);
    }

    protected abstract B createBuilder();

    protected abstract void bakeDefinition(D definition);

    public final void bakeDefinitions() {
        this.runtimes.values().forEach(this::bakeDefinition);
    }

    @Override
    @NotNull
    public Set<D> findIn(final Configuration configuration) {
        final Set<D> directDependency = configuration.getAllDependencies().
                stream().flatMap(dep -> getRuntimes().get().values().stream().filter(runtime -> runtime.replacedDependency().equals(dep)))
                .collect(Collectors.toSet());

        if (directDependency.isEmpty())
            return directDependency;

        return project.getConfigurations().stream()
                .filter(config -> config.getHierarchy().contains(configuration))
                .flatMap(config -> config.getAllDependencies().stream())
                .flatMap(dep -> getRuntimes().get().values().stream().filter(runtime -> runtime.replacedDependency().equals(dep)))
                .collect(Collectors.toSet());
    }
}
