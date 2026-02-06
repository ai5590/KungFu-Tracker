package com.kungfu.controller;

import com.kungfu.model.TreeNode;
import com.kungfu.service.TreeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
public class TreeController {

    private final TreeService treeService;

    public TreeController(TreeService treeService) {
        this.treeService = treeService;
    }

    @GetMapping("/tree")
    public List<TreeNode> getTree() throws IOException {
        return treeService.buildTree();
    }
}
