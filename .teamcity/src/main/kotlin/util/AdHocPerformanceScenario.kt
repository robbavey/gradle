package util

import common.Arch
import common.JvmVendor
import common.Os
import common.applyPerformanceTestSettings
import common.buildToolGradleParameters
import common.checkCleanM2AndAndroidUserHome
import common.gradleWrapper
import common.individualPerformanceTestArtifactRules
import common.killGradleProcessesStep
import common.performanceTestCommandLine
import common.removeSubstDirOnWindows
import common.substDirOnWindows
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.ParameterDisplay
import jetbrains.buildServer.configs.kotlin.v2019_2.ParametrizedWithType

abstract class AdHocPerformanceScenario(os: Os) : BuildType({
    val id = "Util_Performance_AdHocPerformanceScenario${os.asName()}"
    name = "AdHoc Performance Scenario - ${os.asName()}"
    id(id)

    applyPerformanceTestSettings(os = os, timeout = 420)
    artifactRules = individualPerformanceTestArtifactRules

    params {
        text(
            "baselines",
            "defaults",
            display = ParameterDisplay.PROMPT,
            allowEmpty = false,
            description = "The baselines you want to compare against. Can be a Gradle version number. Use force-defaults to not use the commit distribution as a baseline on a branch other than master or release."
        )
        text(
            "testProject",
            "",
            display = ParameterDisplay.PROMPT,
            allowEmpty = false,
            description = "The test project to use. E.g. largeJavaMultiProject"
        )
        param("channel", "adhoc")
        param("checks", "all")
        text("runs", "10", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text("warmups", "3", display = ParameterDisplay.PROMPT, allowEmpty = false)
        text(
            "scenario",
            "",
            display = ParameterDisplay.PROMPT,
            allowEmpty = false,
            description = "Which performance test to run. Should be the fully qualified class name dot (unrolled) method name. E.g. org.gradle.performance.regression.java.JavaUpToDatePerformanceTest.up-to-date assemble (parallel true)"
        )
        text("testJavaVersion", "8", display = ParameterDisplay.PROMPT, allowEmpty = false, description = "The java version to run the performance tests, e.g. 8/11/17")
        select(
            "testJavaVendor",
            JvmVendor.openjdk.name,
            display = ParameterDisplay.PROMPT,
            description = "The java vendor to run the performance tests",
            options = JvmVendor.values().map { it.displayName to it.name }
        )
        when (os) {
            Os.WINDOWS -> {
                profilerParam("jprofiler")
                param("env.JPROFILER_HOME", "C:\\Program Files\\jprofiler\\jprofiler11.1.4")
            }
            else -> {
                profilerParam("async-profiler")
                param("env.FG_HOME_DIR", "/opt/FlameGraph")
                param("env.PATH", "%env.PATH%:/opt/swift/4.2.3/usr/bin:/opt/swift/4.2.4-RELEASE-ubuntu18.04/usr/bin")
                param("env.HP_HOME_DIR", "/opt/honest-profiler")
            }
        }

        param("env.PERFORMANCE_DB_PASSWORD_TCAGENT", "%performance.db.password.tcagent%")
        param("additional.gradle.parameters", "")
    }

    steps {
        killGradleProcessesStep(os)
        substDirOnWindows(os)
        gradleWrapper {
            name = "GRADLE_RUNNER"
            workingDir = os.perfTestWorkingDir
            gradleParams = (
                performanceTestCommandLine(
                    "clean performance:%testProject%PerformanceAdHocTest --tests \"%scenario%\"",
                    "%baselines%",
                    """--warmups %warmups% --runs %runs% --checks %checks% --channel %channel% --profiler %profiler% %additional.gradle.parameters%""",
                    os,
                    Arch.AMD64,
                    "%testJavaVersion%",
                    "%testJavaVendor%",
                ) + buildToolGradleParameters(isContinue = false)
                ).joinToString(separator = " ")
        }
        removeSubstDirOnWindows(os)
        checkCleanM2AndAndroidUserHome(os)
    }
})

private
fun ParametrizedWithType.profilerParam(defaultProfiler: String) {
    text(
        "profiler",
        defaultProfiler,
        display = ParameterDisplay.PROMPT,
        allowEmpty = false,
        description = "Command line option for the performance test task to enable profiling. For example `async-profiler`, `async-profiler-heap`, `async-profiler-all` or `jfr`. Use `none` for benchmarking only."
    )
}

object AdHocPerformanceScenarioLinux : AdHocPerformanceScenario(Os.LINUX)
object AdHocPerformanceScenarioWindows : AdHocPerformanceScenario(Os.WINDOWS)
object AdHocPerformanceScenarioMacOS : AdHocPerformanceScenario(Os.MACOS)
