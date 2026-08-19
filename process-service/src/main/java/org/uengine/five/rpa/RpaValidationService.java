package org.uengine.five.rpa;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Robot Framework 스크립트를 실제 업무 수행 없이 안전하게 dry-run 검증한다. */
@Service
public class RpaValidationService {

    static final int MAX_SCRIPT_CHARS = 200_000;
    static final int MAX_OUTPUT_BYTES = 20_000;
    static final long TIMEOUT_SECONDS = 10;

    private static final Pattern ERROR_LINE = Pattern.compile("(?i).*on line (\\d+):\\s*(.*)");
    private static final Pattern VARIABLE_NAME = Pattern.compile("[\\p{L}\\p{N}_.-]{1,100}");

    @Value("${uengine.rpa.python-command:${UENGINE_RPA_PYTHON:}}")
    String pythonCommand;

    @Value("${uengine.rpa.library-path:${UENGINE_RPA_LIBRARY_PATH:}}")
    String configuredLibraryPath;

    public ValidationResult validate(String script, List<String> variables) {
        List<ValidationError> policyErrors = validatePolicy(script);
        if (!policyErrors.isEmpty()) {
            return new ValidationResult(true, false, policyErrors, "");
        }

        Path workDirectory = null;
        try {
            workDirectory = Files.createTempDirectory("uengine-rpa-dryrun-");
            Path robotFile = workDirectory.resolve("validation.robot");
            Files.writeString(robotFile, script, StandardCharsets.UTF_8);

            List<String> command = new ArrayList<>();
            command.add(resolvePythonCommand());
            command.add("-m");
            command.add("robot");
            command.add("--dryrun");
            command.add("--output");
            command.add("NONE");
            command.add("--log");
            command.add("NONE");
            command.add("--report");
            command.add("NONE");
            command.add("--consolecolors");
            command.add("off");

            Path libraryPath = resolveLibraryPath();
            if (libraryPath != null) {
                command.add("--pythonpath");
                command.add(libraryPath.toString());
            }
            if (variables != null) {
                variables.stream()
                        .filter(name -> name != null && VARIABLE_NAME.matcher(name).matches())
                        .distinct()
                        .forEach(name -> {
                            command.add("--variable");
                            command.add(name + ":");
                        });
            }
            command.add(robotFile.toString());

            Process process = new ProcessBuilder(command)
                    .directory(workDirectory.toFile())
                    .redirectErrorStream(true)
                    .start();
            CompletableFuture<String> outputReader = CompletableFuture.supplyAsync(() -> readLimited(process.getInputStream()));

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                String output = outputReader.get(2, TimeUnit.SECONDS);
                return new ValidationResult(true, false,
                        List.of(new ValidationError(null, "dry-run 검증 시간이 10초를 초과했습니다.")), output);
            }

            String output = outputReader.get(2, TimeUnit.SECONDS);
            if (isRobotUnavailable(output)) {
                return new ValidationResult(false, false,
                        List.of(new ValidationError(null, "Robot Framework 검증 환경을 찾을 수 없습니다.")), output);
            }
            if (process.exitValue() == 0) {
                return new ValidationResult(true, true, List.of(), output);
            }
            return new ValidationResult(true, false, parseErrors(output), output);
        } catch (IOException e) {
            return new ValidationResult(false, false,
                    List.of(new ValidationError(null, "Robot Framework 실행기를 시작할 수 없습니다: " + e.getMessage())), "");
        } catch (Exception e) {
            return new ValidationResult(true, false,
                    List.of(new ValidationError(null, "dry-run 검증 중 오류가 발생했습니다: " + e.getMessage())), "");
        } finally {
            deleteTemporaryDirectory(workDirectory);
        }
    }

    List<ValidationError> validatePolicy(String script) {
        List<ValidationError> errors = new ArrayList<>();
        if (script == null || script.isBlank()) {
            errors.add(new ValidationError(null, "검증할 스크립트가 없습니다."));
            return errors;
        }
        if (script.length() > MAX_SCRIPT_CHARS) {
            errors.add(new ValidationError(null, "스크립트는 200KB 이하만 검증할 수 있습니다."));
            return errors;
        }

        boolean settingsSection = false;
        String[] lines = script.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String trimmed = lines[index].trim();
            if (trimmed.matches("\\*\\*\\*.*\\*\\*\\*")) {
                settingsSection = "*** Settings ***".equalsIgnoreCase(trimmed);
                continue;
            }
            if (!settingsSection || trimmed.isEmpty() || trimmed.startsWith("#"))
                continue;

            String[] cells = trimmed.split("(?:\\s{2,}|\\t+)");
            if (cells.length == 0)
                continue;
            String setting = cells[0].toLowerCase(Locale.ROOT);
            if ("resource".equals(setting) || "variables".equals(setting)) {
                errors.add(new ValidationError(index + 1, "Resource와 Variables import는 검증 서버에서 허용되지 않습니다."));
            } else if ("library".equals(setting)) {
                String library = cells.length > 1 ? cells[1].trim() : "";
                if (!"UEngineLibrary".equals(library)) {
                    errors.add(new ValidationError(index + 1, "허용되지 않은 Robot Library입니다: " + library));
                }
            }
        }
        return errors;
    }

    private Path resolveLibraryPath() {
        List<Path> candidates = new ArrayList<>();
        if (configuredLibraryPath != null && !configuredLibraryPath.isBlank())
            candidates.add(Path.of(configuredLibraryPath));
        candidates.add(Path.of("../rpa-agent/uengine_rpa"));
        candidates.add(Path.of("rpa-agent/uengine_rpa"));
        return candidates.stream().map(path -> path.toAbsolutePath().normalize()).filter(Files::isDirectory).findFirst().orElse(null);
    }

    private String resolvePythonCommand() {
        if (pythonCommand != null && !pythonCommand.isBlank())
            return pythonCommand;
        List<Path> candidates = List.of(
                Path.of("../rpa-agent/.venv-build/bin/python"),
                Path.of("rpa-agent/.venv-build/bin/python"),
                Path.of("../rpa-agent/.venv/bin/python"),
                Path.of("rpa-agent/.venv/bin/python"),
                Path.of("../rpa-agent/.venv-build/Scripts/python.exe"),
                Path.of("rpa-agent/.venv-build/Scripts/python.exe"));
        return candidates.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .filter(Files::isRegularFile)
                .map(Path::toString)
                .findFirst()
                .orElse("python3");
    }

    private List<ValidationError> parseErrors(String output) {
        List<ValidationError> errors = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String lower = line.toLowerCase(Locale.ROOT);
            if (!(lower.contains("error") || lower.contains("no keyword with name")
                    || lower.contains("expected ") || lower.contains("failed")))
                continue;
            Matcher matcher = ERROR_LINE.matcher(line);
            Integer lineNumber = matcher.matches() ? Integer.valueOf(matcher.group(1)) : null;
            String message = matcher.matches() ? matcher.group(2).trim() : line.replaceAll("^[\\s\\[\\]*-]+", "").trim();
            if (!message.isEmpty() && errors.stream().noneMatch(error -> error.message().equals(message)))
                errors.add(new ValidationError(lineNumber, message));
            if (errors.size() >= 20)
                break;
        }
        if (errors.isEmpty())
            errors.add(new ValidationError(null, "Robot Framework dry-run 검증에 실패했습니다. 상세 출력을 확인하세요."));
        return errors;
    }

    private boolean isRobotUnavailable(String output) {
        String lower = output.toLowerCase(Locale.ROOT);
        return lower.contains("no module named robot") || lower.contains("cannot find module robot");
    }

    private String readLimited(InputStream input) {
        try (input; ByteArrayOutputStream kept = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                int remaining = MAX_OUTPUT_BYTES - kept.size();
                if (remaining > 0)
                    kept.write(buffer, 0, Math.min(read, remaining));
            }
            return kept.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "검증 출력 읽기 실패: " + e.getMessage();
        }
    }

    private void deleteTemporaryDirectory(Path directory) {
        if (directory == null || !Files.exists(directory))
            return;
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // 운영체제가 잠시 파일을 점유하면 임시 디렉터리 정리만 건너뛴다.
                }
            });
        } catch (IOException ignored) {
            // 검증 결과 반환을 임시 파일 정리 실패로 바꾸지 않는다.
        }
    }

    public record ValidationError(Integer line, String message) {
    }

    public record ValidationResult(boolean available, boolean valid, List<ValidationError> errors, String output) {
    }
}
