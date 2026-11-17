package com.usagewidget;

/** Per-slot entry points. Each is declared in the manifest with its own process (":c2", ":x2", ...). */
public final class Slots {
 private Slots(){}
 public static class ClaudeLogin2 extends LoginActivity {}
 public static class ClaudeLogin3 extends LoginActivity {}
 public static class CodexLogin2 extends LoginActivity {}
 public static class CodexLogin3 extends LoginActivity {}
 public static class CodexJob2 extends RefreshJob {}
 public static class CodexJob3 extends RefreshJob {}
}
