package com.kungfu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TreeNode {
    private String name;
    private String path;
    private String nodeType;
    private List<TreeNode> children;

    public TreeNode() {}

    public TreeNode(String name, String path, String nodeType, List<TreeNode> children) {
        this.name = name;
        this.path = path;
        this.nodeType = nodeType;
        this.children = children;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public List<TreeNode> getChildren() { return children; }
    public void setChildren(List<TreeNode> children) { this.children = children; }
}
