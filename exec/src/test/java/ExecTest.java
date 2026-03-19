/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/JUnit5TestClass.java to edit this template
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    public ExecTest() throws IOException {
        workingDir = Path.of("").toAbsolutePath();
        cacheDir = workingDir.resolve("cache");
        config = Exec.loadConfig(Path.of(""));
    }

    @Test
    public void testConfig() throws IOException {
        Properties config = Exec.loadConfig(Path.of(""));
        assertThat(config).containsKeys("jdk.linux.x64.sha", "jdk.linux.x64.url");

    }

    @ParameterizedTest
    @CsvSource({
        "linux,X64,deb",
        "linux,X64,rpm",
        "linux,arm,deb",
        "macos,X64,pkg",
        "macos,arm,pkg",
        "windows,X64,exe"
    }
    )
    public void testResource(String os, String arch, String type) throws IOException {
        String cmd = "show";
        Exec exec = new Exec(workingDir, cacheDir, config, os, arch, type);
        ThrowableAssert.ThrowingCallable code = () -> exec.ensureExistence(type, arch, os);
        assertThatCode(code).as("not found " + os + "-" + arch + "-" + type).doesNotThrowAnyException();
    }

    Path cacheDir;
    Path workingDir;
    Properties config;

    @Test
    public void testValidate() throws IOException {
        Exec exec = new Exec(workingDir, cacheDir, config, "linux", "X64", "rpm");
        ThrowableAssert.ThrowingCallable code = () -> exec.validate(true);
        assertThatCode(code).as("validate").doesNotThrowAnyException();
    }


    @ParameterizedTest(name="{0}.{1}.{2}")
    @CsvSource({
        "linux,x64,deb",
        "linux,x64,rpm",
        "linux,arm,deb",
        "macos,x64,pkg",
        "macos,arm,pkg",
        "windows,x64,exe"
    }
    )
    public void testResourceKey(String os, String arch, String type) throws Exception {

        Exec exec = new Exec(workingDir, cacheDir, config, os, arch,type);
        String id = "jdk." + os + "." + arch;
        System.out.println("id = " + id);
        var orDefault = (String)exec.config.getOrDefault(id+".url", "");
        assertThat(orDefault).as("breaks for "+ id+".url").isNotBlank();
    }
}
