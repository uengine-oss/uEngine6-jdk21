package org.uengine.five.deploy;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * {@code {basePath}/deploy-request|deploy-apply|archive/{definitionPath}/{version}.bpmn} 트리용 버전 유틸.
 * process-service {@code findHighestNumberedFileName} 과 동일한 숫자 버전 비교를 사용한다.
 */
public final class VersionedBpmnFiles {

    /** 배포 요청(송신). 외부 쉘이 타 서버 {@link #ROOT_DEPLOY_APPLY} 로 복사. */
    public static final String ROOT_DEPLOY_REQUEST = "deploy-request";
    /** 배포 적용(수신). 이 서버에서 sync 로 definitions 에 반영. */
    public static final String ROOT_DEPLOY_APPLY = "deploy-apply";
    public static final String ROOT_ARCHIVE = "archive";
    public static final String ROOT_DEFINITIONS = "definitions";

    private VersionedBpmnFiles() {
    }

    public static Path rootDir(String basePath, String root) {
        return Path.of(basePath).toAbsolutePath().normalize().resolve(root);
    }

    public static Path definitionDir(String basePath, String root, String definitionPath) {
        return rootDir(basePath, root).resolve(normalizeDefinitionPath(definitionPath));
    }

    public static Path definitionsFile(String basePath, String definitionPath) {
        return rootDir(basePath, ROOT_DEFINITIONS).resolve(normalizeDefinitionPath(definitionPath));
    }

    public static String normalizeDefinitionPath(String definitionPath) {
        if (definitionPath == null) {
            return "";
        }
        String normalized = definitionPath.trim().replace("\\", "/");
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith(ROOT_DEPLOY_REQUEST + "/")) {
            normalized = normalized.substring(ROOT_DEPLOY_REQUEST.length() + 1);
        } else if (normalized.startsWith(ROOT_DEPLOY_APPLY + "/")) {
            normalized = normalized.substring(ROOT_DEPLOY_APPLY.length() + 1);
        } else if (normalized.startsWith("export/")) {
            normalized = normalized.substring("export/".length());
        } else if (normalized.startsWith("import/")) {
            normalized = normalized.substring("import/".length());
        } else if (normalized.startsWith(ROOT_ARCHIVE + "/")) {
            normalized = normalized.substring(ROOT_ARCHIVE.length() + 1);
        } else if (normalized.startsWith(ROOT_DEFINITIONS + "/")) {
            normalized = normalized.substring(ROOT_DEFINITIONS.length() + 1);
        }
        String fileName = normalized.contains("/")
                ? normalized.substring(normalized.lastIndexOf('/') + 1)
                : normalized;
        if (!fileName.isEmpty() && !fileName.contains(".")) {
            normalized = normalized + ".bpmn";
        }
        return normalized;
    }

    /**
     * deploy-apply 아래 버전 파일({@code 0.x.bpmn})의 부모 디렉터리를 definitionPath 로 수집한다.
     */
    public static List<String> listDefinitionPaths(String basePath) throws IOException {
        return listDefinitionPaths(basePath, ROOT_DEPLOY_APPLY);
    }

    /**
     * 지정 root({@code deploy-request} / {@code deploy-apply} 등) 아래 버전 파일의 부모를 definitionPath 로 수집.
     */
    public static List<String> listDefinitionPaths(String basePath, String root) throws IOException {
        Path rootPath = rootDir(basePath, root);
        Set<String> paths = new LinkedHashSet<>();
        if (!Files.isDirectory(rootPath)) {
            return List.of();
        }
        try (Stream<Path> walk = Files.walk(rootPath)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> isVersionBpmnFileName(p.getFileName().toString()))
                    .forEach(p -> {
                        Path parent = p.getParent();
                        if (parent == null) {
                            return;
                        }
                        Path relative = rootPath.relativize(parent);
                        String definitionPath = relative.toString().replace("\\", "/");
                        if (!definitionPath.isBlank()) {
                            paths.add(definitionPath);
                        }
                    });
        }
        List<String> sorted = new ArrayList<>(paths);
        sorted.sort(String::compareTo);
        return sorted;
    }

    public static Optional<String> findHighestVersion(String basePath, String root, String definitionPath)
            throws IOException {
        Path dir = definitionDir(basePath, root, definitionPath);
        if (!Files.isDirectory(dir)) {
            return Optional.empty();
        }
        List<String> versions = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(VersionedBpmnFiles::isVersionBpmnFileName)
                    .map(name -> name.replaceFirst("\\.bpmn$", ""))
                    .forEach(versions::add);
        }
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        versions.sort(Comparator.comparing((String v) -> v, VersionedBpmnFiles::compareDotSeparatedNumericVersions)
                .reversed());
        return Optional.of(versions.get(0));
    }

    public static String nextArchiveVersion(String basePath, String definitionPath) throws IOException {
        return nextVersion(basePath, ROOT_ARCHIVE, definitionPath);
    }

    public static String nextVersion(String basePath, String root, String definitionPath) throws IOException {
        return findHighestVersion(basePath, root, definitionPath)
                .map(VersionedBpmnFiles::incrementVersion)
                .orElse("0.1");
    }

    public static String readVersionFile(String basePath, String root, String definitionPath, String version)
            throws IOException {
        Path file = definitionDir(basePath, root, definitionPath).resolve(version + ".bpmn");
        if (!Files.isRegularFile(file)) {
            throw new IOException("Version file not found: " + file);
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public static String readDefinitionsFile(String basePath, String definitionPath) throws IOException {
        Path file = definitionsFile(basePath, definitionPath);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Definition file not found: " + file);
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public static Path writeVersionFile(String basePath, String root, String definitionPath, String version,
            String content) throws IOException {
        Path dir = definitionDir(basePath, root, definitionPath);
        Files.createDirectories(dir);
        Path file = dir.resolve(version + ".bpmn");
        Files.writeString(file, content != null ? content : "", StandardCharsets.UTF_8);
        return file;
    }

    public static Path writeDefinitionsFile(String basePath, String definitionPath, String content)
            throws IOException {
        Path file = definitionsFile(basePath, definitionPath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content != null ? content : "", StandardCharsets.UTF_8);
        return file;
    }

    public static void deleteDefinitionsFile(String basePath, String definitionPath) throws IOException {
        Path file = definitionsFile(basePath, definitionPath);
        Files.deleteIfExists(file);
    }

    public static void deleteVersionFile(String basePath, String root, String definitionPath, String version)
            throws IOException {
        Path file = definitionDir(basePath, root, definitionPath).resolve(version + ".bpmn");
        Files.deleteIfExists(file);
    }

    /**
     * deploy-apply/{definitionPath}/ 디렉터리 전체 삭제 (버전 파일 포함).
     */
    public static void deleteDefinitionTree(String basePath, String root, String definitionPath) throws IOException {
        Path dir = definitionDir(basePath, root, definitionPath);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to delete " + p, e);
                }
            });
        }
    }

    public static boolean definitionsExists(String basePath, String definitionPath) {
        return Files.isRegularFile(definitionsFile(basePath, definitionPath));
    }

    public static boolean isVersionBpmnFileName(String fileName) {
        if (fileName == null || !fileName.endsWith(".bpmn")) {
            return false;
        }
        String version = fileName.substring(0, fileName.length() - ".bpmn".length());
        return isDotSeparatedNumericVersion(version);
    }

    public static boolean isDotSeparatedNumericVersion(String str) {
        return str != null && str.matches("\\d+(?:\\.\\d+)*");
    }

    public static String incrementVersion(String version) {
        if (version == null || version.isBlank()) {
            return "0.1";
        }
        String[] parts = version.split("\\.");
        if (parts.length == 1) {
            return parts[0] + ".1";
        }
        BigInteger last = new BigInteger(parts[parts.length - 1]);
        parts[parts.length - 1] = last.add(BigInteger.ONE).toString();
        return String.join(".", parts);
    }

    /**
     * v1 &gt; v2 이면 양수. process-service compareDotSeparatedNumericVersions 와 동일.
     */
    public static int compareDotSeparatedNumericVersions(String v1, String v2) {
        String[] p1 = v1.split("\\.");
        String[] p2 = v2.split("\\.");
        int max = Math.max(p1.length, p2.length);
        for (int i = 0; i < max; i++) {
            BigInteger n1 = i < p1.length ? new BigInteger(p1[i]) : BigInteger.ZERO;
            BigInteger n2 = i < p2.length ? new BigInteger(p2[i]) : BigInteger.ZERO;
            int cmp = n1.compareTo(n2);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }
}
