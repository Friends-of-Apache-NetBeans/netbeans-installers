/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/JUnit5TestClass.java to edit this template
 */

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 *
 * @author homberghp {@code <pieter.van.den.hombergh@gmail.com>}
 */
public class ExecTest {

    @Test
    public void testConfig() throws IOException {
        Properties config = Exec.loadConfig(Path.of(""));
        assertThat(config).containsKeys("jdk.linux.x64.sha", "jdk.linux.x64.url");

    }

    @ParameterizedTest
    @CsvSource({
        "linux,x86,deb",
        "linux,x86,rpm",
        "linux,arm,deb",
        "macos,x86,pkg",
        "macos,arm,pkg",
        "windows,x86,exe"
    }
    )
    public void testResource(String os, String arch, String type) throws IOException {
        Properties config = Exec.loadConfig(Path.of(""));
        Path workingDir = Path.of("").toAbsolutePath();
        Path cacheDir = workingDir.resolve("cache");
        String cmd = "show";
        Exec exec = new Exec(workingDir, cacheDir, config, os, arch, type);
        ThrowableAssert.ThrowingCallable code = () -> exec.ensureExistence(type, arch, os);
        assertThatCode(code).as("not found "+os+"-"+arch+"-"+type).doesNotThrowAnyException();
    }

    @Test
    public void testValidate() throws IOException {
        Properties config = Exec.loadConfig(Path.of(""));
        Path workingDir = Path.of("").toAbsolutePath();
        Path cacheDir = workingDir.resolve("cache");
        Exec exec = new Exec(workingDir, cacheDir, config, "linux", "x86", "rpm");
        ThrowableAssert.ThrowingCallable code = () -> exec.validate(true);
        assertThatCode(code).as("validate").doesNotThrowAnyException();
    }

    @Test
    public void testBuildCommandLine() {
        

    }
}
