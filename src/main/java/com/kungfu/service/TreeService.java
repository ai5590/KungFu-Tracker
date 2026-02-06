package com.kungfu.service;

import com.kungfu.model.TreeNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class TreeService {

    private final Path dataRoot;

    public TreeService(@Value("${app.data-dir}") String dataDir) {
        this.dataRoot = Path.of(dataDir).toAbsolutePath().normalize();
    }

    public Path getDataRoot() {
        return dataRoot;
    }

    public List<TreeNode> buildTree() throws IOException {
        if (!Files.exists(dataRoot)) {
            return List.of();
        }
        return buildChildren(dataRoot);
    }

    private List<TreeNode> buildChildren(Path dir) throws IOException {
        List<TreeNode> nodes = new ArrayList<>();
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    entries.add(entry);
                }
            }
        }
        entries.sort(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()));

        for (Path entry : entries) {
            String name = entry.getFileName().toString();
            if (name.startsWith(".") || name.equals("media")) continue;

            String relativePath = dataRoot.relativize(entry).toString().replace('\\', '/');

            if (Files.exists(entry.resolve("exercise.json"))) {
                nodes.add(new TreeNode(name, relativePath, "EXERCISE", null));
            } else if (Files.exists(entry.resolve("_section.json"))) {
                List<TreeNode> children = buildChildren(entry);
                nodes.add(new TreeNode(name, relativePath, "SECTION", children));
            }
        }
        return nodes;
    }
}
