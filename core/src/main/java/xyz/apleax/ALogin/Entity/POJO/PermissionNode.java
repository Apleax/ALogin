package xyz.apleax.ALogin.Entity.POJO;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 权限节点
 *
 * @author Apleax
 */
@Getter(value = AccessLevel.PRIVATE)
@Setter(value = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PermissionNode {

    public static final String DEFAULT_NODE = "default";

    private String value;
    private Map<String, PermissionNode> children;

    private PermissionNode(String value) {
        this.value = value;
        this.children = new HashMap<>();
    }

    public static PermissionNode createRootNode(String rootNodeValue) {
        return new PermissionNode(rootNodeValue);
    }

    public boolean addChild(String child) {
        return children.putIfAbsent(child, new PermissionNode(child)) == null;
    }

    public PermissionNode getChild(String child) {
        return children.get(child);
    }

    public boolean removeChild(String child) {
        return children.remove(child) != null;
    }

    public PermissionNode getNodeByPath(String path) {
        if (path == null || path.isEmpty()) return this;

        if (path.startsWith(this.value + ".")) path = path.substring(this.value.length() + 1);

        if (path.equals(this.value)) return this;

        String[] parts = path.split("\\.");
        PermissionNode current = this;

        for (String part : parts) {
            current = current.getChild(part);
            if (current == null) return null;
        }
        return current;
    }

    public boolean addNodeByPath(String path) {
        if (path == null || path.isEmpty()) return false;
        if (path.startsWith(this.value + ".")) path = path.substring(this.value.length() + 1);
        if (path.equals(this.value)) return false;
        String[] parts = path.split("\\.");
        PermissionNode current = this;
        for (int i = 0; i < parts.length - 1; i++)
            current = current.children.computeIfAbsent(parts[i], PermissionNode::new);
        return current.addChild(parts[parts.length - 1]);
    }

    public ArrayList<String> getAllPermissions() {
        ArrayList<String> permissions = new ArrayList<>();
        collectLeafPermissions(this, this.value, permissions);
        return permissions;
    }

    private void collectLeafPermissions(PermissionNode node, String currentPath, ArrayList<String> permissions) {
        if (node.children.isEmpty()) permissions.add(currentPath);
        else for (Map.Entry<String, PermissionNode> entry : node.children.entrySet()) {
            String childName = entry.getKey();
            PermissionNode childNode = entry.getValue();
            String newPath = currentPath + "." + childName;
            collectLeafPermissions(childNode, newPath, permissions);
        }
    }
}